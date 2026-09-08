package pl.syntaxdevteam.plotsx.databases

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import pl.syntaxdevteam.plotsx.PlotsX
import java.io.File
import java.sql.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry

class DatabaseHandler(private val plugin: PlotsX) {
    private var dataSource: HikariDataSource? = null
    private var logger = plugin.logger
    private val dbType = plugin.config.getString("database.type")?.lowercase() ?: "sqlite"
    private val claimLocks = ConcurrentHashMap<String, ReentrantLock>()

    sealed interface ClaimResult {
        data class Success(val plotId: Int) : ClaimResult
        data object LimitReached : ClaimResult
        data object AreaLimitReached : ClaimResult
        data object Overlap : ClaimResult
        data object DatabaseError : ClaimResult
    }

    sealed interface ExpandResult {
        data class Success(val newRadius: Int) : ExpandResult
        data object PlotNotFound : ExpandResult
        data object NotOwner : ExpandResult
        data object RadiusLimitReached : ExpandResult
        data object AreaLimitReached : ExpandResult
        data object Overlap : ExpandResult
        data object DatabaseError : ExpandResult
    }

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
                    "jdbc:mariadb://${plugin.config.getString("database.sql.host")}:${plugin.config.getString("database.sql.port")}/$dbName"
                hikariConfig.username = user
                hikariConfig.password = password
                hikariConfig.driverClassName = "org.mariadb.jdbc.Driver"
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
            hikariConfig.maximumPoolSize = 20
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
        //logger.debug("SQLite connection detected! I'm enabling WAL mode")
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                statement.execute("PRAGMA foreign_keys=ON;")
                //logger.debug("SQLite WAL mode enabled.")
            }
        } catch (e: SQLException) {
            logger.err("Failed to enable SQLite WAL mode. ${e.message}")
        }
    }

    fun createTables() {
        getConnection()?.use { conn ->
            conn.createStatement().use { statement ->
                DatabaseSchema.statements(dbType).forEach { statement.executeUpdate(it) }
            }
        } ?: error("No database connection.")
    }

    fun claimPlotAtomically(
        ownerUuid: UUID,
        actorUuid: UUID,
        world: String,
        x: Int,
        z: Int,
        y: Int,
        radius: Int,
        maxPlots: Int,
        maxTotalArea: Long,
        namePrefix: String
    ): ClaimResult {
        val claimLock = claimLocks.computeIfAbsent(world.lowercase(Locale.ROOT)) { ReentrantLock() }
        return claimLock.withLock {
            claimPlotInTransaction(
                ownerUuid, actorUuid, world, x, z, y, radius, maxPlots, maxTotalArea, namePrefix
            )
        }
    }

    private fun claimPlotInTransaction(
        ownerUuid: UUID,
        actorUuid: UUID,
        world: String,
        x: Int,
        z: Int,
        y: Int,
        radius: Int,
        maxPlots: Int,
        maxTotalArea: Long,
        namePrefix: String
    ): ClaimResult {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return ClaimResult.DatabaseError
        }
        val defaultFlags = PlotFlagRegistry.allFlags.values.associate { it.name to it.defaultValue }

        connection.use { conn ->
            try {
                conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
                conn.autoCommit = false

                val ownerPlotCount = conn.prepareStatement(
                    "SELECT COUNT(*) FROM plots WHERE owner_uuid = ?"
                ).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }

                if (maxPlots >= 0 && ownerPlotCount >= maxPlots) {
                    conn.rollback()
                    return ClaimResult.LimitReached
                }

                val ownedArea = conn.prepareStatement(
                    "SELECT radius FROM plots WHERE owner_uuid = ?"
                ).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        var sum = 0L
                        while (rs.next()) sum = saturatingAreaSum(sum, rs.getInt(1))
                        sum
                    }
                }
                val claimedArea = plotArea(radius)
                if (ownedArea > maxTotalArea - claimedArea) {
                    conn.rollback()
                    return ClaimResult.AreaLimitReached
                }

                val overlap = conn.prepareStatement(
                    """
                    SELECT 1 FROM plots
                    WHERE world = ?
                      AND (x + radius) >= ?
                      AND (x - radius) <= ?
                      AND (z + radius) >= ?
                      AND (z - radius) <= ?
                    LIMIT 1
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, world)
                    stmt.setInt(2, x - radius)
                    stmt.setInt(3, x + radius)
                    stmt.setInt(4, z - radius)
                    stmt.setInt(5, z + radius)
                    stmt.executeQuery().use(ResultSet::next)
                }

                if (overlap) {
                    conn.rollback()
                    return ClaimResult.Overlap
                }

                val plotId = conn.prepareStatement(
                    """
                    INSERT INTO plots (owner_uuid, x, z, y, radius, world, name, creation_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    Statement.RETURN_GENERATED_KEYS
                ).use { stmt ->
                    stmt.setString(1, ownerUuid.toString())
                    stmt.setInt(2, x)
                    stmt.setInt(3, z)
                    stmt.setInt(4, y)
                    stmt.setInt(5, radius)
                    stmt.setString(6, world)
                    stmt.setString(7, "$namePrefix ${ownerPlotCount + 1}")
                    stmt.setLong(8, System.currentTimeMillis())
                    stmt.executeUpdate()
                    stmt.generatedKeys.use { keys ->
                        if (keys.next()) keys.getInt(1) else throw SQLException("Nie udało się pobrać ID nowej działki.")
                    }
                }

                conn.prepareStatement(
                    "INSERT INTO plot_flags (plot_id, flag_name, flag_value) VALUES (?, ?, ?)"
                ).use { flagStmt ->
                    for ((flag, value) in defaultFlags) {
                        flagStmt.setInt(1, plotId)
                        flagStmt.setString(2, flag)
                        flagStmt.setString(3, value.toString())
                        flagStmt.addBatch()
                    }
                    flagStmt.executeBatch()
                }

                conn.prepareStatement(
                    "INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)"
                ).use { logStmt ->
                    logStmt.setInt(1, plotId)
                    logStmt.setString(2, "CREATE")
                    logStmt.setString(3, actorUuid.toString())
                    logStmt.setLong(4, System.currentTimeMillis())
                    logStmt.executeUpdate()
                }

                conn.commit()
                logger.debug("Utworzono działkę z domyślnymi flagami: plot_id=$plotId")
                return ClaimResult.Success(plotId)
            } catch (ex: SQLException) {
                try {
                    conn.rollback()
                } catch (rollbackException: SQLException) {
                    logger.err("Nie udało się wycofać tworzenia działki: ${rollbackException.message}")
                }
                logger.err("Błąd podczas atomowego tworzenia działki: ${ex.message}")
                return ClaimResult.DatabaseError
            }
        }
    }

    fun deletePlot(plotId: Int): Boolean {
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return false
        }
        logger.debug("Próba nawiązania połączenie z deletePlot()")
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
            logger.debug("Brak danych do aktualizacji dla plot_id=$plotId.")
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
        logger.debug("Próba nawiązania połączenie z updatePlotDetails()")
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

    fun getExpansionLevel(plotId: Int): Int {
        val connection = getConnection() ?: throw SQLException("No database connection")
        return connection.use { conn -> readExpansionLevel(conn, plotId) }
    }

    private fun readExpansionLevel(conn: Connection, plotId: Int): Int =
        conn.prepareStatement("SELECT expansion_level FROM plot_expansion_levels WHERE plot_id = ?").use { stmt ->
            stmt.setInt(1, plotId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    fun expandPlotAtomically(
        plotId: Int,
        ownerUuid: UUID,
        actorUuid: UUID,
        requestedRadius: Int,
        maxRadius: Int,
        maxTotalArea: Long,
        expectedLevel: Int
    ): ExpandResult {
        val plot = getPlotById(plotId) ?: return ExpandResult.PlotNotFound
        val lock = claimLocks.computeIfAbsent(plot.world.lowercase(Locale.ROOT)) { ReentrantLock() }
        return lock.withLock {
            val connection = getConnection() ?: return@withLock ExpandResult.DatabaseError
            connection.use { conn ->
                try {
                    conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
                    conn.autoCommit = false
                    if (readExpansionLevel(conn, plotId) != expectedLevel) {
                        conn.rollback()
                        return@withLock ExpandResult.DatabaseError
                    }
                    val current = conn.prepareStatement(
                        "SELECT owner_uuid, x, z, radius, world FROM plots WHERE plot_id = ?"
                    ).use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.executeQuery().use { rs ->
                            if (!rs.next()) null else arrayOf(
                                rs.getString("owner_uuid"), rs.getInt("x"), rs.getInt("z"),
                                rs.getInt("radius"), rs.getString("world")
                            )
                        }
                    } ?: run {
                        conn.rollback()
                        return@withLock ExpandResult.PlotNotFound
                    }

                    if (current[0] != ownerUuid.toString()) {
                        conn.rollback()
                        return@withLock ExpandResult.NotOwner
                    }
                    val x = current[1] as Int
                    val z = current[2] as Int
                    val oldRadius = current[3] as Int
                    val world = current[4] as String
                    if (requestedRadius <= oldRadius || requestedRadius > maxRadius) {
                        conn.rollback()
                        return@withLock ExpandResult.RadiusLimitReached
                    }

                    val totalArea = conn.prepareStatement("SELECT radius FROM plots WHERE owner_uuid = ?").use { stmt ->
                        stmt.setString(1, ownerUuid.toString())
                        stmt.executeQuery().use { rs ->
                            var sum = 0L
                            while (rs.next()) sum = saturatingAreaSum(sum, rs.getInt(1))
                            sum
                        }
                    }
                    val proposedArea = plotArea(requestedRadius)
                    val currentArea = plotArea(oldRadius)
                    if (totalArea - currentArea > maxTotalArea - proposedArea) {
                        conn.rollback()
                        return@withLock ExpandResult.AreaLimitReached
                    }

                    val overlap = conn.prepareStatement(
                        """
                        SELECT 1 FROM plots
                        WHERE plot_id <> ? AND world = ?
                          AND (x + radius) >= ? AND (x - radius) <= ?
                          AND (z + radius) >= ? AND (z - radius) <= ?
                        LIMIT 1
                        """.trimIndent()
                    ).use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.setString(2, world)
                        stmt.setInt(3, x - requestedRadius)
                        stmt.setInt(4, x + requestedRadius)
                        stmt.setInt(5, z - requestedRadius)
                        stmt.setInt(6, z + requestedRadius)
                        stmt.executeQuery().use(ResultSet::next)
                    }
                    if (overlap) {
                        conn.rollback()
                        return@withLock ExpandResult.Overlap
                    }

                    conn.prepareStatement("DELETE FROM plot_expansion_levels WHERE plot_id = ?").use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.executeUpdate()
                    }
                    conn.prepareStatement("INSERT INTO plot_expansion_levels (plot_id, expansion_level) VALUES (?, ?)").use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.setInt(2, expectedLevel + 1)
                        stmt.executeUpdate()
                    }
                    conn.prepareStatement("UPDATE plots SET radius = ? WHERE plot_id = ?").use { stmt ->
                        stmt.setInt(1, requestedRadius)
                        stmt.setInt(2, plotId)
                        stmt.executeUpdate()
                    }
                    conn.prepareStatement(
                        "INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)"
                    ).use { stmt ->
                        stmt.setInt(1, plotId)
                        stmt.setString(2, "EXPAND:$oldRadius->$requestedRadius")
                        stmt.setString(3, actorUuid.toString())
                        stmt.setLong(4, System.currentTimeMillis())
                        stmt.executeUpdate()
                    }
                    conn.commit()
                    ExpandResult.Success(requestedRadius)
                } catch (exception: SQLException) {
                    try { conn.rollback() } catch (_: SQLException) { }
                    logger.err("Błąd podczas rozszerzania działki $plotId: ${exception.message}")
                    ExpandResult.DatabaseError
                }
            }
        }
    }

    private fun plotArea(radius: Int): Long {
        val side = radius.toLong() * 2L + 1L
        return if (side > 3_037_000_499L) Long.MAX_VALUE else side * side
    }

    private fun saturatingAreaSum(sum: Long, radius: Int): Long {
        val area = plotArea(radius)
        return if (Long.MAX_VALUE - sum < area) Long.MAX_VALUE else sum + area
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
        logger.debug("Próba nawiązania połączenie z updatePlotFlag()")
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
     * @return `List<PlotFlagData>`
     */
    fun getPlotFlags(plotId: Int): List<PlotFlagData> {
        val flags = mutableListOf<PlotFlagData>()
        val connection = getConnection() ?: run {
            logger.err("Brak połączenia z bazą danych.")
            return flags
        }

        val sql = "SELECT flag_name, flag_value FROM plot_flags WHERE plot_id = ?"

        logger.debug("Próba nawiązania połączenie z getPlotFlags()")
        try {
            connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setInt(1, plotId)
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("flag_name")
                            val value = rs.getString("flag_value")
                            flags.add(PlotFlagData(plotId, name, value))
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

        logger.debug("Próba nawiązania połączenie z getPlotFlag()")
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

        logger.debug("Próba nawiązania połączenie z addPlotMember()")
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

        logger.debug("Próba nawiązania połączenie z removePlotMember()")
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

    fun transferPlotOwnership(plotId: Int, expectedOwner: UUID, recipient: UUID,
                              maxPlots: Int, maxRadius: Int, maxArea: Long): Boolean {
        val plot = getPlotById(plotId) ?: return false
        return claimLocks.computeIfAbsent(plot.world.lowercase(Locale.ROOT)) { ReentrantLock() }.withLock {
            (getConnection() ?: return@withLock false).use {
                OwnershipTransfer.transfer(it, plotId, expectedOwner, recipient, maxPlots, maxRadius, maxArea)
            }
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

        logger.debug("Próba nawiązania połączenie z updatePlotMemberRole()")
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

        logger.debug("Próba nawiązania połączenie z getPlotMembers()")
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, plotId)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(
                            PlotMemberData(
                                plotId = plotId,
                                memberUuid = rs.getString("member_uuid"),
                                memberRole = rs.getString("role")
                            )
                        )
                    }
                }
            }
        }
        return result
    }

fun getPlotsFromAllUsers(): List<PlotData> {
    val query = "SELECT * FROM plots"
    val plots = mutableListOf<PlotData>()

    logger.debug("Próba nawiązania połączenia z getPlotsFromAllUsers()")
    getConnection()?.use { conn ->
        conn.prepareStatement(query).use { stmt ->
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    plots.add(
                        PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            y = rs.getInt("y"),
                            radius = rs.getInt("radius"),
                            world = rs.getString("world"),
                            name = rs.getString("name"),
                            creationTime = rs.getLong("creation_time")
                        )
                    )
                }
            }
        }
    } ?: logger.err("Brak połączenia z bazą danych w getPlotsFromAllUsers().")

    return plots
}

    /**
     * Sprawdzenie, czy gracz posiada już działkę
     *
     * @param ownerUuid
     * @return `Boolean`
     */
    fun playerOwnsPlot(ownerUuid: UUID): Boolean {
        val query = "SELECT 1 FROM plots WHERE owner_uuid = ? LIMIT 1"

        logger.debug("Próba nawiązania połączenie z playerOwnsPlot()")
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

        logger.debug("Próba nawiązania połączenie z getPlotAtLocation()")
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
                            y = rs.getInt("y"),
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
     * Pobranie działki po nazwie
     *
     * @param name
     * @param ownerUuid
     * @return `PlotData?`
     */
    fun getPlotByName(name: String, ownerUuid: UUID): PlotData? {
        val query = "SELECT * FROM plots WHERE name = ? AND owner_uuid = ?"

        logger.debug("Próba nawiązania połączenie z getPlotByName()")
        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, name)
                stmt.setString(2, ownerUuid.toString())
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            y = rs.getInt("y"),
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
        logger.debug("Próba nawiązania połączenie z getPlayerPlots()")
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
                            y = rs.getInt("y"),
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

    fun getPlotsByOwner(owner: UUID): List<PlotData> {
        val query = "SELECT * FROM plots WHERE owner_uuid = ?"
        val plots = mutableListOf<PlotData>()
        logger.debug("Próba nawiązania połączenie z getPlotsByOwner()")
        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setString(1, owner.toString())
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        plots += PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            y = rs.getInt("y"),
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

    fun getPlotById(plotId: Int): PlotData? {
        val query = "SELECT * FROM plots WHERE plot_id = ?"

        logger.debug("Próba nawiązania połączenia z getPlotById()")
        getConnection()?.use { conn ->
            conn.prepareStatement(query).use { stmt ->
                stmt.setInt(1, plotId)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) {
                        PlotData(
                            id = rs.getInt("plot_id"),
                            ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                            x = rs.getInt("x"),
                            z = rs.getInt("z"),
                            y = rs.getInt("y"),
                            radius = rs.getInt("radius"),
                            world = rs.getString("world"),
                            name = rs.getString("name"),
                            creationTime = rs.getLong("creation_time")
                        )
                    } else null
                }
            }
        }
        logger.warning("Nie znaleziono działki o plot_id=$plotId")
        return null
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

            logger.debug("Próba nawiązania połączenie z doesPlotOverlap()")
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
        logger.debug("Próba nawiązania połączenie z logPlotAction()")

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

    fun exportDatabase(dialect: String = dbType): File {
        val target = SqlBackup.dialect(dialect)
        return (getConnection() ?: error("No database connection.")).use { conn ->
            SqlBackup.export(conn, target, File(plugin.dataFolder, "dump"))
        }
    }

    fun importDatabase() {
        (getConnection() ?: error("No database connection.")).use { conn ->
            SqlBackup.restore(conn, SqlBackup.dialect(dbType), File(plugin.dataFolder, "dump/backup.sql"))
        }
        plugin.cacheManager.reloadAllCachesSync()
    }
}
