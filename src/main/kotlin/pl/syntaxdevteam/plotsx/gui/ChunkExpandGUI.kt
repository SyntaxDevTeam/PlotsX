package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.*
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.hooks.ExpansionEconomy
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import java.math.BigDecimal
import java.util.UUID

/** Chunk offers use cached data. All SQL in the purchase is performed by a worker. */
internal class ChunkExpandGUI(private val plugin: PlotsX, private val plotId: Int) : AbstractGUI(
    plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "expand.title"), 27
) {
    private val directions = mapOf(4 to ExpansionDirection.NORTH, 14 to ExpansionDirection.EAST,
        22 to ExpansionDirection.SOUTH, 12 to ExpansionDirection.WEST)
    private var direction = ExpansionDirection.NORTH
    private var offered: PlotData? = null
    private var source: ChunkPosition? = null
    private var target: ChunkPosition? = null
    private var price: BigDecimal? = null
    private var operationId = UUID.randomUUID()
    private var submitted = false

    override fun open(player: Player) {
        val plot = plugin.cacheManager.getPlot(plotId) ?: return
        val geometry = plot.geometry as? ChunkGeometry ?: return
        offered = plot
        source = if (player.world.name == plot.world) ChunkPosition.atBlock(player.location.blockX, player.location.blockZ)
            .takeIf { it in geometry.chunks } else null
        target = source?.let { geometry.expansion(direction, it) }
        price = ExpansionEconomy.chunkPrice(plugin, plot.expansionLevel)
        operationId = UUID.randomUUID()
        directions.forEach { (slot, value) -> inventory.setItem(slot, item(
            if (value == direction) Material.LIME_CONCRETE else Material.COMPASS, "expand.directions.${value.name.lowercase()}")) }
        inventory.setItem(13, item(Material.ENDER_EYE, "expand.show_borders"))
        val lore = listOf(
            if (source == null) text("expand.stand_inside") else text("expand.source", mapOf("x" to source!!.x.toString(), "z" to source!!.z.toString())),
            if (target == null) text("expand.direction_blocked") else text("expand.chunk_target", mapOf("x" to target!!.x.toString(), "z" to target!!.z.toString())),
            text("expand.chunk_count", mapOf("count" to plot.chunks.size.toString(), "max" to plugin.hookHandler.getMaxChunksPerPlot(player).toString())),
            text("expand.price", mapOf("price" to (price?.toPlainString() ?: "?")))
        )
        inventory.setItem(20, item(Material.EMERALD_BLOCK, "expand.confirm").apply { itemMeta = itemMeta.apply { lore(lore) } })
        inventory.setItem(24, item(Material.BARRIER, "expand.cancel"))
        plugin.guiHandler.openLegacy(player, this)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (submitted) return
        directions[event.rawSlot]?.let {
            direction = it
            player.scheduler.run(plugin, {
                if (!submitted && player.openInventory.topInventory === inventory) open(player)
            }, null)
            return
        }
        if (event.rawSlot == 13) {
            plugin.cacheManager.getPlot(plotId)?.let { Helpers(plugin).visualizePlotBorder3D(player, it, Helpers(plugin).borderDurationSeconds(), 2, 4) }
            return
        }
        if (event.rawSlot !in setOf(20, 24)) return
        submitted = true
        val confirm = event.rawSlot == 20
        player.scheduler.run(plugin, {
            if (player.openInventory.topInventory !== inventory) return@run
            player.closeInventory(); plugin.guiHandler.unregisterGui(player)
            if (confirm) submit(player)
        }, null)
    }

    private fun submit(player: Player) {
        val plot = offered ?: return
        val from = source ?: run { player.sendMessage(text("expand.stand_inside")); return }
        val to = target ?: run { player.sendMessage(text("expand.direction_blocked")); return }
        val amount = price?.stripTrailingZeros()?.let { if (it.scale() < 0) it.setScale(0) else it }
        if (amount == null || amount.signum() < 0 || amount.precision() > 38 || amount.scale() > 18) {
            error(player, "expand_invalid_price"); return
        }
        val owner = player.uniqueId
        val limits = limits(player)
        fun valid(): Boolean {
            if (!player.isOnline || !PermissionChecker.canExpandPlot(player) || owner != plot.ownerUuid) return false
            val current = plugin.cacheManager.getPlot(plotId) ?: return false
            if (current.ownerUuid != owner || current.geometryRevision != plot.geometryRevision || current.radius != null ||
                current.expansionLevel != plot.expansionLevel || player.world.name != plot.world ||
                ChunkPosition.atBlock(player.location.blockX, player.location.blockZ) != from) return false
            if (ExpansionEconomy.chunkPrice(plugin, current.expansionLevel)?.compareTo(amount) != 0 || limits(player) != limits) return false
            return plugin.regionProtectionHook?.overlapsBounds(player.world, to.bounds) != true
        }
        if (!valid()) { error(player, "expand_quote_changed"); return }
        val account = if (amount.signum() == 0) null else try { ExpansionEconomy.account(plugin, player, amount, pinCurrency = true) }
            catch (failure: Exception) { plugin.logger.err("Economy lookup: ${failure.message}"); null }
        if (amount.signum() > 0 && account == null) { error(player, "expand_no_economy"); return }
        val operation = OperationJournal.Operation(operationId, plotId, owner, owner, plot.world, from, to,
            plot.geometryRevision, amount, account?.providerId ?: "free", account?.currencyId ?: "free", createdAt = System.currentTimeMillis())
        player.sendMessage(text("expand.chunk_processing"))
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = try { plugin.databaseHandler.purchaseChunk(operation, plot.expansionLevel, limits, account, ::valid) }
            catch (failure: Exception) {
                plugin.logger.err("Chunk purchase ${operation.id}: ${failure.message}")
                ChunkPurchaseService.Result.REVIEW_REQUIRED
            }
            if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                when (result) {
                    ChunkPurchaseService.Result.SUCCESS -> {
                        player.sendMessage(text("expand.chunk_success"))
                        plugin.cacheManager.getPlot(plotId)?.let { Helpers(plugin).visualizePlotBorder3D(player, it, Helpers(plugin).borderDurationSeconds(), 2, 4) }
                    }
                    ChunkPurchaseService.Result.AREA_LIMIT -> error(player, "expand_area_limit")
                    ChunkPurchaseService.Result.CHUNK_LIMIT -> error(player, "claim_chunk_limit")
                    ChunkPurchaseService.Result.COLLISION -> error(player, "expand_collision")
                    ChunkPurchaseService.Result.DECLINED -> error(player, "expand_payment_failed")
                    ChunkPurchaseService.Result.REFUNDED -> error(player, "expand_failed")
                    ChunkPurchaseService.Result.BUSY -> error(player, "chunk_purchase_busy")
                    ChunkPurchaseService.Result.REJECTED, ChunkPurchaseService.Result.DUPLICATE -> error(player, "expand_quote_changed")
                    ChunkPurchaseService.Result.REVIEW_REQUIRED -> player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                        "error", "chunk_purchase_review", mapOf("operation" to operation.id.toString())))
                }
            })
        })
    }

    private fun limits(player: Player) = ChunkExpansionTransaction.Limits(plugin.hookHandler.getPlotLimits(player).maxTotalArea,
        plugin.hookHandler.getMaxChunksPerPlot(player), plugin.hookHandler.getMaxOwnedChunks(player))
    private fun error(player: Player, key: String) { player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", key)) }
    private fun text(key: String, replacements: Map<String, String> = emptyMap()) =
        plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", key, replacements)
    private fun item(material: Material, key: String) = ItemStack(material).apply { itemMeta = itemMeta.apply { displayName(text(key)) } }
}
