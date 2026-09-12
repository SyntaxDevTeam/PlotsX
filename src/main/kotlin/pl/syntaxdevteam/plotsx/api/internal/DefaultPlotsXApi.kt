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
    override val apiVersion = 1
    private val access = PlotAccess(plugin)
    private val providers = mutableMapOf<String, Plugin>()
    private fun ready() = check(active) { "PlotsX API is disabled; obtain a new provider from ServicesManager" }
    private fun serverThread() { ready(); check(Bukkit.isPrimaryThread()) { "This API operation requires the Paper server thread" } }
    private fun PlotData.snapshot() = PlotSnapshot(id, ownerUuid, world, x, y, z, radius, name, creationTime,
        immutable(extensions.map { PlotRegionSnapshot(it.x, it.z, it.radius) }))
    private fun <T> immutable(values: Collection<T>): List<T> = Collections.unmodifiableList(values.toList())

    override fun getPlot(id: Int): PlotSnapshot? { ready(); return plugin.cacheManager.getPlot(id)?.snapshot() }
    override fun getPlotAt(world: String, blockX: Int, blockZ: Int): PlotSnapshot? {
        ready()
        return plugin.cacheManager.getCachedPlots().asSequence().map { it.snapshot() }
            .firstOrNull { it.contains(world, blockX, blockZ) }
    }
    override fun getPlots(): List<PlotSnapshot> { ready(); return immutable(plugin.cacheManager.getCachedPlots().map { it.snapshot() }) }
    override fun getOwnedPlots(owner: UUID) = immutable(getPlots().filter { it.owner == owner })
    override fun getAccessiblePlots(player: UUID) = immutable(getPlots().filter { it.owner == player || isMember(it.id, player) })
    override fun getMembers(plotId: Int): List<MemberSnapshot> {
        ready()
        return immutable(plugin.cacheManager.getMembers(plotId).orEmpty().map { MemberSnapshot(UUID.fromString(it.memberUuid), it.memberRole) })
    }
    override fun isMember(plotId: Int, player: UUID) = getMembers(plotId).any { it.player == player }
    override fun getFlags(): List<FlagDefinition> {
        ready()
        return immutable(PlotFlagRegistry.allFlags.values.map {
            FlagDefinition(it.name, it.defaultValue, it.type == FlagType.WHITELIST, it.memberBypass, it.material,
                it.customDisplayName ?: it.displayKey, it.customDescription ?: it.descriptionKey)
        })
    }
    override fun getFlagValue(plotId: Int, flag: String): Boolean? {
        ready()
        if (getPlot(plotId) == null) return null
        val definition = PlotFlagRegistry.allFlags[flag] ?: return null
        return plugin.cacheManager.getFlags(plotId)?.firstOrNull { it.name == flag }?.value?.toBooleanStrictOrNull()
            ?: definition.defaultValue
    }
    override fun evaluateFlag(world: String, blockX: Int, blockZ: Int, flag: String, subject: UUID?): FlagDecision {
        ready()
        val definition = PlotFlagRegistry.allFlags[flag] ?: return FlagDecision.UNKNOWN_FLAG
        val plot = getPlotAt(world, blockX, blockZ) ?: return FlagDecision.ALLOW
        val value = getFlagValue(plot.id, flag) ?: return FlagDecision.DENY
        val member = subject != null && (plot.owner == subject || isMember(plot.id, subject))
        return if (FlagRules.allowed(value, definition.type == FlagType.WHITELIST, definition.memberBypass, member))
            FlagDecision.ALLOW else FlagDecision.DENY
    }
    override fun canManage(actor: CommandSender, plotId: Int, action: String): Boolean {
        serverThread()
        if (action !in access.actions) return false
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: return false
        return access.allowed(actor, plot, action)
    }
    override fun getRolePermissions(plotId: Int, role: String): Set<String> {
        serverThread()
        if (plugin.databaseHandler.getPlotById(plotId) == null) return emptySet()
        return Collections.unmodifiableSet(access.grants(plotId, role))
    }
    override fun getRoles(): List<String> { ready(); return immutable(access.roles) }
    override fun addMember(actor: CommandSender, plotId: Int, player: UUID) = changeMember(actor, plotId, player, "add")
    override fun removeMember(actor: CommandSender, plotId: Int, player: UUID) = changeMember(actor, plotId, player, "remove")
    override fun setMemberRole(actor: CommandSender, plotId: Int, player: UUID, role: String) = changeMember(actor, plotId, player, "role", role)
    override fun transferOwnership(actor: CommandSender, plotId: Int, recipient: UUID) = changeMember(actor, plotId, recipient, "transfer")

    private fun changeMember(actor: CommandSender, plotId: Int, target: UUID, action: String, role: String = "member"): MemberUpdateResult {
        serverThread()
        try {
            val plot = plugin.databaseHandler.getPlotById(plotId) ?: return MemberUpdateResult.PLOT_NOT_FOUND
            val permitted = when (action) {
                "add" -> access.allowed(actor, plot, "invite")
                "remove" -> access.allowed(actor, plot, "kick")
                else -> access.owner(actor, plot)
            }
            if (!permitted) return MemberUpdateResult.DENIED
            if (target == plot.ownerUuid) return MemberUpdateResult.OWNER
            if (role !in access.roles) return MemberUpdateResult.UNKNOWN_ROLE
            val members = plugin.databaseHandler.getPlotMembers(plotId)
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
                    val limits = plugin.hookHandler.getPlotLimits(recipient)
                    plugin.databaseHandler.transferPlotOwnership(plotId, plot.ownerUuid, target,
                        plugin.hookHandler.getMaxPlots(recipient), limits.maxRadius, limits.maxTotalArea)
                }
            }
            if (!success) return if (action == "transfer") MemberUpdateResult.TRANSFER_REJECTED else MemberUpdateResult.DATABASE_ERROR
            plugin.cacheManager.reloadMembersSync(plotId)
            plugin.cacheManager.reloadPlotSync(plotId)
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "Member:$action:$target:$role",
                (actor as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
            return MemberUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API membership update failed: ${ex.message}")
            return MemberUpdateResult.DATABASE_ERROR
        }
    }

    override fun setRolePermission(actor: CommandSender, plotId: Int, role: String, action: String, allowed: Boolean): MemberUpdateResult {
        serverThread()
        try {
            val plot = plugin.databaseHandler.getPlotById(plotId) ?: return MemberUpdateResult.PLOT_NOT_FOUND
            if (!access.owner(actor, plot)) return MemberUpdateResult.DENIED
            if (role !in access.roles) return MemberUpdateResult.UNKNOWN_ROLE
            if (action !in access.actions) return MemberUpdateResult.UNKNOWN_ACTION
            if (!plugin.databaseHandler.updatePlotFlag(plotId, access.key(role, action), allowed)) return MemberUpdateResult.DATABASE_ERROR
            plugin.cacheManager.reloadFlagsSync(plotId)
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "RolePermission:$role:$action:$allowed",
                (actor as? Player)?.uniqueId ?: UUID(0, 0), System.currentTimeMillis()))
            return MemberUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API role update failed: ${ex.message}")
            return MemberUpdateResult.DATABASE_ERROR
        }
    }
    override fun setFlag(actor: CommandSender, plotId: Int, flag: String, value: Boolean): FlagUpdateResult {
        serverThread()
        val definition = PlotFlagRegistry.allFlags[flag] ?: return FlagUpdateResult.UNKNOWN_FLAG
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: return FlagUpdateResult.PLOT_NOT_FOUND
        if (!access.allowed(actor, plot, "flag.$flag")) return FlagUpdateResult.DENIED
        try {
            val previous = plugin.databaseHandler.getPlotFlag(plotId, flag)?.value?.toBooleanStrictOrNull() ?: definition.defaultValue
            if (previous == value) return FlagUpdateResult.UNCHANGED
            if (!plugin.databaseHandler.updatePlotFlag(plotId, flag, value)) return FlagUpdateResult.DATABASE_ERROR
            plugin.cacheManager.reloadFlagsSync(plotId)
            val actorId = (actor as? Player)?.uniqueId
            plugin.databaseHandler.logPlotAction(PlotLogEntry(plotId, "UpdateFlag:$flag:$value", actorId ?: UUID(0, 0), System.currentTimeMillis()))
            plugin.server.pluginManager.callEvent(PlotFlagChangedEvent(plot.snapshot(), flag, previous, value, actorId))
            return FlagUpdateResult.UPDATED
        } catch (ex: java.sql.SQLException) {
            plugin.logger.err("API flag update failed: ${ex.message}")
            return FlagUpdateResult.DATABASE_ERROR
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
