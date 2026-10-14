package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.identity.IdentityMigrationBridgeResult
import pl.syntaxdevteam.plotsx.identity.IdentityMigrationBridgeStatus
import pl.syntaxdevteam.plotsx.identity.IdentityMigrationStore
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry
import pl.syntaxdevteam.syntaxdb.Database
import pl.syntaxdevteam.syntaxdb.config.DatabaseType
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * PlotsX persistence facade.
 *
 * SyntaxDB owns the pool, JDBC drivers and lifecycle. The remaining Connection-based helpers are
 * intentionally scoped through SyntaxDB's managed JDBC interoperability API while they are gradually
 * moved to the higher-level query/CRUD API.
 */
class DatabaseHandler(private val plugin: PlotsX) {
    private var syntaxDb: Database? = null
    private var logger = plugin.logger
    private val configuredDbType = plugin.config.getString("database.type")?.trim()?.lowercase(Locale.ROOT) ?: "sqlite"
    /** PlotsX SQL schema dialect. MariaDB remains a backwards-compatible alias for MySQL. */
    private val dbType = when (configuredDbType) {
        "mariadb" -> "mysql"
        else -> configuredDbType
    }
    private val claimLocks = ConcurrentHashMap<String, ReentrantLock>()
    private val closing = AtomicBoolean(false)
    private val pendingServerCalls = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    private val jdbcScope = object : JdbcConnectionScope {
        override fun <T> withConnection(block: (Connection) -> T): T = withJdbcConnection(block)
    }

    sealed interface ClaimResult {
        data class Success(val plotId: Int) : ClaimResult
        data object LimitReached : ClaimResult
        data object ChunkLimitReached : ClaimResult
        data object AreaLimitReached : ClaimResult
        data object Overlap : ClaimResult
        data object DatabaseError : ClaimResult
    }

    sealed interface ExpandResult {
        data class Success(val segment: PlotSegment) : ExpandResult
        data object PlotNotFound : ExpandResult
        data object NotOwner : ExpandResult
        data object RadiusLimitReached : ExpandResult
        data object AreaLimitReached : ExpandResult
        data object Overlap : ExpandResult
        data object DatabaseError : ExpandResult
    }

    /** Starts one SyntaxDB instance for the plugin lifecycle. */
    @Synchronized
    fun openConnection() {
        closing.set(false)
        if (syntaxDb?.isReady == true) return
        syntaxDb?.let { previous ->
            runCatching { previous.close() }
                .onFailure { logger.warning("Previous SyntaxDB instance could not be closed cleanly: ${it.message}") }
        }
        syntaxDb = null

        val type = when (dbType) {
            "mysql" -> DatabaseType.MYSQL
            "postgresql" -> DatabaseType.POSTGRESQL
            "sqlite" -> DatabaseType.SQLITE
            "h2" -> throw IllegalArgumentException(
                "database.type=h2 is no longer a runtime backend after the SyntaxDB migration. " +
                    "Use sqlite, mysql/mariadb or postgresql; legacy H2 backup conversion remains available."
            )
            else -> throw IllegalArgumentException(
                "Unsupported database type '$configuredDbType'. Supported by PlotsX: sqlite, mysql, mariadb, postgresql."
            )
        }

        val dbName = plugin.config.getString("database.sql.dbname") ?: plugin.name
        val user = plugin.config.getString("database.sql.username") ?: "ROOT"
        val password = plugin.config.getString("database.sql.password") ?: "U5eV3ryStr0ngP4ssw0rd"
        val host = plugin.config.getString("database.sql.host") ?: "localhost"
        val configuredPort = plugin.config.getInt("database.sql.port", if (type == DatabaseType.POSTGRESQL) 5432 else 3306)

        if (configuredDbType == "mariadb") {
            logger.info("database.type=mariadb is using SyntaxDB's MySQL-compatible dialect and Connector/J runtime.")
        }
        logger.debug("Starting SyntaxDB for database type: $configuredDbType (dialect=$dbType)")

        syntaxDb = Database.connect(type) {
            when (type) {
                DatabaseType.SQLITE -> {
                    file = File(plugin.dataFolder, "$dbName.db").absolutePath
                    // Applied to every physical SQLite connection, including high-level SyntaxDB CRUD calls.
                    property("foreign_keys", "true")
                    property("journal_mode", "WAL")
                    property("busy_timeout", "30000")
                }
                DatabaseType.MYSQL, DatabaseType.POSTGRESQL -> {
                    this.host = host
                    port = configuredPort
                    database = dbName
                    username = user
                    this.password = password
                }
                DatabaseType.SQL_SERVER -> error("SQL Server schema support is not enabled in PlotsX")
            }
            pool {
                poolName = "PlotsX-SyntaxDB"
                connectionTimeoutMs = 30_000
                if (type == DatabaseType.SQLITE) {
                    maximumPoolSize = 2
                    minimumIdle = 1
                    idleTimeoutMs = 10_000
                    maxLifetimeMs = 60_000
                    leakDetectionThresholdMs = 0
                } else {
                    maximumPoolSize = 20
                    minimumIdle = 2
                    idleTimeoutMs = 600_000
                    maxLifetimeMs = 1_800_000
                    leakDetectionThresholdMs = 2_000
                }
            }
            lifecycle {
                validateOnStart = true
                validationTimeoutSeconds = 5
                shutdownTimeoutMs = 30_000
            }
        }

        logger.success("SyntaxDB initialized successfully for $configuredDbType.")
    }

    /** Stops admission of new work and lets SyntaxDB gracefully drain/close its owned pool. */
    fun closeConnection() {
        if (!closing.compareAndSet(false, true)) return
        pendingServerCalls.forEach { it.completeExceptionally(IllegalStateException("PlotsX is shutting down")) }
        pendingServerCalls.clear()
        claimLocks.clear()
        val database = syntaxDb
        syntaxDb = null
        try {
            database?.close()
            logger.info("SyntaxDB database lifecycle stopped.")
        } catch (failure: Exception) {
            logger.err("Error while closing SyntaxDB: ${failure.message}")
        }
    }

    private fun database(): Database {
        check(!closing.get()) { "PlotsX database is shutting down" }
        return syntaxDb?.takeIf { it.isReady } ?: error("SyntaxDB is not connected")
    }

    /**
     * Scoped JDBC bridge for existing atomic persistence helpers. A Connection never escapes SyntaxDB's
     * lifecycle admission and is returned to the managed pool automatically after [block].
     */
    internal fun <T> withJdbcConnection(block: (Connection) -> T): T = database().withConnection { connection ->
        if (dbType == "sqlite") enableSQLitePragmas(connection)
        block(connection)
    }

    private fun enableSQLitePragmas(connection: Connection) {
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode=WAL;")
                statement.execute("PRAGMA foreign_keys=ON;")
                statement.execute("PRAGMA busy_timeout=30000;")
            }
        } catch (failure: SQLException) {
            logger.err("Failed to configure SQLite connection. ${failure.message}")
        }
    }

    private fun <T> protectedMutation(action: () -> T): T =
        plugin.protectionCoordinator.mutate({ plugin.cacheManager.reloadAllCachesSync() }, action)

    fun createTables() {
        withJdbcConnection { conn ->
            conn.createStatement().use { statement ->
                DatabaseSchema.statements(dbType).forEach { statement.executeUpdate(it) }
            }
            DatabaseMigrations.migrate(conn, dbType)
            OperationJournal.migrate(conn)
            PlotSpawnRepository.migrate(conn)
            IdentityMigrationStore.migrateSchema(conn)
            plugin.identityAliasRegistry.replaceAll(IdentityMigrationStore.loadAliases(conn))
            reportInterruptedOperations(conn)
        }
    }

    private fun reportInterruptedOperations(conn: Connection) {
        for (operation in OperationJournal.recoverInterrupted(conn, System.currentTimeMillis())) logger.err(
            "PAYMENT RECONCILIATION REQUIRED: operation=${operation.id} plot=${operation.plotId} " +
                "owner=${operation.owner} provider=${operation.provider} amount=${operation.amount} " +
                "currency=${operation.currency} state=${operation.state}"
        )
    }

    internal fun loadPlotCacheData(plotId: Int? = null): PlotCacheData =
        withJdbcConnection { PlotCacheLoader.load(it, plotId) }

    internal fun inspectIdentityMigration(
        migrationId: UUID,
        sourceUuid: UUID,
        targetUuid: UUID,
    ): IdentityMigrationBridgeResult = withJdbcConnection {
        IdentityMigrationStore.inspect(it, migrationId, sourceUuid, targetUuid)
    }

    internal fun migrateIdentity(
        migrationId: UUID,
        sourceUuid: UUID,
        targetUuid: UUID,
    ): IdentityMigrationBridgeResult = protectedMutation {
        withJdbcConnection { connection ->
            val result = IdentityMigrationStore.migrate(connection, migrationId, sourceUuid, targetUuid)
            if (result.status == IdentityMigrationBridgeStatus.SUCCESS) {
                plugin.identityAliasRegistry.put(sourceUuid, targetUuid)
            }
            result
        }
    }

    internal fun rollbackIdentityMigration(
        migrationId: UUID,
        sourceUuid: UUID,
        targetUuid: UUID,
    ): IdentityMigrationBridgeResult = protectedMutation {
        withJdbcConnection { connection ->
            val result = IdentityMigrationStore.rollback(connection, migrationId, sourceUuid, targetUuid)
            if (result.status == IdentityMigrationBridgeStatus.ROLLED_BACK) {
                plugin.identityAliasRegistry.remove(sourceUuid, targetUuid)
            }
            result
        }
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
        namePrefix: String,
        maxChunksPerPlot: Int = Int.MAX_VALUE,
        maxChunksOwned: Int = Int.MAX_VALUE,
    ): ClaimResult = protectedMutation {
        try {
            withJdbcConnection { conn ->
                val shape = pl.syntaxdevteam.plotsx.claiming.ClaimGeometryFactory.create(plugin.claimMode, x, z, radius)
                when (val result = ClaimTransaction.create(
                    conn, ownerUuid, actorUuid, world, x, y, z, shape,
                    maxPlots, maxTotalArea, maxChunksPerPlot, maxChunksOwned, namePrefix,
                    PlotFlagRegistry.allFlags.values.associate { it.name to it.defaultValue },
                )) {
                    is ClaimTransaction.Result.Success -> ClaimResult.Success(result.id)
                    ClaimTransaction.Result.PlotLimit -> ClaimResult.LimitReached
                    ClaimTransaction.Result.AreaLimit -> ClaimResult.AreaLimitReached
                    ClaimTransaction.Result.ChunkLimit -> ClaimResult.ChunkLimitReached
                    ClaimTransaction.Result.Overlap -> ClaimResult.Overlap
                }
            }
        } catch (failure: Exception) {
            logger.err("Claim transaction failed: ${failure.message}")
            ClaimResult.DatabaseError
        }
    }

    internal fun relocatePlot(expected: PlotData, target: PlotRelocation.Target, actor: UUID): PlotRelocation.Result =
        protectedMutation { withJdbcConnection { PlotRelocation.move(it, expected, target, actor) } }

    fun deletePlot(plotId: Int): Boolean = protectedMutation { performDeletePlot(plotId) }

    private fun performDeletePlot(plotId: Int): Boolean = try {
        val affectedRows = database().delete("plots") { "plot_id" eq plotId }
        if (affectedRows == 0) {
            logger.warning("Nie znaleziono działki do usunięcia: plot_id=$plotId")
            false
        } else {
            logger.debug("Działka plot_id=$plotId została usunięta przez SyntaxDB.")
            true
        }
    } catch (failure: Exception) {
        logger.err("Błąd podczas usuwania działki: ${failure.message}")
        false
    }

    fun updatePlotDetails(plotId: Int, newName: String? = null, newRadius: Int? = null): Boolean =
        protectedMutation { performUpdatePlotDetails(plotId, newName, newRadius) }

    private fun performUpdatePlotDetails(plotId: Int, newName: String? = null, newRadius: Int? = null): Boolean {
        if (newRadius != null) {
            val plot = getPlotById(plotId) ?: return false
            if (plot.radius == null || newRadius < 1) return false
            val candidate = plot.copy(radius = newRadius).geometry
            if (getPlotsFromAllUsers().any {
                    it.id != plotId && it.world.equals(plot.world, true) && it.geometry.intersects(candidate)
                }) return false
        }
        if (newName == null && newRadius == null) return false

        val assignments = mutableListOf<String>()
        val parameters = mutableListOf<Any?>()
        newName?.let { assignments += "name = ?"; parameters += it }
        newRadius?.let {
            assignments += "radius = ?"
            assignments += "geometry_revision = geometry_revision + 1"
            parameters += it
        }
        parameters += plotId

        return try {
            val rows = database().execute(
                "UPDATE plots SET ${assignments.joinToString(", ")} WHERE plot_id = ?",
                parameters,
            )
            if (rows == 0) logger.warning("Nie znaleziono działki do aktualizacji: plot_id=$plotId")
            rows > 0
        } catch (failure: Exception) {
            logger.err("Błąd podczas aktualizacji działki: ${failure.message}")
            false
        }
    }

    private fun readSegments(conn: Connection, plotId: Int): List<PlotSegment> =
        conn.prepareStatement("SELECT x, z, radius FROM plot_segments WHERE plot_id = ?").use { stmt ->
            stmt.setInt(1, plotId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(PlotSegment(rs.getInt(1), rs.getInt(2), rs.getInt(3)))
                }
            }
        }

    /** Worker-thread purchase entry point; Bukkit and economy calls return to the server thread. */
    internal fun purchaseExpansion(
        operation: OperationJournal.Operation,
        level: Int,
        limits: ChunkExpansionTransaction.Limits,
        account: pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy.Account?,
        validate: () -> Boolean,
        classicLimits: ClassicExpansionTransaction.Limits? = null,
    ): ExpansionPurchaseService.Result {
        check(!org.bukkit.Bukkit.isPrimaryThread()) { "Expansion purchases must run off the server thread" }
        val workflowStarted = System.nanoTime()
        val serverCallbackNanos = java.util.concurrent.atomic.AtomicLong()
        val cachePublicationNanos = java.util.concurrent.atomic.AtomicLong()
        val calls = object : ExpansionPurchaseService.ServerCalls {
            override fun refund(action: () -> Boolean): Boolean {
                check(!closing.get()) { "PlotsX is shutting down" }
                val future = CompletableFuture<Boolean>()
                pendingServerCalls.add(future)
                try {
                    plugin.schedulerAdapter.runSync(Runnable {
                        if (!future.isDone && !closing.get()) try {
                            future.complete(action())
                        } catch (failure: Throwable) {
                            future.completeExceptionally(failure)
                        }
                    })
                    return future.get(30, java.util.concurrent.TimeUnit.SECONDS)
                } catch (failure: Exception) {
                    future.cancel(false)
                    throw failure
                } finally {
                    pendingServerCalls.remove(future)
                }
            }

            override fun <T> call(action: () -> T): T {
                check(!closing.get()) { "PlotsX is shutting down" }
                val future = CompletableFuture<T>()
                pendingServerCalls.add(future)
                val player = plugin.server.getPlayer(operation.actor)
                if (player == null) {
                    pendingServerCalls.remove(future)
                    error("Purchase actor is no longer online")
                }
                val scheduled = player.scheduler.run(plugin, {
                    val callbackStarted = System.nanoTime()
                    if (!future.isDone && !closing.get()) try {
                        future.complete(action())
                    } catch (failure: Throwable) {
                        future.completeExceptionally(failure)
                    } finally {
                        serverCallbackNanos.addAndGet(System.nanoTime() - callbackStarted)
                    }
                }, { future.completeExceptionally(IllegalStateException("Purchase actor disconnected")) })
                if (scheduled == null) future.completeExceptionally(IllegalStateException("Purchase actor disconnected"))
                try {
                    return future.get(30, java.util.concurrent.TimeUnit.SECONDS)
                } catch (failure: Exception) {
                    future.cancel(false)
                    throw failure
                } finally {
                    pendingServerCalls.remove(future)
                }
            }
        }

        val result = ExpansionPurchaseService(
            jdbcScope,
            plugin.protectionCoordinator,
            {
                val publicationStarted = System.nanoTime()
                try {
                    plugin.cacheManager.reloadPlotSync(operation.plotId)
                } finally {
                    cachePublicationNanos.addAndGet(System.nanoTime() - publicationStarted)
                }
            },
            calls,
            { logger.err(it) },
        ).purchase(operation, level, limits, account, validate, classicLimits)
        logger.debug(
            "PlotsX expansion timings: database workflow=${formatMillis(System.nanoTime() - workflowStarted)} ms [worker], " +
                "cache publication=${formatMillis(cachePublicationNanos.get())} ms [worker], " +
                "server callbacks=${formatMillis(serverCallbackNanos.get())} ms [main], operation=${operation.id}, result=$result"
        )
        return result
    }

    private fun formatMillis(nanos: Long): String = "%.3f".format(Locale.ROOT, nanos / 1_000_000.0)

    /** Internal persistence entry point. Do not expose as a paid purchase before journal integration. */
    internal fun expandChunkAtomically(
        request: ChunkExpansionTransaction.Request,
        limits: ChunkExpansionTransaction.Limits,
    ): ChunkExpansionTransaction.Result = protectedMutation {
        withJdbcConnection { ChunkExpansionTransaction.expand(it, request, limits) }
    }

    /** Removes exactly one chunk while publishing the new protection snapshot atomically. */
    internal fun removeChunkAtomically(request: ChunkRemovalTransaction.Request): ChunkRemovalTransaction.Result =
        protectedMutation { withJdbcConnection { ChunkRemovalTransaction.remove(it, request) } }

    fun expandPlotAtomically(
        plotId: Int,
        ownerUuid: UUID,
        actorUuid: UUID,
        direction: ExpansionDirection,
        sourceSegment: PlotSegment,
        expectedSegment: PlotSegment,
        maxRadius: Int,
        maxTotalArea: Long,
        expectedSegmentCount: Int,
    ): ExpandResult {
        var transactionNanos = 0L
        var publicationNanos = 0L
        val result = plugin.protectionCoordinator.mutate({
            val started = System.nanoTime()
            try {
                plugin.cacheManager.reloadPlotSync(plotId)
            } finally {
                publicationNanos = System.nanoTime() - started
            }
        }, {
            val started = System.nanoTime()
            try {
                performExpandPlotAtomically(
                    plotId, ownerUuid, actorUuid, direction, sourceSegment, expectedSegment,
                    maxRadius, maxTotalArea, expectedSegmentCount,
                )
            } finally {
                transactionNanos = System.nanoTime() - started
            }
        })
        logger.debug(
            "PlotsX expansion timings: classic database transaction=${formatMillis(transactionNanos)} ms [worker], " +
                "cache publication=${formatMillis(publicationNanos)} ms [worker], plot=$plotId, result=$result"
        )
        return result
    }

    private fun performExpandPlotAtomically(
        plotId: Int,
        ownerUuid: UUID,
        actorUuid: UUID,
        direction: ExpansionDirection,
        sourceSegment: PlotSegment,
        expectedSegment: PlotSegment,
        maxRadius: Int,
        maxTotalArea: Long,
        expectedSegmentCount: Int,
    ): ExpandResult {
        val plot = getPlotById(plotId) ?: return ExpandResult.PlotNotFound
        if (plot.radius == null) return ExpandResult.DatabaseError
        val lock = claimLocks.computeIfAbsent(plot.world.lowercase(Locale.ROOT)) { ReentrantLock() }
        return lock.withLock {
            try {
                withJdbcConnection { conn ->
                    conn.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
                    conn.autoCommit = false
                    try {
                        val current = conn.prepareStatement(
                            "SELECT owner_uuid, x, z, radius, world FROM plots WHERE plot_id = ?"
                        ).use { stmt ->
                            stmt.setInt(1, plotId)
                            stmt.executeQuery().use { rs ->
                                if (!rs.next()) null else arrayOf<Any>(
                                    rs.getString("owner_uuid"), rs.getInt("x"), rs.getInt("z"),
                                    rs.getInt("radius"), rs.getString("world"),
                                )
                            }
                        } ?: run {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.PlotNotFound
                        }

                        if (current[0] != ownerUuid.toString()) {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.NotOwner
                        }
                        val x = current[1] as Int
                        val z = current[2] as Int
                        val oldRadius = current[3] as Int
                        val world = current[4] as String
                        val fresh = plot.copy(x = x, z = z, radius = oldRadius, extensions = readSegments(conn, plotId))
                        val target = fresh.expansion(direction, sourceSegment)
                        if (target == null || target != expectedSegment || fresh.extensions.size != expectedSegmentCount) {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.DatabaseError
                        }
                        val extent = (fresh.segments + target).maxOf {
                            maxOf(kotlin.math.abs(it.x.toLong() - x), kotlin.math.abs(it.z.toLong() - z)) + it.radius
                        }
                        if (extent > maxRadius) {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.RadiusLimitReached
                        }

                        val allPlots = PlotRepository.readAll(conn)
                        val totalArea = allPlots.filter { it.ownerUuid == ownerUuid }.fold(0L) { total, candidate ->
                            ClaimTransaction.saturatedAdd(total, candidate.geometry.area)
                        }
                        val proposedArea = target.area
                        if (totalArea > maxTotalArea - proposedArea) {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.AreaLimitReached
                        }

                        val candidateShape = pl.syntaxdevteam.plotsx.geometry.ClassicGeometry(target)
                        val overlap = allPlots.any {
                            it.id != plotId && it.world.equals(world, true) && it.geometry.intersects(candidateShape)
                        }
                        if (overlap) {
                            conn.rollback()
                            return@withJdbcConnection ExpandResult.Overlap
                        }

                        conn.prepareStatement(
                            "INSERT INTO plot_segments (plot_id, x, z, radius) VALUES (?, ?, ?, ?)"
                        ).use { stmt ->
                            stmt.setInt(1, plotId)
                            stmt.setInt(2, target.x)
                            stmt.setInt(3, target.z)
                            stmt.setInt(4, target.radius)
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement(
                            "INSERT INTO plot_logs (plot_id, action, actor_uuid, timestamp) VALUES (?, ?, ?, ?)"
                        ).use { stmt ->
                            stmt.setInt(1, plotId)
                            stmt.setString(2, "EXPAND:${direction.name}:${target.x},${target.z},${target.radius}")
                            stmt.setString(3, actorUuid.toString())
                            stmt.setLong(4, System.currentTimeMillis())
                            stmt.executeUpdate()
                        }
                        conn.prepareStatement(
                            "UPDATE plots SET geometry_revision = geometry_revision + 1 WHERE plot_id = ?"
                        ).use {
                            it.setInt(1, plotId)
                            it.executeUpdate()
                        }
                        conn.commit()
                        ExpandResult.Success(target)
                    } catch (failure: SQLException) {
                        try {
                            conn.rollback()
                        } catch (_: SQLException) {
                        }
                        throw failure
                    }
                }
            } catch (failure: SQLException) {
                logger.err("Błąd podczas rozszerzania działki $plotId: ${failure.message}")
                ExpandResult.DatabaseError
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

    fun updatePlotFlag(plotId: Int, flagName: String, flagValue: Boolean): Boolean =
        plugin.protectionCoordinator.mutate({ plugin.cacheManager.reloadPlotSync(plotId) }) {
            performUpdatePlotFlag(plotId, flagName, flagValue)
        }

    private fun performUpdatePlotFlag(plotId: Int, flagName: String, flagValue: Boolean): Boolean = try {
        database().upsert(
            "plot_flags",
            linkedMapOf(
                "plot_id" to plotId,
                "flag_name" to flagName,
                "flag_value" to flagValue.toString(),
            ),
            conflictColumns = listOf("plot_id", "flag_name"),
            updateColumns = listOf("flag_value"),
        )
        true
    } catch (failure: Exception) {
        logger.err("Błąd przy aktualizacji flagi '$flagName' dla plot_id=$plotId: ${failure.message}")
        false
    }

    fun getPlotFlags(plotId: Int): List<PlotFlagData> = try {
        database().select("plot_flags")
            .columns("flag_name", "flag_value")
            .where { "plot_id" eq plotId }
            .fetch()
            .map { row ->
                PlotFlagData(
                    plotId,
                    requireNotNull(row.string("flag_name")),
                    requireNotNull(row.string("flag_value")),
                )
            }
    } catch (failure: Exception) {
        logger.err("Błąd podczas pobierania flag działki plot_id=$plotId: ${failure.message}")
        emptyList()
    }

    fun getPlotFlag(plotId: Int, flagName: String): PlotFlagData? = try {
        database().get("plot_flags") {
            "plot_id" eq plotId
            "flag_name" eq flagName
        }?.let { row -> PlotFlagData(plotId, flagName, requireNotNull(row.string("flag_value"))) }
    } catch (failure: Exception) {
        logger.err("Błąd podczas pobierania flagi '$flagName' dla plot_id=$plotId: ${failure.message}")
        null
    }

    fun addPlotMember(plotId: Int, memberUuid: UUID, role: String = "member"): Boolean =
        protectedMutation { performAddPlotMember(plotId, memberUuid, role) }

    private fun performAddPlotMember(plotId: Int, memberUuid: UUID, role: String = "member"): Boolean = try {
        database().upsert(
            "plot_members",
            linkedMapOf(
                "plot_id" to plotId,
                "member_uuid" to memberUuid.toString(),
                "role" to role,
            ),
            conflictColumns = listOf("plot_id", "member_uuid"),
            updateColumns = listOf("role"),
        )
        true
    } catch (failure: Exception) {
        logger.err("Błąd podczas dodawania członka do działki: ${failure.message}")
        false
    }

    fun removePlotMember(plotId: Int, memberUuid: UUID): Boolean =
        protectedMutation { performRemovePlotMember(plotId, memberUuid) }

    private fun performRemovePlotMember(plotId: Int, memberUuid: UUID): Boolean = try {
        database().delete("plot_members") {
            "plot_id" eq plotId
            "member_uuid" eq memberUuid.toString()
        } > 0
    } catch (failure: Exception) {
        logger.err("Błąd podczas usuwania członka działki: ${failure.message}")
        false
    }

    fun transferPlotOwnership(
        plotId: Int,
        expectedOwner: UUID,
        recipient: UUID,
        maxPlots: Int,
        maxRadius: Int,
        maxArea: Long,
        maxChunksPerPlot: Int = Int.MAX_VALUE,
        maxChunksOwned: Int = Int.MAX_VALUE,
    ): Boolean = protectedMutation {
        performTransferPlotOwnership(
            plotId, expectedOwner, recipient, maxPlots, maxRadius, maxArea, maxChunksPerPlot, maxChunksOwned,
        )
    }

    private fun performTransferPlotOwnership(
        plotId: Int,
        expectedOwner: UUID,
        recipient: UUID,
        maxPlots: Int,
        maxRadius: Int,
        maxArea: Long,
        maxChunksPerPlot: Int,
        maxChunksOwned: Int,
    ): Boolean {
        val plot = getPlotById(plotId) ?: return false
        return claimLocks.computeIfAbsent(plot.world.lowercase(Locale.ROOT)) { ReentrantLock() }.withLock {
            withJdbcConnection {
                OwnershipTransfer.transfer(
                    it, plotId, expectedOwner, recipient, maxPlots, maxRadius, maxArea,
                    maxChunksPerPlot, maxChunksOwned,
                )
            }
        }
    }

    fun updatePlotMemberRole(plotId: Int, memberUuid: UUID, newRole: String): Boolean =
        protectedMutation { performUpdatePlotMemberRole(plotId, memberUuid, newRole) }

    private fun performUpdatePlotMemberRole(plotId: Int, memberUuid: UUID, newRole: String): Boolean = try {
        database().update("plot_members", mapOf("role" to newRole)) {
            "plot_id" eq plotId
            "member_uuid" eq memberUuid.toString()
        } > 0
    } catch (failure: Exception) {
        logger.err("Błąd podczas aktualizacji roli członka działki: ${failure.message}")
        false
    }

    fun getPlotMembers(plotId: Int): List<PlotMemberData> = try {
        database().select("plot_members")
            .columns("member_uuid", "role")
            .where { "plot_id" eq plotId }
            .fetch()
            .map { row ->
                PlotMemberData(
                    plotId = plotId,
                    memberUuid = requireNotNull(row.string("member_uuid")),
                    memberRole = requireNotNull(row.string("role")),
                )
            }
    } catch (failure: Exception) {
        logger.err("Błąd podczas pobierania członków działki plot_id=$plotId: ${failure.message}")
        emptyList()
    }

    fun getPlotsFromAllUsers(): List<PlotData> = loadPlotCacheData().plots

    fun playerOwnsPlot(ownerUuid: UUID): Boolean = try {
        database().exists("plots") { "owner_uuid" eq ownerUuid.toString() }
    } catch (failure: Exception) {
        logger.err("Błąd podczas sprawdzania działek gracza: ${failure.message}")
        false
    }

    fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? =
        loadPlotCacheData().plots.firstOrNull { it.world.equals(world, true) && it.contains(x, z) }

    fun getPlotByName(name: String, ownerUuid: UUID): PlotData? =
        loadPlotCacheData().plots.firstOrNull { it.ownerUuid == ownerUuid && it.name.equals(name, true) }

    fun getPlayerPlots(uuid: UUID): List<PlotData> {
        val loaded = loadPlotCacheData()
        return loaded.plots.filter {
            it.ownerUuid == uuid || loaded.members[it.id].orEmpty().any { member -> member.memberUuid == uuid.toString() }
        }
    }

    fun getPlotsByOwner(owner: UUID): List<PlotData> = loadPlotCacheData().plots.filter { it.ownerUuid == owner }

    fun getPlotById(plotId: Int): PlotData? = loadPlotCacheData(plotId).plots.singleOrNull()

    fun doesPlotOverlap(x: Int, z: Int, radius: Int, world: String): Boolean {
        val candidate = pl.syntaxdevteam.plotsx.geometry.ClassicGeometry(PlotSegment(x, z, radius))
        return loadPlotCacheData().plots.any { it.world.equals(world, true) && it.geometry.intersects(candidate) }
    }

    fun logPlotAction(entry: PlotLogEntry) {
        try {
            database().insert(
                "plot_logs",
                linkedMapOf(
                    "plot_id" to entry.plotId,
                    "action" to entry.action,
                    "actor_uuid" to entry.actorUUID.toString(),
                    "timestamp" to entry.timestamp,
                ),
            )
        } catch (failure: Exception) {
            logger.err("Błąd podczas zapisywania logu działki ${entry.plotId}: ${failure.message}")
        }
    }

    fun exportDatabase(dialect: String = if (configuredDbType == "mariadb") "mariadb" else dbType): File {
        val target = SqlBackup.dialect(dialect)
        return withJdbcConnection { conn -> SqlBackup.export(conn, target, File(plugin.dataFolder, "dump")) }
    }

    fun importDatabase() = protectedMutation { performImportDatabase() }

    private fun performImportDatabase() {
        withJdbcConnection { conn ->
            SqlBackup.restore(
                conn,
                SqlBackup.dialect(dbType),
                File(plugin.dataFolder, "dump/backup.sql"),
                allowChunkPlots = true,
            )
            IdentityMigrationStore.migrateSchema(conn)
            plugin.identityAliasRegistry.replaceAll(IdentityMigrationStore.loadAliases(conn))
            if (OperationJournal.exists(conn)) reportInterruptedOperations(conn)
        }
        plugin.cacheManager.reloadAllCachesSync()
    }
}
