package pl.syntaxdevteam.plotsx.databases

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Location
import pl.syntaxdevteam.plotsx.PlotsX
import java.io.File
import java.io.IOException
import java.sql.*

class DatabaseHandler(private val plugin: PlotsX) {
    private var dataSource: HikariDataSource? = null
    private var logger = plugin.logger
    private val dbType = plugin.config.getString("database.type")?.lowercase() ?: "sqlite"

    init {
        setupDataSource()
    }

    /**
     * Configures the HikariCP data source for database connections.
     *
     * This method sets up the data source based on the database type defined in the plugin configuration.
     * It supports the following database types:
     * - MySQL/MariaDB
     * - PostgreSQL
     * - SQLite
     * - H2
     *
     * For SQLite databases, specific pool settings are applied to minimize resource usage.
     * For other database types, the pool is configured with more robust settings suitable for production.
     *
     * @throws IllegalArgumentException If the database type is unsupported.
     * @throws Exception If an error occurs during data source initialization.
     */

    private fun setupDataSource() {
        val hikariConfig = HikariConfig()
        val dbName = plugin.config.getString("database.sql.dbname") ?: plugin.name
        val user = plugin.config.getString("database.sql.username") ?: "ROOT"
        val password = plugin.config.getString("database.sql.password") ?: "U5eV3ryStr0ngP4ssw0rd"
        when (dbType) {
            "mysql", "mariadb" -> {
                hikariConfig.jdbcUrl =
                    "jdbc:mysql://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
            }

            "postgresql" -> {
                hikariConfig.jdbcUrl =
                    "jdbc:postgresql://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "org.postgresql.Driver"
            }

            "sqlite" -> {
                hikariConfig.jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/$dbName.db"
                hikariConfig.driverClassName = "org.sqlite.JDBC"
            }

            "h2" -> {
                hikariConfig.jdbcUrl = "jdbc:h2:./${plugin.dataFolder}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "org.h2.Driver"
            }

            else -> throw IllegalArgumentException("Unsupported database type: $dbType")
        }
        if (dbType == "sqlite") {
            hikariConfig.maximumPoolSize = 2
            hikariConfig.minimumIdle = 1
            hikariConfig.connectionTimeout = 30000
            hikariConfig.idleTimeout = 10000
            hikariConfig.maxLifetime = 60000
            hikariConfig.keepaliveTime = 30000
        } else {
            hikariConfig.maximumPoolSize = 10
            hikariConfig.minimumIdle = 2
            hikariConfig.connectionTimeout = 30000
            hikariConfig.idleTimeout = 600000
            hikariConfig.maxLifetime = 1800000
            hikariConfig.keepaliveTime = 900000
            hikariConfig.leakDetectionThreshold = 2000
        }

        logger.debug("Setting up data source for database type: $dbType")
        try {
            dataSource = HikariDataSource(hikariConfig)
            logger.debug("HikariCP data source initialized successfully for $dbType.")
        } catch (e: Exception) {
            logger.err("Failed to initialize HikariCP data source: ${e.message}")
            throw e
        }
    }

    /**
     * Ensures that the database connection is open.
     *
     * If the data source is not initialized, this method will invoke `setupDataSource`
     * to configure and start the connection pool.
     */
    fun openConnection() {
        if (dataSource == null) {
            setupDataSource()
        }
    }

    /**
     * Closes the HikariCP connection pool.
     *
     * This method gracefully shuts down the data source, releasing all resources associated with
     * the connection pool. It also logs the pool's statistics, such as total, active, and idle connections.
     *
     * Any errors encountered during shutdown are logged but do not interrupt the process.
     */
    fun closeConnection() {
        try {
            dataSource?.close()
            logger.info("HikariCP pool shut down. Total=${dataSource?.hikariPoolMXBean?.totalConnections}, Active=${dataSource?.hikariPoolMXBean?.activeConnections}, Idle=${dataSource?.hikariPoolMXBean?.idleConnections}")
        } catch (e: SQLException) {
            logger.err("Error while closing HikariCP pool: ${e.message}")
        }
    }

    /**
     * Obtains a connection from the HikariCP data source.
     *
     * This method retrieves a connection from the pool and, if the database type is SQLite,
     * enables Write-Ahead Logging (WAL) mode to improve performance.
     *
     * Connections must be properly closed after use to avoid resource leaks.
     *
     * @return A valid `Connection` object, or `null` if the connection could not be established.
     */
    private fun getConnection(): Connection? {
        return try {
            val connection = dataSource?.connection
            if (connection != null && dbType == "sqlite") {
                enableSQLiteWAL(connection)
            }
            logger.debug("Connection obtained: Active=${dataSource?.hikariPoolMXBean?.activeConnections}, Idle=${dataSource?.hikariPoolMXBean?.idleConnections}")
            connection
        } catch (e: SQLException) {
            logger.err("Failed to get connection. ${e.message}")
            null
        }
    }

    /**
     * Enables Write-Ahead Logging (WAL) mode for SQLite.
     *
     * WAL mode improves SQLite's performance by allowing concurrent reads and writes.
     * This method executes the `PRAGMA journal_mode=WAL;` statement on the provided connection.
     *
     * @param connection The active SQLite connection.
     */
    private fun enableSQLiteWAL(connection: Connection) {
        logger.debug("SQLite connection detected! I'm enabling WAL mode")
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                logger.debug("SQLite WAL mode enabled.")
            }
        } catch (e: SQLException) {
            logger.err("Failed to enable SQLite WAL mode. ${e.message}")
        }
    }

    fun createTables() {
        getConnection()?.use { conn ->
            logger.debug("Database connection established from createTables")
            conn.createStatement().use { statement ->
                try {
                    val createPlotsTable = when (dbType) {
                        "sqlite" -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                owner_uuid TEXT NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world TEXT NOT NULL,
                                name TEXT NOT NULL,
                                creation_time TEXT NOT NULL,
                                expiration_time TEXT,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                id SERIAL PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                expiration_time BIGINT,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                expiration_time BIGINT,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INT NOT NULL,
                                z INT NOT NULL,
                                radius INTEGER NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                expiration_time BIGINT,
                                UNIQUE(x, z, world)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
                        """.trimIndent()
                    }
                    statement.executeUpdate(createPlotsTable)
                    logger.debug("Table 'plots' created.")

                    val createPlotMembersTable = when (dbType) {
                        "sqlite" -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INTEGER NOT NULL,
                                member_uuid TEXT NOT NULL,
                                role TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INTEGER NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
                        """.trimIndent()
                    }
                    statement.executeUpdate(createPlotMembersTable)
                    logger.debug("Table 'plot_members' created.")

                    val createPlotFlagsTable = when (dbType) {
                        "sqlite" -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INTEGER NOT NULL,
                                flag_key TEXT NOT NULL,
                                flag_value TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_key)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INTEGER NOT NULL,
                                flag_key VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_key)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INT NOT NULL,
                                flag_key VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_key)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INT NOT NULL,
                                flag_key VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_key)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
                        """.trimIndent()
                    }
                    statement.executeUpdate(createPlotFlagsTable)
                    logger.debug("Table 'plot_flags' created.")

                    val createPlotLogsTable = when (dbType) {
                        "sqlite" -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                plot_id INTEGER NOT NULL,
                                action TEXT NOT NULL,
                                actor_uuid TEXT NOT NULL,
                                timestamp TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id SERIAL PRIMARY KEY,
                                plot_id INTEGER NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp BIGINT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                plot_id INT NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                plot_id INT NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp BIGINT (255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
                        """.trimIndent()
                    }
                    statement.executeUpdate(createPlotLogsTable)
                    logger.debug("Table 'plot_logs' created.")
                } catch (ex: SQLException) {
                    logger.err("Error creating tables: ${ex.message}")
                }
            }
        } ?: logger.err("No database connection.")
        logger.debug("Table creation operations completed.")
    }

    fun saveNewPlot(ownerUuid: String, x: Int, z: Int, radius: Int, world: String, name: String, creationTime: Long, expirationTime: Long?) {
        getConnection()?.use { conn ->
            conn.useTransaction {
                val plotId = insertPlot(conn, ownerUuid, x, z, radius, world, name, creationTime, expirationTime)
                    ?: throw SQLException("Failed to retrieve plot ID.")

                // Wstawienie właściciela do plot_members
                conn.prepareStatement(
                    "INSERT INTO plot_members (plot_id, member_uuid, role) VALUES (?, ?, ?);"
                ).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, ownerUuid)
                    stmt.setString(3, "owner")
                    stmt.executeUpdate()
                }

                // Wstawienie domyślnej flagi do plot_flags
                conn.prepareStatement(
                    "INSERT INTO plot_flags (plot_id, flag_key, flag_value) VALUES (?, ?, ?);"
                ).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, "default_flag")
                    stmt.setString(3, "true")
                    stmt.executeUpdate()
                }

                // Zapisanie loga utworzenia działki
                conn.prepareStatement(
                    "INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?);"
                ).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, "plot_created")
                    stmt.setString(3, ownerUuid)
                    stmt.setLong(4, creationTime)
                    stmt.executeUpdate()
                }

                logger.success("New plot saved successfully with ID: $plotId")
            }
        } ?: logger.err("No database connection.")
    }

    private fun insertPlot(conn: Connection, ownerUuid: String, x: Int, z: Int, radius: Int, world: String, name: String, creationTime: Long, expirationTime: Long?): Int? {
        conn.prepareStatement(
            """
        INSERT INTO plots (owner_uuid, x, z, radius, world, name, creation_time, expiration_time)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, Statement.RETURN_GENERATED_KEYS
        ).use { stmt ->
            stmt.setString(1, ownerUuid)
            stmt.setInt(2, x)
            stmt.setInt(3, z)
            stmt.setInt(4, radius)
            stmt.setString(5, world)
            stmt.setString(6, name)
            stmt.setLong(7, creationTime)
            stmt.setObject(8, expirationTime)
            stmt.executeUpdate()

            stmt.generatedKeys.use { rs ->
                if (rs.next()) return rs.getInt(1)
            }

            if (conn.metaData.databaseProductName.contains("SQLite", true)) {
                conn.prepareStatement("SELECT last_insert_rowid();").use { stmt2 ->
                    stmt2.executeQuery().use { rs ->
                        if (rs.next()) return rs.getInt(1)
                    }
                }
            }
        }
        return null
    }

    private fun Connection.useTransaction(block: () -> Unit) {
        autoCommit = false
        try {
            block()
            commit()
        } catch (ex: SQLException) {
            rollback()
            logger.err("Transaction failed: ${ex.message}")
        } finally {
            autoCommit = true
        }
    }

    fun getPlotById(plotId: Int): Plot? {
        getConnection()?.use { conn ->
            try {
                val plot: Plot?
                conn.prepareStatement(
                    """
                    SELECT * FROM plots WHERE id = ?
                    """
                ).use { stmt ->
                    stmt.setInt(1, plotId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val ownerUuid = rs.getString("owner_uuid")
                        val x = rs.getInt("x")
                        val z = rs.getInt("z")
                        val radius = rs.getInt("radius")
                        val world = rs.getString("world")
                        val name = rs.getString("name")
                        val creationTime = rs.getLong("creation_time")
                        val expirationTime = rs.getLong("expiration_time").takeIf { !rs.wasNull() }

                        val members = getPlotMembers(conn, plotId)
                        val flags = getPlotFlags(conn, plotId)
                        val logs = getPlotLogs(conn, plotId)

                        plot = Plot(plotId, ownerUuid, x, z, radius, world, name, creationTime, expirationTime, members, flags, logs)
                    } else {
                        plot = null
                    }
                }
                return plot
            } catch (ex: SQLException) {
                logger.err("Failed to retrieve plot: ${ex.message}")
                return null
            }
        } ?: run {
            logger.err("No database connection.")
            return null
        }
    }

    fun getPlotIdByLocation(location: Location): Int? {
        getConnection()?.use { conn ->
            try {
                conn.prepareStatement(
                    """
                    SELECT id FROM plots WHERE x = ? AND z = ? AND world = ?
                    """
                ).use { stmt ->
                    stmt.setInt(1, location.blockX)
                    stmt.setInt(2, location.blockZ)
                    stmt.setString(3, location.world.name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        return rs.getInt("id")
                    }
                }
            } catch (ex: SQLException) {
                logger.err("Błąd podczas pobierania identyfikatora działki: ${ex.message}")
            }
        } ?: logger.err("Brak połączenia z bazą danych.")
        return null
    }

    private fun getPlotMembers(conn: Connection, plotId: Int): List<PlotMember> {
        val members = mutableListOf<PlotMember>()
        val sql = """
        SELECT member_uuid, role FROM plot_members WHERE plot_id = ?
    """
        try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        members.add(
                            PlotMember(
                                plotId,
                                rs.getString("member_uuid"),
                                rs.getString("role")
                            )
                        )
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas pobierania członków działki: ${ex.message}")
        }
        return members
    }

    private fun getPlotFlags(conn: Connection, plotId: Int): List<PlotFlag> {
        val flags = mutableListOf<PlotFlag>()
        val sql = """
        SELECT flag_key, flag_value FROM plot_flags WHERE plot_id = ?
    """
        try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        flags.add(
                            PlotFlag(
                                plotId,
                                rs.getString("flag_key"),
                                rs.getString("flag_value")
                            )
                        )
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas pobierania flag działki: ${ex.message}")
        }
        return flags
    }

    private fun getPlotLogs(conn: Connection, plotId: Int): List<PlotLog> {
        val logs = mutableListOf<PlotLog>()
        val sql = """
        SELECT id, action, actor_uuid, timestamp FROM plot_logs WHERE plot_id = ?
    """
        try {
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        logs.add(
                            PlotLog(
                                rs.getInt("id"),
                                plotId,
                                rs.getString("action"),
                                rs.getString("actor_uuid"),
                                rs.getLong("timestamp")
                            )
                        )
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas pobierania logów działki: ${ex.message}")
        }
        return logs
    }


    fun updatePlot(plotId: Int, ownerUuid: String?, x: Int?, z: Int?, world: String?, name: String?, creationTime: Long?, expirationTime: Long?) {
        getConnection()?.use { conn ->
            conn.useTransaction {
                val updates = mutableListOf<String>()
                val params = mutableListOf<Any?>()

                ownerUuid?.let { updates.add("owner_uuid = ?"); params.add(it) }
                x?.let { updates.add("x = ?"); params.add(it) }
                z?.let { updates.add("z = ?"); params.add(it) }
                world?.let { updates.add("world = ?"); params.add(it) }
                name?.let { updates.add("name = ?"); params.add(it) }
                creationTime?.let { updates.add("creation_time = ?"); params.add(it) }
                expirationTime?.let { updates.add("expiration_time = ?"); params.add(it) }

                if (updates.isEmpty()) {
                    logger.warning("No values provided to update for plot ID: $plotId")
                    return@useTransaction
                }

                val sql = "UPDATE plots SET ${updates.joinToString(", ")} WHERE id = ?"
                params.add(plotId)

                conn.prepareStatement(sql).use { stmt ->
                    params.forEachIndexed { index, param -> stmt.setObject(index + 1, param) }
                    stmt.executeUpdate()
                }

                logger.success("Plot ID: $plotId updated successfully.")
            }
        } ?: logger.err("No database connection.")
    }

    fun deletePlot(plotId: Int) {
        getConnection()?.use { conn ->
            conn.useTransaction {
                listOf("plot_members", "plot_flags", "plot_logs").forEach { table ->
                    conn.prepareStatement("DELETE FROM $table WHERE plot_id = ?").use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.executeUpdate()
                    }
                }
                conn.prepareStatement("DELETE FROM plots WHERE id = ?").use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.executeUpdate()
                }
                logger.success("Plot ID: $plotId and its related data deleted successfully.")
            }
        } ?: logger.err("No database connection.")
    }


    /**
     * Exports the database to a SQL dump file.
     *
     * This method retrieves the data from the database tables and writes it to a SQL dump file.
     * The dump file contains valid SQL statements to recreate the tables and insert the data.
     *
     * The dump file is saved in the `dump` directory inside the plugin's data folder.
     */
    fun exportDatabase() {
        val tables = listOf("punishments", "punishmenthistory")
        try {
            getConnection()?.use { conn ->
                val dumpDir = File(plugin.dataFolder, "dump")
                if (!dumpDir.exists()) {
                    dumpDir.mkdirs()
                }
                val writer = File(dumpDir, "backup.sql").bufferedWriter()
                for (table in tables) {
                    val resultSet = conn.createStatement().executeQuery("SELECT * FROM $table")
                    val metaData = resultSet.metaData
                    val columnCount = metaData.columnCount

                    if (!resultSet.isBeforeFirst) {
                        continue
                    }

                    writer.write("INSERT INTO $table VALUES\n")
                    var first = true
                    while (resultSet.next()) {
                        if (!first) {
                            writer.write(",\n")
                        }
                        first = false
                        writer.write("(")
                        for (i in 1..columnCount) {
                            val value = resultSet.getObject(i)
                            if (value == null) {
                                writer.write("NULL")
                            } else {
                                writer.write("'${value.toString().replace("'", "''")}'")
                            }
                            if (i < columnCount) writer.write(", ")
                        }
                        writer.write(")")
                    }
                    writer.write(";\n")
                }
                writer.close()
                plugin.logger.success("Database exported to ${dumpDir}/backup.sql")
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to export database. ${e.message}")
        } catch (e: IOException) {
            plugin.logger.err("Failed to write to file. ${e.message}")
        }
    }

    /**
     * Imports the database from a SQL dump file.
     *
     * This method reads the SQL dump file line by line and executes the SQL statements to recreate
     * the database tables and insert the data. The dump file must contain valid SQL statements
     * separated by semicolons.
     */
    fun importDatabase() {
        val filePath = File(plugin.dataFolder, "dump/backup.sql").absolutePath
        try {
            getConnection()?.use { conn ->
                val lines = File(filePath).readLines()
                val statement = conn.createStatement()
                val sql = StringBuilder()
                for (line in lines) {
                    sql.append(line)
                    if (line.trim().endsWith(";")) {
                        statement.execute(sql.toString())
                        sql.setLength(0)
                    }
                }
                plugin.logger.success("Database imported from $filePath")
            }
        } catch (e: SQLException) {
            plugin.logger.err("Failed to import database. ${e.message}")
        } catch (e: IOException) {
            plugin.logger.err("Failed to read from file. ${e.message}")
        }
    }
}