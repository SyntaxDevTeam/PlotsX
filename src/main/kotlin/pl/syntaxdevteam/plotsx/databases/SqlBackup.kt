package pl.syntaxdevteam.plotsx.databases

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.util.Locale

/** Versioned, one-statement-per-line SQL. Text is UTF-8 hex so delimiters are unambiguous. */
internal object SqlBackup {
    val dialects = listOf("mysql", "mariadb", "sqlite", "postgresql", "h2")
    private val tables = listOf("plots", "plot_segments", "plot_expansion_levels", "plot_members", "plot_flags", "plot_logs")

    fun dialect(value: String): String {
        val normalized = value.lowercase(Locale.ROOT)
        require(normalized in dialects) { "Supported dialects: ${dialects.joinToString()}" }
        return if (normalized == "mariadb") "mysql" else normalized
    }

    private fun literal(value: Any?, dialect: String): String = when (value) {
        null -> "NULL"
        is Number -> value.toString()
        is Boolean -> if (value) "TRUE" else "FALSE"
        else -> {
            val hex = value.toString().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
            when (dialect) {
                "mysql" -> "CONVERT(X'$hex' USING utf8mb4)"
                "postgresql" -> "convert_from(decode('$hex', 'hex'), 'UTF8')"
                "h2" -> "CAST(X'$hex' AS VARCHAR)"
                else -> "CAST(X'$hex' AS TEXT)"
            }
        }
    }

    fun export(conn: Connection, dialect: String, directory: File): File {
        require(conn.autoCommit) { "Export requires a dedicated auto-commit connection" }
        val isolation = conn.transactionIsolation
        val chunkSchema = DatabaseMigrations.isChunkSchema(conn)
        val journalSchema = OperationJournal.exists(conn)
        require(!journalSchema || chunkSchema) { "Journal requires the geometry schema" }
        val exportedTables = (if (chunkSchema) tables + "plot_chunks" else tables) + if (journalSchema) listOf("plot_operations") else emptyList()
        val schema = (if (chunkSchema) DatabaseSchema.chunkStatements(dialect) else DatabaseSchema.statements(dialect)) +
            if (journalSchema) listOf(OperationJournal.schema()) else emptyList()
        Files.createDirectories(directory.toPath())
        val destination = File(directory, "backup.sql")
        val temporary = Files.createTempFile(directory.toPath(), "backup-", ".tmp")
        try {
            if (conn.metaData.databaseProductName != "SQLite") {
                conn.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            }
            conn.autoCommit = false
            if (journalSchema) OperationJournal.readAll(conn)
            Files.newBufferedWriter(temporary, Charsets.UTF_8).use { writer ->
                fun line(sql: String) { writer.write(sql); writer.newLine() }
                line("-- PlotsX SQL backup v${if (journalSchema) 3 else if (chunkSchema) 2 else 1} dialect=$dialect")
                schema.forEach { line(it.trim().removeSuffix(";").replace(Regex("\\s+"), " ") + ";") }
                line("BEGIN;")
                exportedTables.asReversed().forEach { line("DELETE FROM $it;") }
                for (table in exportedTables) {
                    conn.createStatement().use { statement ->
                        statement.executeQuery("SELECT * FROM $table").use { rows ->
                            val columns = (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it).lowercase(Locale.ROOT) }
                            while (rows.next()) {
                                val values = columns.mapIndexed { index, column ->
                                    val value = rows.getObject(index + 1)
                                    // SQLite stores epoch times as TEXT; other schemas use BIGINT.
                                    if (value != null && (column == "creation_time" || column == "timestamp")) {
                                        value.toString().toLong().toString()
                                    } else literal(value, dialect)
                                }
                                line("INSERT INTO $table (${columns.joinToString()}) VALUES (${values.joinToString()});")
                            }
                        }
                    }
                }
                for ((table, column) in listOf("plots" to "plot_id", "plot_logs" to "id")) {
                    if (dialect == "postgresql") {
                        line("SELECT setval(pg_get_serial_sequence('$table', '$column'), COALESCE((SELECT MAX($column) FROM $table), 0) + 1, false);")
                    } else if (dialect == "h2") {
                        conn.createStatement().use { statement ->
                            statement.executeQuery("SELECT COALESCE(MAX($column), 0) + 1 FROM $table").use { rows ->
                                rows.next()
                                line("ALTER TABLE $table ALTER COLUMN $column RESTART WITH ${rows.getLong(1)};")
                            }
                        }
                    }
                }
                line("COMMIT;")
            }
            conn.commit()
            Files.move(temporary, destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return destination
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        } finally {
            conn.autoCommit = true
            if (conn.transactionIsolation != isolation) conn.transactionIsolation = isolation
            Files.deleteIfExists(temporary)
        }
    }

    fun restore(conn: Connection, dialect: String, file: File, allowChunkPlots: Boolean = false) {
        // Validate the entire file before modifying the database. Only our versioned format is supported.
        val lines = file.readLines(Charsets.UTF_8)
        val journalBackup = lines.firstOrNull() == "-- PlotsX SQL backup v3 dialect=$dialect"
        val chunkBackup = journalBackup || lines.firstOrNull() == "-- PlotsX SQL backup v2 dialect=$dialect"
        require(chunkBackup || lines.firstOrNull() == "-- PlotsX SQL backup v1 dialect=$dialect") {
            "Not a PlotsX backup for $dialect. Export using the target database dialect."
        }
        val schema = ((if (chunkBackup) DatabaseSchema.chunkStatements(dialect) else DatabaseSchema.statements(dialect)) +
            if (journalBackup) listOf(OperationJournal.schema()) else emptyList())
            .map { it.trim().removeSuffix(";").replace(Regex("\\s+"), " ") + ";" }
        val legacySchema = schema.filterNot { it.startsWith("CREATE TABLE IF NOT EXISTS plot_segments ") }
        val sourceSchema = if (chunkBackup || lines.drop(1).take(schema.size) == schema) schema else legacySchema
        val sourceTables = (if (chunkBackup) tables + "plot_chunks" else if (sourceSchema == schema) tables else tables.filterNot { it == "plot_segments" }) +
            if (journalBackup) listOf("plot_operations") else emptyList()
        require(lines.drop(1).take(sourceSchema.size) == sourceSchema && lines.lastOrNull() == "COMMIT;") { "Incomplete backup or unsupported schema." }
        val body = lines.drop(1 + sourceSchema.size).dropLast(1)
        require(body.firstOrNull() == "BEGIN;") { "Missing transaction." }
        require(body.drop(1).take(sourceTables.size) == sourceTables.asReversed().map { "DELETE FROM $it;" }) { "Incomplete backup." }
        require(conn.autoCommit) { "Restore requires a dedicated auto-commit connection" }
        require(!OperationJournal.exists(conn) || OperationJournal.pending(conn).isEmpty()) {
            "Restore would overwrite unresolved payments; reconcile them before restoring a backup"
        }
        if (chunkBackup) DatabaseMigrations.migrate(conn, dialect)
        if (journalBackup) OperationJournal.migrate(conn)
        conn.createStatement().use { statement ->
            schema.forEach { statement.execute(it) }
        }
        val chunkTarget = DatabaseMigrations.isChunkSchema(conn)
        conn.autoCommit = false
        try {
            conn.createStatement().use { statement ->
                if (OperationJournal.exists(conn)) statement.execute("DELETE FROM plot_operations")
                if (chunkTarget) statement.execute("DELETE FROM plot_chunks")
                statement.execute("DELETE FROM plot_segments")
                // H2 ALTER TABLE commits implicitly: reset identities only after all data has loaded.
                body.drop(1).filterNot { it.startsWith("ALTER TABLE ") }.forEach { statement.execute(it) }
            }
            if (chunkTarget) {
                val geometries = PlotGeometryRepository.readAll(conn)
                require(allowChunkPlots || geometries.none { it.geometry is pl.syntaxdevteam.plotsx.geometry.ChunkGeometry }) {
                    "Chunk plots cannot be imported before chunk protection is available"
                }
                PlotGeometryRepository.validateNoOverlaps(geometries)
            }
            if (OperationJournal.exists(conn)) {
                OperationJournal.readAll(conn)
                OperationJournal.quarantineRestored(conn, System.currentTimeMillis())
            }
            conn.commit()
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        } finally {
            conn.autoCommit = true
        }
        conn.createStatement().use { statement ->
            body.filter { it.startsWith("ALTER TABLE ") }.forEach { statement.execute(it) }
        }
    }
}
