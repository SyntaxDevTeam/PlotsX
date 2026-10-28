package pl.syntaxdevteam.plotsx.api.internal

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.api.*
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import pl.syntaxdevteam.plotsx.protection.FlagMeta
import pl.syntaxdevteam.plotsx.protection.FlagType
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry
import java.util.Collections
import java.util.Locale
import java.util.UUID

internal class DefaultPlotsXApi(private val plugin: PlotsX) : PlotsXApi, Listener {
    @Volatile private var active = true
    override val apiVersion = 2
    private val access = PlotAccess(plugin)
    private val asyncTasks = AsyncApiTasks(report = { plugin.logger.err("Async API operation failed: ${it.message}") }) { plugin.schedulerAdapter.runAsync(it) }
    private val providers = java.util.concurrent.ConcurrentHashMap<String, Plugin>()
    private fun ready() = check(active) { "PlotsX API is disabled; obtain a new provider from ServicesManager" }
    private fun serverThread() { ready(); check(Bukkit.isPrimaryThread()) { "This API operation requires the Paper server thread" } }
    private fun PlotData.snapshot() = PlotSnapshot(id, ownerUuid, world, x, y, z, radius, name, creationTime,
        immutable(extensions.map { PlotRegionSnapshot(it.x, it.z, it.radius) }),
        immutable(chunks.map { ChunkSnapshot(it.x, it.z) }), geometryRevision)
    private fun <T> immutable(values: Collection<T>): List<T> = Collections.unmodifiableList(values.toList())

    override fun getPlot(id: Int): PlotSnapshot? { ready(); return plugin.cacheManager.getPlot(id)?.snapshot() }
    override fun getPlotAt(world: String, blockX: Int, blockZ: Int): PlotSnapshot? {
        ready()
        return plugin.cacheManager.getPlotAt(world, blockX, blockZ)?.snapshot()
    }
    override fun getPlots(): List<PlotSnapshot> { ready(); return immutable(plugin.cacheManager.getCachedPlots().map { it.snapshot() }) }
    override fun getOwnedPlotProfiles(owner: UUID): List<PlotProfileSnapshot> {
        ready()
        return immutable(plugin.cacheManager.getCachedPlots().filter { it.ownerUuid == owner }.map {
            PlotProfileSnapshot(it.snapshot(), it.teleportSpawn?.let { spawn -> PlotTeleportSnapshot(spawn.x, spawn.y, spawn.z) })
        })
    }
    override fun getOwnedPlots(owner: UUID): List<PlotSnapshot> {
        ready()
        return immutable(plugin.cacheManager.getCachedPlots().filter { it.ownerUuid == owner }.map { it.snapshot() })
    }
    override fun getAccessiblePlots(player: UUID): List<PlotSnapshot> {
        ready()
        return immutable(plugin.cacheManager.getPlayerPlots(player).map { it.snapshot() })
    }
    override fun getMembers(plotId: Int): List<MemberSnapshot> {
        ready()
        return immutable(plugin.cacheManager.getMembers(plotId).orEmpty().map { MemberSnapshot(UUID.fromString(it.memberUuid), it.memberRole) })
    }
    override fun isMember(plotId: Int, player: UUID): Boolean { ready(); return plugin.cacheManager.isMember(plotId, player) }
    override fun getFlags(): List<FlagDefinition> {
        ready()
        return immutable(PlotFlagRegistry.allFlags.values.map {
            FlagDefinition(it.name, it.defaultValue, it.type == FlagType.WHITELIST, it.memberBypass, it.material,
                it.customDisplayName ?: it.displayKey, it.customDescription ?: it.descriptionKey)
        })
    }
    override fun getFlagValue(plotId: Int, flag: String): Boolean? {
        ready()
        if (plugin.cacheManager.getPlot(plotId) == null) return null
        val definition = PlotFlagRegistry.allFlags[flag] ?: return null
        return plugin.cacheManager.getFlagValue(plotId, flag)
            ?: definition.defaultValue
    }
    override fun evaluateFlag(world: String, blockX: Int, blockZ: Int, flag: String, subject: UUID?): FlagDecision {
        ready()
        val definition = PlotFlagRegistry.allFlags[flag] ?: return FlagDecision.UNKNOWN_FLAG
        return plugin.protectionCoordinator.decision({ FlagDecision.DENY }) {
        val snapshot = plugin.cacheManager.readSnapshot()
        val plot = snapshot.at(world, blockX, blockZ) ?: return@decision FlagDecision.ALLOW
        val value = snapshot.flagValue(plot.id, flag)
            ?: definition.defaultValue
        val member = subject != null && (plot.ownerUuid == subject ||
            snapshot.member(plot.id, subject) != null)
        if (FlagRules.allowed(value, definition.type == FlagType.WHITELIST, definition.memberBypass, member))
            FlagDecision.ALLOW else FlagDecision.DENY
        }
    }

    private fun actorThread(actor: CommandSender) {
        ready()
        if (actor is Player) check(Bukkit.isOwnedByCurrentRegion(actor)) { "This API operation requires the actor entity thread" }
        else serverThread()
    }

    override fun canManage(actor: CommandSender, plotId: Int, action: String): Boolean {
        actorThread(actor)
        if (action !in access.actions) return false
        return plugin.cacheManager.withDecisionSnapshot {
            val plot = plugin.cacheManager.getPlot(plotId) ?: return@withDecisionSnapshot false
            access.allowed(actor, plot, action)
        }
    }
    override fun getRolePermissions(plotId: Int, role: String): Set<String> {
        ready()
        return plugin.cacheManager.withDecisionSnapshot {
            if (plugin.cacheManager.getPlot(plotId) == null) emptySet()
            else Collections.unmodifiableSet(access.grants(plotId, role))
        }
    }
    override fun getRoles(): List<String> { ready(); return immutable(access.roles) }
    override fun addMember(actor: CommandSender, plotId: Int, player: UUID) = changeMember(actor, plotId, player, "add")
    override fun removeMember(actor: CommandSender, plotId: Int, player: UUID) = changeMember(actor, plotId, player, "remove")
    override fun setMemberRole(actor: CommandSender, plotId: Int, player: UUID, role: String) = changeMember(actor, plotId, player, "role", role)
    override fun transferOwnership(actor: CommandSender, plotId: Int, recipient: UUID) = changeMember(actor, plotId, recipient, "transfer")

    override fun transferOwnershipAsync(actor: CommandSender, plotId: Int, recipient: UUID): java.util.concurrent.CompletableFuture<MemberUpdateResult> {
        actorThread(actor)
        return plugin.ownershipTransfers.transfer(actor, plotId, recipient)
    }

    private fun changeMember(actor: CommandSender, plotId: Int, target: UUID, action: String, role: String = "member"): MemberUpdateResult {
        actorThread(actor)
        try {
            val plot = plugin.cacheManager.getPlot(plotId) ?: return MemberUpdateResult.PLOT_NOT_FOUND
            val permitted = when (action) {
                "add" -> access.allowed(actor, plot, "invite")
                "remove" -> access.allowed(actor, plot, "kick")
                else -> access.owner(actor, plot)
            }
            if (!permitted) return MemberUpdateResult.DENIED
            if (target == plot.ownerUuid) return MemberUpdateResult.OWNER
            if (role !in access.roles) return MemberUpdateResult.UNKNOWN_ROLE
            val members = plugin.cacheManager.getMembers(plotId).orEmpty()
            val existing = members.firstOrNull { it.memberUuid == target.toString() }
            if (action == "add" && existing != null) return MemberUpdateResult.ALREADY_MEMBER
            if (action != "add" && existing == null) return MemberUpdateResult.NOT_MEMBER
            if (action == "remove" && !access.owner(actor, plot)) {
                val actorRole = members.firstOrNull { it.memberUuid == (actor as Player).uniqueId.toString() }?.memberRole
                if (!pl.syntaxdevteam.plotsx.commands.RoleHierarchy.canRemove(actorRole, existing?.memberRole)) return MemberUpdateResult.DENIED
            }
            val success = when (action) {
                "add" -> plugin.databaseHandler.addPlotMember(plotId, target)
                "remove" -> plugin.databaseHandler.removePlotMember(plotId, target)
                "role" -> plugin.databaseHandler.updatePlotMemberRole(plotId, target, role)
                else -> {
                    val recipient = plugin.server.getPlayer(target) ?: return MemberUpdateResult.RECIPIENT_OFFLINE
                    if (plugin.schedulerAdapter.isFoliaBased() && !Bukkit.isOwnedByCurrentRegion(recipient))
                        return MemberUpdateResult.RECIPIENT_WRONG_THREAD
                    val limits = plugin.hookHandler.getPlotLimits(recipient)
                    plugin.databaseHandler.transferPlotOwnership(plotId, plot.ownerUuid, target,
                        plugin.hookHandler.getMaxPlots(recipient), limits.maxRadius,
                        if (plot.radius == null) plugin.hookHandler.getChunkMaxTotalArea(recipient) else limits.maxTotalArea,
                        plugin.hookHandler.getMaxChunksPerPlot(recipient), plugin.hookHandler.getMaxOwnedChunks(recipient))
                }
            }
            if (!success) return if (action == "transfer") MemberUpdateResult.TRANSFER_REJECTED else MemberUpdateResult.DATABASE_ERROR
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "Member:$action:$target:$role",
                (actor as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
            return MemberUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API membership update failed: ${ex.message}")
            return MemberUpdateResult.DATABASE_ERROR
        }
    }

    override fun setRolePermission(actor: CommandSender, plotId: Int, role: String, action: String, allowed: Boolean): MemberUpdateResult {
        actorThread(actor)
        try {
            val plot = plugin.cacheManager.getPlot(plotId) ?: return MemberUpdateResult.PLOT_NOT_FOUND
            if (!access.owner(actor, plot)) return MemberUpdateResult.DENIED
            if (role !in access.roles) return MemberUpdateResult.UNKNOWN_ROLE
            if (action !in access.actions) return MemberUpdateResult.UNKNOWN_ACTION
            if (!plugin.databaseHandler.updatePlotFlag(plotId, access.key(role, action), allowed)) return MemberUpdateResult.DATABASE_ERROR
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "RolePermission:$role:$action:$allowed",
                (actor as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
            return MemberUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API role update failed: ${ex.message}")
            return MemberUpdateResult.DATABASE_ERROR
        }
    }
    override fun setFlag(actor: CommandSender, plotId: Int, flag: String, value: Boolean): FlagUpdateResult {
        actorThread(actor)
        val definition = PlotFlagRegistry.allFlags[flag] ?: return FlagUpdateResult.UNKNOWN_FLAG
        val plot = plugin.cacheManager.getPlot(plotId) ?: return FlagUpdateResult.PLOT_NOT_FOUND
        if (!access.allowed(actor, plot, "flag.$flag")) return FlagUpdateResult.DENIED
        try {
            val previous = plugin.cacheManager.getFlagValue(plotId, flag) ?: definition.defaultValue
            if (previous == value) return FlagUpdateResult.UNCHANGED
            if (!plugin.databaseHandler.updatePlotFlag(plotId, flag, value)) return FlagUpdateResult.DATABASE_ERROR
            val actorId = (actor as? Player)?.uniqueId
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "UpdateFlag:$flag:$value", actorId ?: UUID(0, 0), System.currentTimeMillis()))
            plugin.server.pluginManager.callEvent(PlotFlagChangedEvent(plot.snapshot(), flag, previous, value, actorId))
            return FlagUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API flag update failed: ${ex.message}")
            return FlagUpdateResult.DATABASE_ERROR
        }
    }
    /** Capture Bukkit permissions/configuration on the caller's entity thread. Worker checks use current cache data. */
    private fun actorPolicy(actor: CommandSender): ApiActorPolicy {
        actorThread(actor)
        return ApiActorPolicy((actor as? Player)?.uniqueId, access.admin(actor), access.roles.toSet(), access.actions.toSet(),
            access.roles.associateWith { role ->
                val path = "plot-roles.$role.permissions"
                if (plugin.config.contains(path)) plugin.config.getStringList(path).toSet()
                else if (role == "manager") setOf("invite", "kick", "rename") else emptySet()
            })
    }

    private class WriteScope(val actor: ApiActorPolicy) {
        var dirty = false
        var event: PlotFlagChangedEvent? = null
        fun write(action: () -> Boolean): Boolean { dirty = true; return action() }
    }

    private fun <T> submit(actor: CommandSender, plotId: Int, failure: T, action: (WriteScope) -> T): java.util.concurrent.CompletableFuture<T> {
        val policy = actorPolicy(actor)
        return asyncTasks.submit(failure) {
            ready()
            val scope = WriteScope(policy)
            val result = plugin.protectionCoordinator.mutate({
                if (scope.dirty) plugin.cacheManager.reloadPlotSync(plotId)
            }) { action(scope) }
            scope.event?.let { event ->
                // This is a normal synchronous Bukkit event; dispatch after cache publication on the actor scheduler.
                runCatching {
                    plugin.schedulerAdapter.runForSender(actor, Runnable {
                        if (active && plugin.isEnabled) plugin.server.pluginManager.callEvent(event)
                    })
                }.onFailure { plugin.logger.err("API flag-change notification could not be scheduled: ${it.message}") }
            }
            result
        }
    }

    override fun addMemberAsync(actor: CommandSender, plotId: Int, player: UUID) = changeMemberAsync(actor, plotId, player, "add")
    override fun removeMemberAsync(actor: CommandSender, plotId: Int, player: UUID) = changeMemberAsync(actor, plotId, player, "remove")
    override fun setMemberRoleAsync(actor: CommandSender, plotId: Int, player: UUID, role: String) = changeMemberAsync(actor, plotId, player, "role", role)

    private fun changeMemberAsync(actor: CommandSender, plotId: Int, target: UUID, action: String, role: String = "member") =
        submit(actor, plotId, MemberUpdateResult.DATABASE_ERROR) { scope ->
            val snapshot = plugin.cacheManager.readSnapshot()
            val plot = snapshot.plots[plotId] ?: return@submit MemberUpdateResult.PLOT_NOT_FOUND
            val owner = scope.actor.owner(plot)
            val permitted = when (action) {
                "add" -> scope.actor.allowed(snapshot, plot, "invite")
                "remove" -> scope.actor.allowed(snapshot, plot, "kick")
                else -> owner
            }
            if (!permitted) return@submit MemberUpdateResult.DENIED
            if (target == plot.ownerUuid) return@submit MemberUpdateResult.OWNER
            if (role !in scope.actor.roles) return@submit MemberUpdateResult.UNKNOWN_ROLE
            val existing = snapshot.member(plotId, target)
            if (action == "add" && existing != null) return@submit MemberUpdateResult.ALREADY_MEMBER
            if (action != "add" && existing == null) return@submit MemberUpdateResult.NOT_MEMBER
            if (action == "remove" && !owner && !pl.syntaxdevteam.plotsx.commands.RoleHierarchy.canRemove(
                    scope.actor.id?.let { snapshot.member(plotId, it)?.memberRole }, existing?.memberRole))
                return@submit MemberUpdateResult.DENIED
            val success = scope.write {
                when (action) {
                    "add" -> plugin.databaseHandler.addPlotMember(plotId, target)
                    "remove" -> plugin.databaseHandler.removePlotMember(plotId, target)
                    else -> plugin.databaseHandler.updatePlotMemberRole(plotId, target, role)
                }
            }
            if (!success) return@submit MemberUpdateResult.DATABASE_ERROR
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "Member:$action:$target:$role",
                scope.actor.id ?: UUID(0, 0), System.currentTimeMillis()))
            MemberUpdateResult.UPDATED
        }

    override fun setRolePermissionAsync(actor: CommandSender, plotId: Int, role: String, action: String, allowed: Boolean) =
        submit(actor, plotId, MemberUpdateResult.DATABASE_ERROR) { scope ->
            val plot = plugin.cacheManager.getPlot(plotId) ?: return@submit MemberUpdateResult.PLOT_NOT_FOUND
            if (!scope.actor.owner(plot)) return@submit MemberUpdateResult.DENIED
            if (role !in scope.actor.roles) return@submit MemberUpdateResult.UNKNOWN_ROLE
            if (action !in scope.actor.actions) return@submit MemberUpdateResult.UNKNOWN_ACTION
            if (!scope.write { plugin.databaseHandler.updatePlotFlag(plotId, access.key(role, action), allowed) })
                return@submit MemberUpdateResult.DATABASE_ERROR
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "RolePermission:$role:$action:$allowed",
                scope.actor.id ?: UUID(0, 0), System.currentTimeMillis()))
            MemberUpdateResult.UPDATED
        }

    override fun setFlagAsync(actor: CommandSender, plotId: Int, flag: String, value: Boolean): java.util.concurrent.CompletableFuture<FlagUpdateResult> {
        actorThread(actor)
        val definition = PlotFlagRegistry.allFlags[flag]
            ?: return java.util.concurrent.CompletableFuture.completedFuture(FlagUpdateResult.UNKNOWN_FLAG)
        return submit(actor, plotId, FlagUpdateResult.DATABASE_ERROR) { scope ->
            val snapshot = plugin.cacheManager.readSnapshot()
            val plot = snapshot.plots[plotId] ?: return@submit FlagUpdateResult.PLOT_NOT_FOUND
            if (!scope.actor.allowed(snapshot, plot, "flag.$flag")) return@submit FlagUpdateResult.DENIED
            val previous = snapshot.flagValue(plotId, flag) ?: definition.defaultValue
            if (previous == value) return@submit FlagUpdateResult.UNCHANGED
            if (!scope.write { plugin.databaseHandler.updatePlotFlag(plotId, flag, value) }) return@submit FlagUpdateResult.DATABASE_ERROR
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "UpdateFlag:$flag:$value", scope.actor.id ?: UUID(0, 0), System.currentTimeMillis()))
            scope.event = PlotFlagChangedEvent(plot.snapshot(), flag, previous, value, scope.actor.id)
            FlagUpdateResult.UPDATED
        }
    }

    override fun registerFlag(provider: Plugin, definition: FlagDefinition): Boolean {
        serverThread()
        require(provider.isEnabled) { "Flag provider must be enabled" }
        val namespace = provider.name.lowercase(Locale.ROOT)
        require(FlagRules.validKey(namespace, definition.key)) { "Use providername:key (lowercase), at most 120 characters" }
        val meta = FlagMeta(definition.key, definition.defaultValue,
            if (definition.enabledMeansAllowed) FlagType.WHITELIST else FlagType.BLACKLIST,
            definition.material, "", "", definition.memberBypass, definition.displayName, definition.description)
        if (!PlotFlagRegistry.register(meta)) return false
        providers[definition.key] = provider
        return true
    }
    override fun unregisterFlag(provider: Plugin, key: String): Boolean {
        serverThread()
        if (providers[key] !== provider) return false
        providers.remove(key)
        return PlotFlagRegistry.unregister(key)
    }
    @EventHandler fun onPluginDisable(event: PluginDisableEvent) {
        providers.filterValues { it === event.plugin }.keys.toList().forEach {
            providers.remove(it); PlotFlagRegistry.unregister(it)
        }
    }
    fun close() {
        active = false
        asyncTasks.close()
        providers.keys.toList().forEach { PlotFlagRegistry.unregister(it) }
        providers.clear()
    }
}

internal object FlagRules {
    fun allowed(value: Boolean, enabledMeansAllowed: Boolean, memberBypass: Boolean, member: Boolean) =
        (memberBypass && member) || (value == enabledMeansAllowed)
    fun validKey(namespace: String, key: String) = key.length <= 120 &&
        key.startsWith("$namespace:") && Regex("[a-z0-9_-]+:[a-z0-9_-]+").matches(key)
}
