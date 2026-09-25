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
internal class ChunkExpandGUI(
    private val plugin: PlotsX,
    private val plotId: Int,
    private var requestedTarget: ChunkPosition? = null
) : AbstractGUI(
    plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "expand.title"), 54
) {
    private val panButtons = mapOf(4 to ExpansionDirection.NORTH, 26 to ExpansionDirection.EAST,
        49 to ExpansionDirection.SOUTH, 18 to ExpansionDirection.WEST)
    private val mapSlots = (1..4).flatMap { row -> (1..7).map { column -> row * 9 + column } }
    private val bordersIndex = 45
    private val confirmIndex = 47
    private val cancelIndex = 51
    private var direction = ExpansionDirection.NORTH
    private var offered: PlotData? = null
    private var source: ChunkPosition? = null
    private var target: ChunkPosition? = null
    private var viewCenter: ChunkPosition? = null
    private var price: BigDecimal? = null
    private var operationId = UUID.randomUUID()
    private var submitted = false

    override fun open(player: Player) {
        val plot = prepareOffer(player) ?: return
        renderInventory(player, plot)
        openLegacy(player)
    }

    private fun prepareOffer(player: Player): PlotData? {
        val plot = plugin.cacheManager.getPlot(plotId) ?: return null
        val geometry = plot.geometry as? ChunkGeometry ?: return null
        offered = plot
        val standing = if (player.world.name == plot.world)
            ChunkPosition.atBlock(player.location.blockX, player.location.blockZ) else null
        val fixedRequestedTarget = requestedTarget
        if (viewCenter == null) viewCenter = fixedRequestedTarget ?: standing?.takeIf { it in geometry.chunks } ?: geometry.chunks.first()
        if (fixedRequestedTarget != null) {
            val expansion = geometry.expansionTo(fixedRequestedTarget) ?: return null
            source = expansion.source
            direction = expansion.direction
            target = expansion.target
        } else if (target != null) {
            val expansion = geometry.expansionTo(target!!)
            source = expansion?.source
            direction = expansion?.direction ?: direction
            target = expansion?.target
        }
        price = ExpansionEconomy.chunkPrice(plugin, plot.expansionLevel)
        operationId = UUID.randomUUID()
        return plot
    }

private fun renderInventory(player: Player, plot: PlotData) {
inventory.clear()
val geometry = plot.geometry as ChunkGeometry
val center = requireNotNull(viewCenter)
val standing = ChunkPosition.atBlock(player.location.blockX, player.location.blockZ)
mapSlots.forEach { slot ->
    val row = slot / 9
    val column = slot % 9
    val position = offset(center, column - 4, row - 3) ?: return@forEach
    val expansion = geometry.expansionTo(position)
    val blocked = expansion != null && isBlocked(player, plot, position)
    val material = when {
        position == target -> Material.EMERALD_BLOCK
        position in geometry.chunks && position == standing -> Material.PLAYER_HEAD
        position in geometry.chunks -> Material.WHITE_CONCRETE
        blocked -> Material.RED_STAINED_GLASS_PANE
        expansion != null -> Material.LIME_STAINED_GLASS_PANE
        else -> Material.GRAY_STAINED_GLASS_PANE
    }
    val key = when {
        position == target -> "expand.map_selected"
        position in geometry.chunks && position == standing -> "expand.map_player"
        position in geometry.chunks -> "expand.map_claimed"
        blocked -> "expand.map_blocked"
        expansion != null -> "expand.map_available"
        else -> "expand.map_empty"
    }
    inventory.setItem(slot, mapItem(material, key, position))
}
panButtons.forEach { (slot, value) -> inventory.setItem(slot,
    item(Material.ARROW, "expand.map_pan_${value.name.lowercase()}")) }
inventory.setItem(bordersIndex, item(Material.ENDER_EYE, "expand.show_borders"))
val lore = listOf(
    if (target == null) text("expand.map_choose") else text("expand.chunk_target", mapOf("x" to target!!.x.toString(), "z" to target!!.z.toString())),
    text("expand.chunk_count", mapOf("count" to plot.chunks.size.toString(), "max" to plugin.hookHandler.getMaxChunksPerPlot(player).toString())),
    text("expand.price", mapOf("price" to (price?.toPlainString() ?: "?")))
)
inventory.setItem(confirmIndex, item(if (target == null) Material.GRAY_DYE else Material.EMERALD_BLOCK,
    "expand.confirm").apply { itemMeta = itemMeta.apply { lore(lore) } })
inventory.setItem(cancelIndex, item(Material.BARRIER, "expand.cancel"))
}

    private fun openLegacy(player: Player) { plugin.guiHandler.openLegacy(player, this) }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (submitted) return
        panButtons[event.rawSlot]?.let {
            viewCenter = viewCenter?.neighbour(it) ?: viewCenter
            offered?.let { plot -> renderInventory(player, plot) }
            return
        }
        if (event.rawSlot in mapSlots && requestedTarget == null) {
            val plot = offered ?: return
            val geometry = plot.geometry as? ChunkGeometry ?: return
            val row = event.rawSlot / 9
            val column = event.rawSlot % 9
            val candidate = viewCenter?.let { offset(it, column - 4, row - 3) } ?: return
            val expansion = geometry.expansionTo(candidate) ?: return
            if (isBlocked(player, plot, candidate)) return
            source = expansion.source
            direction = expansion.direction
            target = expansion.target
            operationId = UUID.randomUUID()
            renderInventory(player, plot)
            return
        }
        if (event.rawSlot == bordersIndex) {
            plugin.cacheManager.getPlot(plotId)?.let { Helpers(plugin).visualizePlotBorder3D(player, it, Helpers(plugin).borderDurationSeconds(), 2, 4) }
            return
        }
        if (event.rawSlot == cancelIndex) {
            submitted = true
            player.scheduler.run(plugin, {
                if (player.openInventory.topInventory !== inventory) return@run
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
            }, null)
            return
        }
        if (event.rawSlot != confirmIndex) return
        if (target == null) { player.sendMessage(text("expand.map_choose")); return }
        if (!refreshSelectedOffer(player)) return
        submitted = true
        inventory.setItem(confirmIndex, item(Material.CLOCK, "expand.processing"))
        submit(player)
    }

    /** Refreshes the quote immediately before closing the GUI, so map navigation cannot submit stale data. */
    private fun refreshSelectedOffer(player: Player): Boolean {
        val selected = target ?: return false
        val current = plugin.cacheManager.getPlot(plotId)
        val geometry = current?.geometry as? ChunkGeometry
        val expansion = geometry?.expansionTo(selected)
        val standing = ChunkPosition.atBlock(player.location.blockX, player.location.blockZ)
        if (current == null || current.ownerUuid != player.uniqueId || player.world.name != current.world ||
            expansion == null || (requestedTarget == null && standing !in geometry.chunks) ||
            (requestedTarget != null && standing != selected)) {
            player.sendMessage(text(if (requestedTarget == null) "expand.stand_inside" else "expand.direction_blocked"))
            target = null
            source = null
            current?.let { renderInventory(player, it) }
            return false
        }
        if (isBlocked(player, current, selected)) {
            player.sendMessage(text("expand.direction_blocked"))
            target = null
            source = null
            renderInventory(player, current)
            return false
        }
        offered = current
        source = expansion.source
        direction = expansion.direction
        target = expansion.target
        price = ExpansionEconomy.chunkPrice(plugin, current.expansionLevel)
        operationId = UUID.randomUUID()
        return true
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
                current.expansionLevel != plot.expansionLevel || player.world.name != plot.world) return false
            val standing = ChunkPosition.atBlock(player.location.blockX, player.location.blockZ)
            val currentGeometry = current.geometry as? ChunkGeometry ?: return false
            if ((requestedTarget == null && standing !in currentGeometry.chunks) ||
                (requestedTarget != null && standing != to)) return false
            if (ExpansionEconomy.chunkPrice(plugin, current.expansionLevel)?.compareTo(amount) != 0 || limits(player) != limits) return false
            return plugin.regionProtectionHook?.overlapsBounds(player.world, to.bounds) != true
        }
        if (!valid()) { error(player, "expand_quote_changed"); return }
        val account = if (amount.signum() == 0) null else try { ExpansionEconomy.account(plugin, player, amount, pinCurrency = true) }
            catch (failure: Exception) { plugin.logger.err("Economy lookup: ${failure.message}"); null }
        if (amount.signum() > 0 && account == null) { error(player, "expand_no_economy"); return }
        val operation = OperationJournal.Operation(operationId, plotId, owner, owner, plot.world, from, to,
            plot.geometryRevision, amount, account?.providerId ?: "free", account?.currencyId ?: "free", createdAt = System.currentTimeMillis())
        plugin.logger.debug(
            "Chunk purchase started: operation=${operation.id}, player=${player.name}, plot=$plotId, " +
                "source=${from.x},${from.z}, target=${to.x},${to.z}, amount=$amount"
        )
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = try { plugin.databaseHandler.purchaseChunk(operation, plot.expansionLevel, limits, account, ::valid) }
            catch (failure: Exception) {
                plugin.logger.err("Chunk purchase ${operation.id}: ${failure.message}")
                ChunkPurchaseService.Result.REVIEW_REQUIRED
            }
            if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable {
                if (!player.isOnline) return@Runnable
                submitted = false
                when (result) {
                    ChunkPurchaseService.Result.SUCCESS -> {
                        plugin.logger.debug(
                            "Chunk purchase completed: operation=${operation.id}, player=${player.name}, " +
                                "plot=$plotId, target=${to.x},${to.z}"
                        )
                        requestedTarget = null
                        target = null
                        source = null
                        viewCenter = to
                        player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                            "plots", "chunk_expand_success", mapOf("x" to to.x.toString(), "z" to to.z.toString())))
                        plugin.cacheManager.getPlot(plotId)?.let { current ->
                            Helpers(plugin).visualizePlotBorder3D(player, current, Helpers(plugin).borderDurationSeconds(), 2, 4)
                            if (player.openInventory.topInventory === inventory) {
                                prepareOffer(player)?.let { renderInventory(player, it) }
                            }
                        }
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
                if (result != ChunkPurchaseService.Result.SUCCESS && player.openInventory.topInventory === inventory) {
                    val current = plugin.cacheManager.getPlot(plotId)
                    if (current != null) {
                        offered = current
                        price = ExpansionEconomy.chunkPrice(plugin, current.expansionLevel)
                        renderInventory(player, current)
                    }
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
    private fun mapItem(material: Material, key: String, position: ChunkPosition) = item(material, key).apply {
        itemMeta = itemMeta.apply { lore(listOf(text("expand.map_coordinates", mapOf(
            "x" to position.x.toString(), "z" to position.z.toString())))) }
    }
    private fun offset(origin: ChunkPosition, dx: Int, dz: Int): ChunkPosition? {
        val x = origin.x.toLong() + dx
        val z = origin.z.toLong() + dz
        if (x !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong() ||
            z !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong()) return null
        return ChunkPosition(x.toInt(), z.toInt())
    }
    private fun isBlocked(player: Player, plot: PlotData, position: ChunkPosition): Boolean {
        val occupied = plugin.cacheManager.getCachedPlots().any { other ->
            other.id != plot.id && other.world.equals(plot.world, true) &&
                other.geometry.intersects(ChunkGeometry(setOf(position)))
        }
        return occupied || plugin.regionProtectionHook?.overlapsBounds(player.world, position.bounds) == true
    }
}
