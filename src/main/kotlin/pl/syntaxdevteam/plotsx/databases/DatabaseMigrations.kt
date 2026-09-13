package pl.syntaxdevteam.plotsx.databases

import java.sql.Connection
import java.util.Locale

/** Maintenance-only schema migration. No world/player operations and no implicit runtime activation. */
internal object DatabaseMigrations {
    const val VERSION = 1
    private const val NAME = "chunk_geometry"

    private fun tableExists(conn: Connection, table: String): Boolean =
        conn.metaData.getTables(conn.catalog, conn.schema, "%", null).use { rows ->
            var found = false
            while (rows.next()) if (rows.getString("TABLE_NAME").equals(table, true)) found = true
            found
        }

    private fun columns(conn: Connection): Set<String> {
        if (!tableExists(conn, "plots")) return emptySet()
        return conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM plots WHERE 1 = 0").use { rows ->
                (1..rows.metaData.columnCount).map { rows.metaData.getColumnName(it).lowercase(Locale.ROOT) }.toSet()
            }
        }
    }

    fun isChunkSchema(conn: Connection): Boolean = columns(conn).containsAll(listOf("geometry_type", "geometry_revision"))

    fun migrate(conn: Connection, dialect: String) {
        require(conn.autoCommit) { "Migration requires a dedicated auto-commit connection" }
        val type = SqlBackup.dialect(dialect)
        if (tableExists(conn, "schema_migrations")) {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT version, name FROM schema_migrations").use { rows ->
                    while (rows.next()) require((rows.getInt(1) == VERSION && rows.getString(2) == NAME) ||
                        (rows.getInt(1) == 2 && rows.getString(2) == "operation_journal")) {
                        "Unsupported database migration version"
                    }
                }
            }
        }
        if (type == "sqlite") migrateSQLite(conn) else migrateServerDatabase(conn, type)
    }

    private fun migrateSQLite(conn: Connection) {
        val oldColumns = columns(conn)
        val radiusRequired = if (oldColumns.isEmpty()) false else conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info(plots)").use { rows ->
                var required = false
                while (rows.next()) if (rows.getString("name") == "radius") required = rows.getInt("notnull") != 0
                required
            }
        }
        val foreignKeys = conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA foreign_keys").use { it.next(); it.getInt(1) }
        }
        conn.createStatement().use { it.execute("PRAGMA foreign_keys=OFF") }
        conn.autoCommit = false
        try {
            if (oldColumns.isNotEmpty() && (!isChunkSchema(conn) || radiusRequired)) {
                require(!tableExists(conn, "plots_chunk_migration")) { "Unexpected migration staging table" }
                val sequence = conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT seq FROM sqlite_sequence WHERE name='plots'").use { if (it.next()) it.getLong(1) else 0L }
                }
                val create = DatabaseSchema.chunkStatements("sqlite").first()
                    .replace("IF NOT EXISTS plots (", "plots_chunk_migration (")
                conn.createStatement().use { stmt ->
                    stmt.execute(create)
                    val legacyColumns = "plot_id, owner_uuid, x, z, y, radius, world, name, creation_time"
                    val geometryType = if ("geometry_type" in oldColumns) "geometry_type" else "'classic'"
                    val revision = if ("geometry_revision" in oldColumns) "geometry_revision" else "0"
                    stmt.execute("INSERT INTO plots_chunk_migration ($legacyColumns, geometry_type, geometry_revision) " +
                        "SELECT $legacyColumns, $geometryType, $revision FROM plots")
                    stmt.execute("DROP TABLE plots")
                    stmt.execute("ALTER TABLE plots_chunk_migration RENAME TO plots")
                    stmt.execute("UPDATE sqlite_sequence SET seq = MAX(seq, $sequence) WHERE name='plots'")
                }
            }
            finish(conn, "sqlite")
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA foreign_key_check").use { require(!it.next()) { "Foreign key violation after migration" } }
            }
            conn.commit()
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        } finally {
            conn.autoCommit = true
            conn.createStatement().use { it.execute("PRAGMA foreign_keys=$foreignKeys") }
        }
    }

    /** H2/MySQL DDL may commit implicitly: every step is repeatable; the marker is written last. */
    private fun migrateServerDatabase(conn: Connection, dialect: String) {
        val existing = columns(conn)
        if (existing.isNotEmpty()) {
            conn.createStatement().use { stmt ->
                if ("geometry_type" !in existing) stmt.execute("ALTER TABLE plots ADD COLUMN geometry_type VARCHAR(16) NOT NULL DEFAULT 'classic'")
                if ("geometry_revision" !in existing) stmt.execute("ALTER TABLE plots ADD COLUMN geometry_revision BIGINT NOT NULL DEFAULT 0")
                stmt.execute(if (dialect == "mysql") "ALTER TABLE plots MODIFY COLUMN radius INT NULL"
                    else "ALTER TABLE plots ALTER COLUMN radius DROP NOT NULL")
            }
            removeLocationConstraint(conn, dialect)
        }
        finish(conn, dialect)
    }

    private fun removeLocationConstraint(conn: Connection, dialect: String) {
        val constraints = linkedMapOf<String, MutableSet<String>>()
        if (dialect == "mysql") {
            conn.metaData.getIndexInfo(conn.catalog, null, "plots", true, false).use { rows ->
                while (rows.next()) {
                    val name = rows.getString("INDEX_NAME") ?: continue
                    val column = rows.getString("COLUMN_NAME") ?: continue
                    constraints.getOrPut(name) { mutableSetOf() }.add(column.lowercase(Locale.ROOT))
                }
            }
        } else {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("""
                    SELECT tc.constraint_name, kc.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kc
                      ON tc.constraint_catalog = kc.constraint_catalog
                     AND tc.constraint_schema = kc.constraint_schema
                     AND tc.constraint_name = kc.constraint_name
                    WHERE LOWER(tc.table_name) = 'plots' AND tc.constraint_type = 'UNIQUE'
                      AND tc.constraint_schema = CURRENT_SCHEMA()
                """.trimIndent()).use { rows ->
                    while (rows.next()) constraints.getOrPut(rows.getString(1)) { mutableSetOf() }
                        .add(rows.getString(2).lowercase(Locale.ROOT))
                }
            }
        }
        val quote = conn.metaData.identifierQuoteString.trim()
        for ((name, fields) in constraints) if (fields == setOf("x", "z", "world")) {
            val identifier = quote + name.replace(quote, quote + quote) + quote
            conn.createStatement().use { it.execute("ALTER TABLE plots DROP ${if (dialect == "mysql") "INDEX" else "CONSTRAINT"} $identifier") }
        }
    }

    private fun finish(conn: Connection, dialect: String) {
        conn.createStatement().use { stmt -> DatabaseSchema.chunkStatements(dialect).forEach { stmt.execute(it) } }
        val table = if (dialect == "h2") "PLOT_CHUNKS" else "plot_chunks"
        val hasIndex = conn.metaData.getIndexInfo(conn.catalog, conn.schema, table, false, false).use { rows ->
            var found = false
            while (rows.next()) if (rows.getString("INDEX_NAME").equals("idx_plot_chunks_plot_id", true)) found = true
            found
        }
        if (!hasIndex) conn.createStatement().use { it.execute("CREATE INDEX idx_plot_chunks_plot_id ON plot_chunks(plot_id)") }
        // Loading validates each geometry before marking the migration complete.
        PlotGeometryRepository.readAll(conn)
        conn.prepareStatement("SELECT name FROM schema_migrations WHERE version = ?").use { stmt ->
            stmt.setInt(1, VERSION)
            stmt.executeQuery().use { rows -> if (rows.next()) return }
        }
        conn.prepareStatement("INSERT INTO schema_migrations (version, name) VALUES (?, ?)").use {
            it.setInt(1, VERSION); it.setString(2, NAME); it.executeUpdate()
        }
    }
}
