package pl.syntaxdevteam.plotsx.databases

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import pl.syntaxdevteam.plotsx.PlotsX
import java.io.File
import java.io.IOException
import java.sql.*
import java.util.*

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
                                plot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                                owner_uuid TEXT NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world TEXT NOT NULL,
                                name TEXT NOT NULL,
                                creation_time TEXT NOT NULL,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                plot_id SERIAL PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                plot_id INT AUTO_INCREMENT PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INTEGER NOT NULL,
                                z INTEGER NOT NULL,
                                radius INTEGER NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                UNIQUE(x, z, world)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plots (
                                plot_id INT AUTO_INCREMENT PRIMARY KEY,
                                owner_uuid VARCHAR(36) NOT NULL,
                                x INT NOT NULL,
                                z INT NOT NULL,
                                radius INT NOT NULL,
                                world VARCHAR(255) NOT NULL,
                                name VARCHAR(255) NOT NULL,
                                creation_time BIGINT NOT NULL,
                                
                                INDEX idx_owner_uuid (owner_uuid),
                                INDEX idx_world (world),
                                INDEX idx_coordinates (x, z),
                                INDEX idx_radius (radius),
                                UNIQUE KEY unique_location (x, z, world)
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
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INTEGER NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, member_uuid)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_members (
                                plot_id INT NOT NULL,
                                member_uuid VARCHAR(36) NOT NULL,
                                role VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
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
                                flag_name TEXT NOT NULL,
                                flag_value TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_name)
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INTEGER NOT NULL,
                                flag_name VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_name)
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INT NOT NULL,
                                flag_name VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_name)
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_flags (
                                plot_id INT NOT NULL,
                                flag_name VARCHAR(255) NOT NULL,
                                flag_value VARCHAR(255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE,
                                UNIQUE(plot_id, flag_name)
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
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        "postgresql" -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id SERIAL PRIMARY KEY,
                                plot_id INTEGER NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp BIGINT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        "h2" -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                plot_id INT NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp TEXT NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
                            );
                        """.trimIndent()

                        else -> """
                            CREATE TABLE IF NOT EXISTS plot_logs (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                plot_id INT NOT NULL,
                                action VARCHAR(255) NOT NULL,
                                actor_uuid VARCHAR(36) NOT NULL,
                                timestamp BIGINT (255) NOT NULL,
                                FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
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

    fun createNewPlot(
        ownerUuid: UUID,
        world: String,
        x: Int,
        z: Int,
        radius: Int,
        name: String
    ): Int? {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return null
        }

        val defaultFlags = mapOf(
            "build" to false,
            "pvp" to false,
            "chest" to false,
            "ender-chest" to true,
            "lever" to false,
            "button" to false,
            "door" to true,
            "spawn-monsters" to false,
            "spawn-animals" to true,
            "pasywne" to false,
            "flow" to false,
            "fire" to false,
            "minecart" to false,
            "allow-home" to true,
            "smart-door" to false,
            "use-potions" to false,
            "mob-loot" to false,
            "flow-damage" to false,
            "iceform-player" to false,
            "iceform-world" to true,
            "allow-fly" to true,
            "teleport" to false,
            "can-grow" to true,
            "allow-spawners" to false,
            "leaves-decay" to false,
            "allow-effects" to false,
            "redstone" to false,
            "block-transform" to false,
            "team" to false
        )

        try {
            connection.use { conn ->
                conn.autoCommit = false 

                // 1. Dodajemy działkę
                val plotId: Int
                val insertPlotSql = """
                INSERT INTO plots (owner_uuid, x, z, radius, world, name, creation_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

                conn.prepareStatement(insertPlotSql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.setInt(2, x)
                    stmt.setInt(3, z)
                    stmt.setInt(4, radius)
                    stmt.setString(5, world)
                    stmt.setString(6, name)
                    stmt.setLong(7, System.currentTimeMillis())
                    stmt.executeUpdate()

                    val generatedKeys = stmt.generatedKeys
                    if (generatedKeys.next()) {
                        plotId = generatedKeys.getInt(1)
                    } else {
                        conn.rollback()
                        logger.err("Nie udało się pobrać ID nowej działki.")
                        return null
                    }
                }

                // 2. Domyślne flagi
                val insertFlagSql = """
                INSERT INTO plot_flags (plot_id, flag_name, flag_value) VALUES (?, ?, ?)
            """.trimIndent()

                conn.prepareStatement(insertFlagSql).use { flagStmt ->
                    for ((flag, value) in defaultFlags) {
                        flagStmt.setInt(1, plotId)
                        flagStmt.setString(2, flag)
                        flagStmt.setString(3, value.toString())
                        flagStmt.addBatch()
                    }
                    flagStmt.executeBatch()
                }

                conn.commit()
                logger.debug("Utworzono działkę z domyślnymi flagami: plot_id=$plotId")
                return plotId
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas tworzenia działki: ${ex.message}")
            return null
        }
    }

    fun deletePlot(plotId: Int): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        try {
            connection.use { conn ->
                conn.autoCommit = false

                // Usunięcie działki — reszta powiązanych danych usunie się kaskadowo
                val deleteSql = "DELETE FROM plots WHERE plot_id = ?"

                conn.prepareStatement(deleteSql).use { stmt ->
                    stmt.setInt(1, plotId)
                    val affectedRows = stmt.executeUpdate()
                    if (affectedRows == 0) {
                        conn.rollback()
                        logger.warning("Nie znaleziono działki do usunięcia: plot_id=$plotId")
                        return false
                    }
                }

                conn.commit()
                logger.debug("Działka plot_id=$plotId została usunięta.")
                return true
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas usuwania działki: ${ex.message}")
            return false
        }
    }

    fun updatePlotDetails(plotId: Int, newName: String? = null, newRadius: Int? = null): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        if (newName == null && newRadius == null) {
            logger.warning("Brak danych do aktualizacji dla plot_id=$plotId.")
            return false
        }

        val updates = mutableListOf<String>()
        val params = mutableListOf<Any>()

        newName?.let {
            updates.add("name = ?")
            params.add(it)
        }

        newRadius?.let {
            updates.add("radius = ?")
            params.add(it)
        }

        val sql = "UPDATE plots SET ${updates.joinToString(", ")} WHERE plot_id = ?"

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    params.forEachIndexed { index, param ->
                        when (param) {
                            is String -> stmt.setString(index + 1, param)
                            is Int -> stmt.setInt(index + 1, param)
                        }
                    }
                    stmt.setInt(params.size + 1, plotId)

                    val rows = stmt.executeUpdate()
                    if (rows == 0) {
                        logger.warning("Nie znaleziono działki do aktualizacji: plot_id=$plotId")
                        return false
                    }

                    logger.debug("Działka plot_id=$plotId została zaktualizowana.")
                    return true
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas aktualizacji działki: ${ex.message}")
            return false
        }
    }

    /**
     * Aktualizacja flag do sprawdzenia, czy nie pomyliłem czegoś!
     *
     * Muszę pamiętać o sposobie użycia np.
     *
     * `updatePlotFlag(plotId = 42, flagName = "pvp", flagValue = true)`
     **/
    fun updatePlotFlag(plotId: Int, flagName: String, flagValue: Boolean): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        val sql = when (dbType) {
            "postgresql" -> """
            INSERT INTO plot_flags (plot_id, flag_name, flag_value)
            VALUES (?, ?, ?)
            ON CONFLICT (plot_id, flag_name) DO UPDATE SET flag_value = EXCLUDED.flag_value;
        """
            "sqlite" -> """
            INSERT INTO plot_flags (plot_id, flag_name, flag_value)
            VALUES (?, ?, ?)
            ON CONFLICT(plot_id, flag_name) DO UPDATE SET flag_value = excluded.flag_value;
        """
            "mysql", "mariadb" -> """
            INSERT INTO plot_flags (plot_id, flag_name, flag_value)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value);
        """
            else -> """
            MERGE INTO plot_flags (plot_id, flag_name, flag_value)
            KEY (plot_id, flag_name)
            VALUES (?, ?, ?);
        """ // dla H2, ale chyba zmienię w przyszłości miejscami na MySQL
        }.trimIndent()

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, flagName)
                    stmt.setString(3, flagValue.toString())
                    val rows = stmt.executeUpdate()
                    logger.debug("Zmieniono flagę '$flagName' na '$flagValue' dla plot_id=$plotId (rows=$rows).")
                    return true
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd przy aktualizacji flagi '$flagName' dla plot_id=$plotId: ${ex.message}")
            return false
        }
    }

    /**
     * Pobranie flag działki
     *
     * @param plotId
     * @return `Map<String, Boolean>`
     */
    fun getPlotFlags(plotId: Int): Map<String, Boolean> {
        val flags = mutableMapOf<String, Boolean>()
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return flags
        }

        val sql = "SELECT flag_name, flag_value FROM plot_flags WHERE plot_id = ?"

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("flag_name")
                            val valueRaw = rs.getString("flag_value").lowercase()
                            val value = valueRaw == "true" || valueRaw == "1" || valueRaw == "yes"
                            flags[name] = value
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas pobierania flag działki plot_id=$plotId: ${ex.message}")
        }

        return flags
    }

    /**
     * Pobranie pojedynczej flagi danej działki
     *
     * @param plotId
     * @param flagName
     * @return `String?`
     */
    fun getPlotFlag(plotId: Int, flagName: String): PlotFlagData? {
        val sql = "SELECT flag_value FROM plot_flags WHERE plot_id = ? AND flag_name = ?"

        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.setString(2, flagName)

                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        PlotFlagData(plotId, flagName, rs.getString("flag_value"))
                    } else null
                }
            }
        }
        return null
    }


    /**
     * Dodanie gracza do działki
     *
     * @param plotId
     * @param memberUuid
     * @param role
     * @return `Boolean`
     */
    fun addPlotMember(plotId: Int, memberUuid: UUID, role: String = "member"): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        val sql = when (dbType.lowercase()) {
            "sqlite", "postgresql" -> """
            INSERT INTO plot_members (plot_id, member_uuid, role)
            VALUES (?, ?, ?)
            ON CONFLICT(plot_id, member_uuid) DO UPDATE SET role = excluded.role
        """.trimIndent()

            "h2" -> """
            MERGE INTO plot_members (plot_id, member_uuid, role)
            KEY (plot_id, member_uuid)
            VALUES (?, ?, ?)
        """.trimIndent()

            else -> """
            INSERT INTO plot_members (plot_id, member_uuid, role)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE role = VALUES(role)
        """.trimIndent() // domyślnie MySQL/MariaDB
        }

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, memberUuid.toString())
                    stmt.setString(3, role)
                    stmt.executeUpdate()
                    logger.debug("Dodano członka działki: $memberUuid do plot_id=$plotId jako $role")
                    return true
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas dodawania członka do działki: ${ex.message}")
            return false
        }
    }

    fun removePlotMember(plotId: Int, memberUuid: UUID): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        val sql = """
        DELETE FROM plot_members
        WHERE plot_id = ? AND member_uuid = ?
    """.trimIndent()

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.setString(2, memberUuid.toString())
                    val affectedRows = stmt.executeUpdate()
                    logger.debug("Usunięto członka $memberUuid z działki $plotId (zmienione wiersze: $affectedRows)")
                    return affectedRows > 0
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas usuwania członka działki: ${ex.message}")
            return false
        }
    }

    /**
     * Zmiana roli członka działki
     *
     * @param plotId
     * @param memberUuid
     * @param newRole
     * @return `Boolean`
     */
    fun updatePlotMemberRole(plotId: Int, memberUuid: UUID, newRole: String): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }

        val sql = """
        UPDATE plot_members
        SET role = ?
        WHERE plot_id = ? AND member_uuid = ?
    """.trimIndent()

        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, newRole)
                    stmt.setInt(2, plotId)
                    stmt.setString(3, memberUuid.toString())
                    val affectedRows = stmt.executeUpdate()
                    logger.debug("Zmieniono rolę członka $memberUuid na $newRole w działce $plotId (zmienione wiersze: $affectedRows)")
                    return affectedRows > 0
                }
            }
        } catch (ex: SQLException) {
            logger.err("Błąd podczas aktualizacji roli członka działki: ${ex.message}")
            return false
        }
    }

    /**
     * Pobranie listy członków działki
     *
     * @param plotId
     * @return `Map<UUID, String>`
     */
    fun getPlotMembers(plotId: Int): List<PlotMemberData> {
        val result = mutableListOf<PlotMemberData>()
        val sql = "SELECT member_uuid, role FROM plot_members WHERE plot_id = ?"

        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            PlotMemberData(
                                plotId = plotId,
                                memberUUID = rs.getString("member_uuid"),
                                memberRole = rs.getString("role")
                            )
                        )
                    }
                }
            }
        }
        return result
    }


    /**
     * Sprawdzenie, czy gracz posiada już działkę
     *
     * @param ownerUuid
     * @return `Boolean`
     */
    fun playerOwnsPlot(ownerUuid: UUID): Boolean {
        val query = when (dbType) {
            "sqlite", "postgresql", "h2" -> "SELECT 1 FROM plots WHERE owner_uuid = ? LIMIT 1"
            else -> "SELECT 1 FROM plots WHERE owner_uuid = ? LIMIT 1"
        }

        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, ownerUuid.toString())
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
        return false
    }

    /**
     * Sprawdzenie, czy działka, a raczej jej teren istnieje w danej lokalizacji X, Z
     *
     * @param world
     * @param x
     * @param z
     * @return `Boolean`
     */
    fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        val query = when (dbType) {
            "sqlite", "postgresql", "h2" -> """
            SELECT * FROM plots WHERE world = ? AND
            (? BETWEEN x - radius AND x + radius) AND
            (? BETWEEN z - radius AND z + radius)
        """
            else -> """
            SELECT * FROM plots WHERE world = ? AND
            (? BETWEEN x - radius AND x + radius) AND
            (? BETWEEN z - radius AND z + radius)
        """
        }

        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, world)
                stmt.setInt(2, x)
                stmt.setInt(3, z)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            radius = rs.getInt("radius"),
                            world = rs.getString("world"),
                            name = rs.getString("name"),
                            creationTime = rs.getLong("creation_time")
                        )
                    } else null
                }
            }
        }
        return null
    }

    /**
     * Pobranie wszystkich działek gracza (których jest właścicielem lub członkiem)
     *
     * @param uuid
     * @return `List<PlotData>`
     */
    fun getPlayerPlots(uuid: UUID): List<PlotData> {
        val query = when (dbType) {
            "sqlite", "postgresql", "h2" -> """
            SELECT DISTINCT p.* FROM plots p
            LEFT JOIN plot_members m ON p.plot_id = m.plot_id
            WHERE p.owner_uuid = ? OR m.member_uuid = ?
        """
            else -> """
            SELECT DISTINCT p.* FROM plots p
            LEFT JOIN plot_members m ON p.plot_id = m.plot_id
            WHERE p.owner_uuid = ? OR m.member_uuid = ?
        """
        }

        val plots = mutableListOf<PlotData>()
        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setString(2, uuid.toString())
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        plots += PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            radius = rs.getInt("radius"),
                            world = rs.getString("world"),
                            name = rs.getString("name"),
                            creationTime = rs.getLong("creation_time")
                        )
                    }
                }
            }
        }
        return plots
    }

    /**
     * Sprawdzenie, czy działka nie koliduje z innymi działkami w danym świecie
     *
     * @param x
     * @param z
     * @param radius
     * @param world
     * @return `Boolean`
     */
    fun doesPlotOverlap(x: Int, z: Int, radius: Int, world: String): Boolean {
        getConnection()?.use { conn ->
            val query = when (dbType) {
                "sqlite", "h2" -> """
                SELECT 1 FROM plots
                WHERE world = ?
                  AND (x + radius) >= ?
                  AND (x - radius) <= ?
                  AND (z + radius) >= ?
                  AND (z - radius) <= ?
                LIMIT 1;
            """.trimIndent()

                "postgresql" -> """
                SELECT 1 FROM plots
                WHERE world = ?
                  AND (x + radius) >= ?
                  AND (x - radius) <= ?
                  AND (z + radius) >= ?
                  AND (z - radius) <= ?
                LIMIT 1;
            """.trimIndent()

                else -> """ -- MySQL / MariaDB
                SELECT 1 FROM plots
                WHERE world = ?
                  AND (x + radius) >= ?
                  AND (x - radius) <= ?
                  AND (z + radius) >= ?
                  AND (z - radius) <= ?
                LIMIT 1;
            """.trimIndent()
            }

            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, world)
                stmt.setInt(2, x - radius)
                stmt.setInt(3, x + radius)
                stmt.setInt(4, z - radius)
                stmt.setInt(5, z + radius)

                stmt.executeQuery().use { rs ->
                    return rs.next() // Jeśli istnieje przynajmniej 1 wynik — kolizja
                }
            }
        }
        return false
    }

    /**
     * Logowanie akcji na działce
     *
     */
    fun logPlotAction(entry: PlotLogEntry) {
        val sql = when (dbType) {
            "sqlite", "h2" -> """
            INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

            "postgresql" -> """
            INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

            else -> """
            INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        }

        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, entry.plotId)
                stmt.setString(2, entry.action)
                stmt.setString(3, entry.actorUUID.toString())
                stmt.setLong(4, entry.timestamp)

                stmt.executeUpdate()
            }
        }
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