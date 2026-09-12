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
        Files.createDirectories(directory.toPath())
        val destination = File(directory, "backup.sql")
        val temporary = Files.createTempFile(directory.toPath(), "backup-", ".tmp")
        try {
            if (conn.metaData.databaseProductName != "SQLite") {
                conn.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            }
            conn.autoCommit = false
            Files.newBufferedWriter(temporary, Charsets.UTF_8).use { writer ->
                fun line(sql: String) { writer.write(sql); writer.newLine() }
                line("-- PlotsX SQL backup v1 dialect=$dialect")
                DatabaseSchema.statements(dialect).forEach { line(it.trim().removeSuffix(";").replace(Regex("\\s+"), " ") + ";") }
                line("BEGIN;")
                tables.asReversed().forEach { line("DELETE FROM $it;") }
                for (table in tables) {
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
            Files.deleteIfExists(temporary)
        }
    }

    fun restore(conn: Connection, dialect: String, file: File) {
        // Validate the entire file before modifying the database. Only our versioned format is supported.
        val lines = file.readLines(Charsets.UTF_8)
        require(lines.firstOrNull() == "-- PlotsX SQL backup v1 dialect=$dialect") {
            "Not a PlotsX backup for $dialect. Export using the target database dialect."
        }
        val schema = DatabaseSchema.statements(dialect).map { it.trim().removeSuffix(";").replace(Regex("\\s+"), " ") + ";" }
        val legacySchema = schema.filterNot { it.startsWith("CREATE TABLE IF NOT EXISTS plot_segments ") }
        val sourceSchema = if (lines.drop(1).take(schema.size) == schema) schema else legacySchema
        val sourceTables = if (sourceSchema == schema) tables else tables.filterNot { it == "plot_segments" }
        require(lines.drop(1).take(sourceSchema.size) == sourceSchema && lines.lastOrNull() == "COMMIT;") { "Incomplete backup or unsupported schema." }
        val body = lines.drop(1 + sourceSchema.size).dropLast(1)
        require(body.firstOrNull() == "BEGIN;") { "Missing transaction." }
        require(body.drop(1).take(sourceTables.size) == sourceTables.asReversed().map { "DELETE FROM $it;" }) { "Incomplete backup." }
        conn.createStatement().use { statement ->
            schema.forEach { statement.execute(it) }
        }
        conn.autoCommit = false
        try {
            conn.createStatement().use { statement ->
                statement.execute("DELETE FROM plot_segments")
                // H2 ALTER TABLE commits implicitly: reset identities only after all data has loaded.
                body.drop(1).filterNot { it.startsWith("ALTER TABLE ") }.forEach { statement.execute(it) }
            }
            conn.commit()
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        }
        conn.autoCommit = true
        conn.createStatement().use { statement ->
            body.filter { it.startsWith("ALTER TABLE ") }.forEach { statement.execute(it) }
        }
    }
}
