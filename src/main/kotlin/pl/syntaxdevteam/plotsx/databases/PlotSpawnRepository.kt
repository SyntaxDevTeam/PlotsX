package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.PlotsX
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/** Player-selected teleport point stored independently from plot geometry. */
data class PlotTeleportSpawn(val x: Int, val y: Int, val z: Int)

internal object PlotSpawnRepository {
    sealed interface SaveResult {
        data object Success : SaveResult
        data object PlotNotFound : SaveResult
        data object NotOwner : SaveResult
        data object DatabaseError : SaveResult
    }

    fun schema(): String = """
        CREATE TABLE IF NOT EXISTS plot_spawns (
            plot_id INTEGER PRIMARY KEY,
            x INTEGER NOT NULL,
            y INTEGER NOT NULL,
            z INTEGER NOT NULL,
            FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
        )
    """.trimIndent()

    fun migrate(conn: Connection) {
        conn.createStatement().use { it.execute(schema()) }
    }

    fun exists(conn: Connection): Boolean =
        conn.metaData.getTables(conn.catalog, conn.schema, "%", null).use { rows ->
            var found = false
            while (rows.next()) {
                if (rows.getString("TABLE_NAME").equals("plot_spawns", ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }

    fun readAll(conn: Connection, plotId: Int? = null): Map<Int, PlotTeleportSpawn> {
        if (!exists(conn)) return emptyMap()
        val sql = "SELECT plot_id, x, y, z FROM plot_spawns" +
            if (plotId == null) " ORDER BY plot_id" else " WHERE plot_id = ?"
        return conn.prepareStatement(sql).use { stmt ->
            if (plotId != null) stmt.setInt(1, plotId)
            stmt.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getInt("plot_id"),
                            PlotTeleportSpawn(rows.getInt("x"), rows.getInt("y"), rows.getInt("z"))
                        )
                    }
                }
            }
        }
    }

    /**
     * Rare metadata write. The caller must invoke this off the server thread.
     * A short dedicated JDBC connection avoids exposing DatabaseHandler internals while ordinary
     * protection and teleport reads remain entirely cache-backed.
     */
    fun save(
        plugin: PlotsX,
        plotId: Int,
        expectedOwner: UUID,
        actor: UUID,
        spawn: PlotTeleportSpawn
    ): SaveResult = try {
        openConnection(plugin).use { conn ->
            migrate(conn)
            save(conn, plotId, expectedOwner, actor, spawn)
        }
    } catch (failure: Exception) {
        plugin.logger.err("Teleport spawn update failed for plot $plotId: ${failure.message}")
        SaveResult.DatabaseError
    }

    private fun openConnection(plugin: PlotsX): Connection {
        val type = plugin.config.getString("database.type")?.lowercase() ?: "sqlite"
        val dbName = plugin.config.getString("database.sql.dbname") ?: plugin.name
        val user = plugin.config.getString("database.sql.username") ?: "ROOT"
        val password = plugin.config.getString("database.sql.password") ?: "U5eV3ryStr0ngP4ssw0rd"
        val connection = when (type) {
            "mysql", "mariadb" -> DriverManager.getConnection(
                "jdbc:mariadb://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName",
                user,
                password
            )
            "postgresql" -> DriverManager.getConnection(
                "jdbc:postgresql://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName",
                user,
                password
            )
            "sqlite" -> DriverManager.getConnection("jdbc:sqlite:${plugin.dataFolder}/$dbName.db")
            "h2" -> DriverManager.getConnection("jdbc:h2:./${plugin.dataFolder}/$dbName", user, password)
            else -> throw IllegalArgumentException("Unsupported database type: $type")
        }
        if (type == "sqlite") {
            connection.createStatement().use {
                it.execute("PRAGMA journal_mode=WAL;")
                it.execute("PRAGMA foreign_keys=ON;")
            }
        }
        return connection
    }

    private fun save(
        conn: Connection,
        plotId: Int,
        expectedOwner: UUID,
        actor: UUID,
        spawn: PlotTeleportSpawn
    ): SaveResult {
        require(conn.autoCommit) { "Teleport-spawn update requires a dedicated auto-commit connection" }
        conn.autoCommit = false
        try {
            conn.prepareStatement("DELETE FROM plot_spawns WHERE plot_id = ?").use {
                it.setInt(1, plotId)
                it.executeUpdate()
            }
            val inserted = conn.prepareStatement(
                """
                INSERT INTO plot_spawns (plot_id, x, y, z)
                SELECT plot_id, ?, ?, ? FROM plots
                WHERE plot_id = ? AND owner_uuid = ?
                """.trimIndent()
            ).use { stmt ->
                stmt.setInt(1, spawn.x)
                stmt.setInt(2, spawn.y)
                stmt.setInt(3, spawn.z)
                stmt.setInt(4, plotId)
                stmt.setString(5, expectedOwner.toString())
                stmt.executeUpdate()
            }
            if (inserted != 1) {
                val plotExists = conn.prepareStatement("SELECT 1 FROM plots WHERE plot_id = ?").use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.executeQuery().use { it.next() }
                }
                conn.rollback()
                return if (plotExists) SaveResult.NotOwner else SaveResult.PlotNotFound
            }
            conn.prepareStatement(
                "INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.setString(2, "SET_TELEPORT_SPAWN:${spawn.x},${spawn.y},${spawn.z}")
                stmt.setString(3, actor.toString())
                stmt.setLong(4, System.currentTimeMillis())
                stmt.executeUpdate()
            }
            conn.commit()
            return SaveResult.Success
        } catch (failure: Exception) {
            try {
                conn.rollback()
            } catch (rollback: Exception) {
                failure.addSuppressed(rollback)
            }
            throw failure
        } finally {
            conn.autoCommit = true
        }
    }
}
