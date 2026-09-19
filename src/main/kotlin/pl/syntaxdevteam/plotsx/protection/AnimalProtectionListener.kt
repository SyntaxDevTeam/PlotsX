package pl.syntaxdevteam.plotsx.protection

import io.papermc.paper.event.entity.EntityFertilizeEggEvent
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.player.PlayerUnleashEntityEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

/**
 * Fine-grained animal protection introduced for Alpha-02.
 *
 * Paper exposes independent cancellable events for leashing, mounting and breeding,
 * so these actions no longer need to share the broad `animal-interact` flag.
 */
internal class AnimalProtectionListener(private val plugin: PlotsX) : Listener {
    private val passiveMobs: Set<EntityType> by lazy { PlotCompat.loadPassiveMobs() }
    private val message = plugin.messageHandler

    private fun plotAt(entity: Entity): PlotData? {
        val location = entity.location
        return plugin.cacheManager.getCachedPlots().firstOrNull { plot ->
            plot.world.equals(location.world.name, ignoreCase = true) &&
                plot.contains(location.blockX, location.blockZ)
        }
    }

    private fun hasPlotPermission(player: Player, plot: PlotData, flag: String): Boolean {
        if (PermissionChecker.canBypassPlots(player)) return true
        if (player.uniqueId == plot.ownerUuid) return true

        val isMember = plugin.cacheManager.getMembers(plot.id)
            ?.any { it.memberUuid == player.uniqueId.toString() } ?: false
        if (isMember) return true

        val definition = PlotFlagRegistry.allFlags[flag] ?: return false
        val stored = plugin.cacheManager.getFlags(plot.id)
            ?.firstOrNull { it.name == flag }
            ?.value
            ?.toBooleanStrictOrNull()
            ?: definition.defaultValue

        return when (definition.type) {
            FlagType.WHITELIST -> stored
            FlagType.BLACKLIST -> !stored
        }
    }

    private fun deny(player: Player) {
        // Keep the already translated legacy denial until dedicated PL/EN message keys land.
        player.sendMessage(message.stringMessageToComponent("flags", "animal-interact.not_allowed"))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerLeashEntity(event: PlayerLeashEntityEvent) {
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(event.player, plot, "animal-leash")) {
            event.isCancelled = true
            deny(event.player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerUnleashEntity(event: PlayerUnleashEntityEvent) {
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(event.player, plot, "animal-leash")) {
            event.isCancelled = true
            deny(event.player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityMount(event: EntityMountEvent) {
        val player = event.entity as? Player ?: return
        if (event.mount.type !in passiveMobs) return
        val plot = plotAt(event.mount) ?: return
        if (!hasPlotPermission(player, plot, "animal-ride")) {
            event.isCancelled = true
            deny(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityBreed(event: EntityBreedEvent) {
        val player = event.breeder as? Player ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "animal-breed")) {
            event.isCancelled = true
            deny(player)
        }
    }

    /**
     * Paper uses fertilization instead of EntityBreedEvent for animals whose offspring
     * is produced later (for example frogs, sniffers and turtles).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityFertilizeEgg(event: EntityFertilizeEggEvent) {
        val player = event.breeder ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "animal-breed")) {
            event.isCancelled = true
            deny(player)
        }
    }
}
