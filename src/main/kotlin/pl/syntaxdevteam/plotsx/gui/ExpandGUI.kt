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
import pl.syntaxdevteam.plotsx.databases.PlotSegment
import pl.syntaxdevteam.plotsx.databases.ExpansionDirection
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy
import java.math.BigDecimal

class ExpandGUI(private val plugin: PlotsX, private val plotId: Int) : AbstractGUI(
    plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "expand.title"), 27
) {
    private val bordersIndex = 13
    private var quotedSource: PlotSegment? = null
    private val helpers = Helpers(plugin)
    private val confirmIndex = 20
    private val cancelIndex = 24
    private var quotedPrice: BigDecimal? = null
    private var quotedSegment: PlotSegment? = null
    private var direction = ExpansionDirection.NORTH
    private val directions = mapOf(4 to ExpansionDirection.NORTH, 14 to ExpansionDirection.EAST, 22 to ExpansionDirection.SOUTH, 12 to ExpansionDirection.WEST)
    private var submitted = false

    override fun open(player: Player) {
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: run {
            player.sendMessage(error("plot_not_found")); return
        }
        val source = if (player.world.name == plot.world)
            plot.segmentAt(player.location.blockX, player.location.blockZ) else null
        val target = source?.let { plot.expansion(direction, it) }
        quotedPrice = ExpansionEconomy.price(plugin, plot.extensions.size)
        quotedSegment = target
        quotedSource = source
        val limits = plugin.hookHandler.getPlotLimits(player)
        val currentTotal = plugin.databaseHandler.getPlotsByOwner(plot.ownerUuid).sumOf { it.area }
        val targetTotal = if (target == null || Long.MAX_VALUE - currentTotal < target.area) Long.MAX_VALUE else currentTotal + target.area
        val summary = listOf(
            text("expand.directions.${direction.name.lowercase()}"),
            if (source == null) text("expand.stand_inside") else text("expand.source", mapOf("x" to source.x.toString(), "z" to source.z.toString())),
            text("expand.segment", mapOf("size" to (plot.radius.toLong() * 2 + 1).toString())),
            text("expand.area", mapOf("used" to targetTotal.toString(), "max" to limit(limits.maxTotalArea))),
            text("expand.max_radius", mapOf("max" to limit(limits.maxRadius.toLong()))),
            text("expand.price", mapOf("price" to (quotedPrice?.toPlainString() ?: "?"))))
        directions.forEach { (slot, value) ->
            inventory.setItem(slot, item(if (value == direction) Material.LIME_CONCRETE else Material.COMPASS,
                text("expand.directions.${value.name.lowercase()}"), emptyList()))
        }
        inventory.setItem(bordersIndex, item(Material.ENDER_EYE, text("expand.show_borders"),
            listOf(text("expand.borders_duration", mapOf("seconds" to helpers.borderDurationSeconds().toString())))))
        inventory.setItem(confirmIndex, item(Material.EMERALD_BLOCK, text("expand.confirm"), summary))
        inventory.setItem(cancelIndex, item(Material.BARRIER, text("expand.cancel"), emptyList()))
        openLegacy(player)
    }

    private fun openLegacy(player: Player) { plugin.guiHandler.openLegacy(player, this) }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (submitted) return
        if (event.rawSlot == bordersIndex) {
            player.scheduler.run(plugin, {
                if (player.openInventory.topInventory !== inventory || submitted) return@run
                val plot = plugin.databaseHandler.getPlotById(plotId) ?: return@run
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
                helpers.visualizePlotBorder3D(player, plot, helpers.borderDurationSeconds(), 2, 4)
            }, null)
            return
        }
        directions[event.rawSlot]?.let {
            direction = it
            player.scheduler.run(plugin, {
                if (player.openInventory.topInventory === inventory && !submitted) open(player)
            }, null)
            return
        }
        if (event.rawSlot !in setOf(confirmIndex, cancelIndex)) return
        submitted = true
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
        val ownerUuid = player.uniqueId
        if (plot.ownerUuid != ownerUuid) {
            player.sendMessage(error("not_owner")); return
        }
        val source = quotedSource ?: run {
            player.sendMessage(text("expand.stand_inside")); return
        }
        if (player.world.name != plot.world || plot.segmentAt(player.location.blockX, player.location.blockZ) != source) {
            player.sendMessage(error("expand_quote_changed")); return
        }
        val target = plot.expansion(direction, source)
        val price = ExpansionEconomy.price(plugin, plot.extensions.size)
        if (price == null || quotedPrice == null) {
            player.sendMessage(error("expand_invalid_price")); return
        }
        if (target == null) {
            player.sendMessage(text("expand.direction_blocked")); return
        }
        if (price.compareTo(quotedPrice) != 0 || target != quotedSegment) {
            player.sendMessage(error("expand_quote_changed")); return
        }
        val limits = plugin.hookHandler.getPlotLimits(player)
        val world = plugin.server.getWorld(plot.world) ?: run {
            player.sendMessage(error("expand_failed")); return
        }
        if (plugin.regionProtectionHook?.overlapsProtectedRegion(world, target.x, target.z, target.radius) == true) {
            player.sendMessage(error("worldguard_collision")); return
        }
        // Economy APIs are synchronous. Finish withdrawal, SQL and compensation in this
        // callback so disabling the plugin cannot strand an asynchronous refund callback.
        val account = if (price.signum() > 0) {
            try { ExpansionEconomy.account(plugin, player, price) } catch (ex: Exception) {
                plugin.logger.err("Economy lookup failed: ${ex.message}")
                null
            } ?: run { player.sendMessage(error("expand_no_economy")); return }
        } else null
        if (account != null) {
            val paid = try { account.withdraw() } catch (ex: Exception) {
                plugin.logger.err("Expansion payment failed for $ownerUuid, amount=$price: ${ex.message}")
                false
            }
            if (!paid) { player.sendMessage(error("expand_payment_failed")); return }
        }
        val result = try {
            plugin.databaseHandler.expandPlotAtomically(
                plot.id, ownerUuid, player.uniqueId, direction, source, target, limits.maxRadius, limits.maxTotalArea, plot.extensions.size
            )
        } catch (ex: Exception) {
            plugin.logger.err("Expansion failed for plot ${plot.id}: ${ex.message}")
            DatabaseHandler.ExpandResult.DatabaseError
        }
        if (result !is DatabaseHandler.ExpandResult.Success && account != null) {
            val refunded = try { account.refund() } catch (ex: Exception) {
                plugin.logger.err("Expansion refund exception: ${ex.message}")
                false
            }
            if (!refunded) {
                plugin.logger.err("REFUND REQUIRED: player=$ownerUuid plot=${plot.id} amount=$price")
                player.sendMessage(error("expand_refund_failed"))
            }
        }
        showResult(player, plot, result)
    }

    private fun showResult(player: Player, oldPlot: PlotData, result: DatabaseHandler.ExpandResult) {
        val errorKey = when (result) {
            is DatabaseHandler.ExpandResult.Success -> {
                plugin.cacheManager.reloadPlotSync(oldPlot.id)
                player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                    "plots", "expand_success", mapOf("size" to (result.segment.radius.toLong() * 2 + 1).toString())
                ))
                helpers.visualizePlotBorder3D(player, oldPlot.copy(extensions = oldPlot.extensions + result.segment), helpers.borderDurationSeconds(), 2, 4)
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

    private fun limit(value: Long) = if (value == Long.MAX_VALUE || value == Int.MAX_VALUE.toLong()) "∞" else value.toString()
    private fun error(key: String) = plugin.messageHandler.stringMessageToComponent("error", key)
    private fun text(key: String, replacements: Map<String, String> = emptyMap()) =
        plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", key, replacements)
    private fun item(material: Material, name: Component, lore: List<Component>) = ItemStack(material).apply {
        itemMeta = itemMeta.apply { displayName(name); lore(lore) }
    }
}
