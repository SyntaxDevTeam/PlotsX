package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.databases.Helpers
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

class ExpandGUI(private val plugin: PlotsX, private val plotId: Int) : AbstractGUI(
    plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "expand.title"), 27
) {
    private val confirmIndex = 11
    private val cancelIndex = 15

    override fun open(player: Player) {
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: run {
            player.sendMessage(error("plot_not_found")); return
        }
        val target = targetRadius(plot)
        val limits = plugin.hookHandler.getPlotLimits(player)
        val currentTotal = plugin.databaseHandler.getPlotsByOwner(plot.ownerUuid).sumOf { area(it.radius) }
        val targetTotal = currentTotal - area(plot.radius) + area(target)
        inventory.setItem(confirmIndex, item(Material.EMERALD_BLOCK,
            text("expand.confirm"), listOf(
                text("expand.radius", mapOf("current" to plot.radius.toString(), "target" to target.toString())),
                text("expand.area", mapOf("used" to targetTotal.toString(), "max" to limit(limits.maxTotalArea))),
                text("expand.max_radius", mapOf("max" to limit(limits.maxRadius.toLong())))
            )))
        inventory.setItem(cancelIndex, item(Material.BARRIER, text("expand.cancel"), emptyList()))
        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.slot !in setOf(confirmIndex, cancelIndex)) return
        player.closeInventory()
        plugin.guiHandler.unregisterGui(player)
        if (event.slot == cancelIndex) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "expand_cancelled"))
        } else expand(player)
    }

    private fun expand(player: Player) {
        if (!PermissionChecker.canExpandPlot(player)) {
            player.sendMessage(error("no_permission")); return
        }
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: run {
            player.sendMessage(error("plot_not_found")); return
        }
        val ownerUuid = plugin.uuidManager.getUUID(player.name)
        if (plot.ownerUuid != ownerUuid) {
            player.sendMessage(error("not_owner")); return
        }
        val target = targetRadius(plot)
        if (target <= plot.radius) {
            player.sendMessage(error("expand_radius_limit")); return
        }
        val limits = plugin.hookHandler.getPlotLimits(player)
        val world = plugin.server.getWorld(plot.world) ?: run {
            player.sendMessage(error("expand_failed")); return
        }
        if (plugin.regionProtectionHook?.overlapsProtectedRegion(world, plot.x, plot.z, target) == true) {
            player.sendMessage(error("worldguard_collision")); return
        }
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = plugin.databaseHandler.expandPlotAtomically(
                plot.id, ownerUuid, player.uniqueId, target, limits.maxRadius, limits.maxTotalArea
            )
            plugin.server.scheduler.runTask(plugin, Runnable { showResult(player, plot, result) })
        })
    }

    private fun showResult(player: Player, oldPlot: PlotData, result: DatabaseHandler.ExpandResult) {
        val errorKey = when (result) {
            is DatabaseHandler.ExpandResult.Success -> {
                plugin.cacheManager.updatePlotCacheAsync(oldPlot.id)
                player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                    "plots", "expand_success", mapOf("radius" to result.newRadius.toString())
                ))
                Helpers(plugin).visualizePlotBorder3D(player, oldPlot.x, oldPlot.z, result.newRadius, 20, 2, 4)
                return
            }
            DatabaseHandler.ExpandResult.PlotNotFound -> "plot_not_found"
            DatabaseHandler.ExpandResult.NotOwner -> "not_owner"
            DatabaseHandler.ExpandResult.RadiusLimitReached -> "expand_radius_limit"
            DatabaseHandler.ExpandResult.AreaLimitReached -> "expand_area_limit"
            DatabaseHandler.ExpandResult.Overlap -> "expand_collision"
            DatabaseHandler.ExpandResult.DatabaseError -> "expand_failed"
        }
        player.sendMessage(error(errorKey))
    }

    private fun targetRadius(plot: PlotData): Int =
        (plot.radius.toLong() + plugin.config.getInt("plots.expansion.step", 8).coerceAtLeast(1))
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun area(radius: Int): Long = (radius.toLong() * 2L + 1L).let { it * it }
    private fun limit(value: Long) = if (value == Long.MAX_VALUE || value == Int.MAX_VALUE.toLong()) "∞" else value.toString()
    private fun error(key: String) = plugin.messageHandler.stringMessageToComponent("error", key)
    private fun text(key: String, replacements: Map<String, String> = emptyMap()) =
        plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", key, replacements)
    private fun item(material: Material, name: Component, lore: List<Component>) = ItemStack(material).apply {
        itemMeta = itemMeta.apply { displayName(name); lore(lore) }
    }
}
