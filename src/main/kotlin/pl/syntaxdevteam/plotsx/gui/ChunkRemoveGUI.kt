package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.ChunkRemovalTransaction
import pl.syntaxdevteam.plotsx.databases.ExpansionDirection
import pl.syntaxdevteam.plotsx.databases.Helpers
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

internal class ChunkRemoveGUI(
    private val plugin: PlotsX,
    private val plotId: Int
) : AbstractGUI(
    plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "remove_chunk.title"), 54
) {
    private val panButtons = mapOf(
        4 to ExpansionDirection.NORTH,
        26 to ExpansionDirection.EAST,
        49 to ExpansionDirection.SOUTH,
        18 to ExpansionDirection.WEST
    )
    private val mapSlots = (1..4).flatMap { row -> (1..7).map { column -> row * 9 + column } }
    private val bordersIndex = 45
    private val confirmIndex = 47
    private val cancelIndex = 51

    private var offered: PlotData? = null
    private var target: ChunkPosition? = null
    private var viewCenter: ChunkPosition? = null
    private var submitted = false

    override fun open(player: Player) {
        val plot = prepare(player) ?: run {
            plugin.guiHandler.unregisterGui(player)
            return
        }
        renderInventory(player, plot)
        plugin.guiHandler.openLegacy(player, this)
    }

    private fun prepare(player: Player): PlotData? {
        val plot = plugin.cacheManager.getPlot(plotId) ?: run {
            error(player, "plot_not_found")
            return null
        }
        val geometry = plot.geometry as? ChunkGeometry ?: run {
            error(player, "chunk_remove_wrong_geometry")
            return null
        }
        if (plot.ownerUuid != player.uniqueId) {
            error(player, "not_owner")
            return null
        }
        if (!PermissionChecker.canExpandPlot(player)) {
            error(player, "no_permission")
            return null
        }
        offered = plot
        val standing = if (player.world.name == plot.world)
            ChunkPosition.atBlock(player.location.blockX, player.location.blockZ) else null
        if (viewCenter == null) viewCenter = standing?.takeIf { it in geometry.chunks } ?: anchor(plot)
        target = target?.takeIf { isRemovable(plot, geometry, it) }
        return plot
    }

    private fun renderInventory(player: Player, plot: PlotData) {
        inventory.clear()
        val geometry = plot.geometry as ChunkGeometry
        val center = viewCenter ?: anchor(plot)
        val standing = if (player.world.name == plot.world)
            ChunkPosition.atBlock(player.location.blockX, player.location.blockZ) else null

        mapSlots.forEach { slot ->
            val row = slot / 9
            val column = slot % 9
            val position = offset(center, column - 4, row - 3) ?: return@forEach
            val owned = position in geometry.chunks
            val removable = owned && isRemovable(plot, geometry, position)
            val material = when {
                position == target -> Material.RED_CONCRETE
                position == anchor(plot) -> Material.GOLD_BLOCK
                owned && position == standing -> Material.PLAYER_HEAD
                removable -> Material.ORANGE_STAINED_GLASS_PANE
                owned -> Material.RED_STAINED_GLASS_PANE
                else -> Material.GRAY_STAINED_GLASS_PANE
            }
            val key = when {
                position == target -> "remove_chunk.map_selected"
                position == anchor(plot) -> "remove_chunk.map_anchor"
                owned && position == standing -> "remove_chunk.map_player"
                removable -> "remove_chunk.map_removable"
                owned -> "remove_chunk.map_locked"
                else -> "remove_chunk.map_empty"
            }
            inventory.setItem(slot, mapItem(material, key, position))
        }

        panButtons.forEach { (slot, direction) ->
            inventory.setItem(slot, item(Material.ARROW, "remove_chunk.map_pan_${direction.name.lowercase()}"))
        }
        inventory.setItem(bordersIndex, item(Material.ENDER_EYE, "remove_chunk.show_borders"))
        val lore = listOf(
            target?.let {
                text("remove_chunk.chunk_target", mapOf("x" to it.x.toString(), "z" to it.z.toString()))
            } ?: text("remove_chunk.map_choose"),
            text("remove_chunk.chunk_count", mapOf("count" to geometry.chunks.size.toString())),
            text("remove_chunk.no_refund")
        )
        inventory.setItem(
            confirmIndex,
            item(if (target == null) Material.GRAY_DYE else Material.REDSTONE_BLOCK, "remove_chunk.confirm").apply {
                itemMeta = itemMeta.apply { lore(lore) }
            }
        )
        inventory.setItem(cancelIndex, item(Material.BARRIER, "remove_chunk.cancel"))
    }

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

        if (event.rawSlot in mapSlots) {
            val plot = offered ?: return
            val geometry = plot.geometry as? ChunkGeometry ?: return
            val row = event.rawSlot / 9
            val column = event.rawSlot % 9
            val candidate = viewCenter?.let { offset(it, column - 4, row - 3) } ?: return
            if (candidate !in geometry.chunks) return
            when {
                geometry.chunks.size <= 1 -> player.sendMessage(text("remove_chunk.last_locked"))
                candidate == anchor(plot) -> player.sendMessage(text("remove_chunk.anchor_locked"))
                geometry.without(candidate) == null -> player.sendMessage(text("remove_chunk.disconnect_locked"))
                else -> {
                    target = candidate
                    renderInventory(player, plot)
                }
            }
            return
        }

        if (event.rawSlot == bordersIndex) {
            plugin.cacheManager.getPlot(plotId)?.let {
                Helpers(plugin).visualizePlotBorder3D(player, it, Helpers(plugin).borderDurationSeconds(), 2, 4)
            }
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
        val selected = target ?: run {
            player.sendMessage(text("remove_chunk.map_choose"))
            return
        }
        val current = refreshSelectedOffer(player, selected) ?: return
        submitted = true
        inventory.setItem(confirmIndex, item(Material.CLOCK, "remove_chunk.processing"))

        val request = ChunkRemovalTransaction.Request(
            plotId = plotId,
            owner = player.uniqueId,
            actor = player.uniqueId,
            target = selected,
            expectedRevision = current.geometryRevision
        )
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val result = try {
                plugin.databaseHandler.removeChunkAtomically(request)
            } catch (failure: Exception) {
                plugin.logger.err("Chunk removal from plot $plotId failed: ${failure.message}")
                null
            }
            if (!plugin.isEnabled) return@Runnable
            player.scheduler.run(plugin, {
                if (!player.isOnline) return@run
                submitted = false
                when (result) {
                    is ChunkRemovalTransaction.Result.Success -> {
                        target = null
                        player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                            "plots", "chunk_remove_success",
                            mapOf("x" to result.chunk.x.toString(), "z" to result.chunk.z.toString())
                        ))
                        plugin.cacheManager.getPlot(plotId)?.let { fresh ->
                            if (player.world.name == fresh.world) {
                                Helpers(plugin).visualizePlotBorder3D(
                                    player, fresh, Helpers(plugin).borderDurationSeconds(), 2, 4
                                )
                            }
                            if (player.openInventory.topInventory === inventory) {
                                offered = fresh
                                renderInventory(player, fresh)
                            }
                        }
                    }
                    ChunkRemovalTransaction.Result.LastChunk -> error(player, "chunk_remove_last")
                    ChunkRemovalTransaction.Result.AnchorChunk -> error(player, "chunk_remove_anchor")
                    ChunkRemovalTransaction.Result.WouldDisconnect -> error(player, "chunk_remove_disconnect")
                    ChunkRemovalTransaction.Result.NotOwner -> error(player, "not_owner")
                    ChunkRemovalTransaction.Result.WrongGeometry -> error(player, "chunk_remove_wrong_geometry")
                    ChunkRemovalTransaction.Result.PlotNotFound -> error(player, "plot_not_found")
                    ChunkRemovalTransaction.Result.StaleQuote,
                    ChunkRemovalTransaction.Result.ChunkNotFound -> error(player, "chunk_remove_changed")
                    null -> error(player, "chunk_remove_failed")
                }
                if (result !is ChunkRemovalTransaction.Result.Success && player.openInventory.topInventory === inventory) {
                    val fresh = plugin.cacheManager.getPlot(plotId)
                    if (fresh != null && fresh.geometry is ChunkGeometry) {
                        offered = fresh
                        target = null
                        renderInventory(player, fresh)
                    }
                }
            }, null)
        })
    }

    private fun refreshSelectedOffer(player: Player, selected: ChunkPosition): PlotData? {
        val current = plugin.cacheManager.getPlot(plotId) ?: run {
            error(player, "plot_not_found")
            return null
        }
        val geometry = current.geometry as? ChunkGeometry ?: run {
            error(player, "chunk_remove_wrong_geometry")
            return null
        }
        if (current.ownerUuid != player.uniqueId || !PermissionChecker.canExpandPlot(player)) {
            error(player, if (current.ownerUuid != player.uniqueId) "not_owner" else "no_permission")
            return null
        }
        when {
            selected !in geometry.chunks -> error(player, "chunk_remove_changed")
            geometry.chunks.size <= 1 -> error(player, "chunk_remove_last")
            selected == anchor(current) -> error(player, "chunk_remove_anchor")
            geometry.without(selected) == null -> error(player, "chunk_remove_disconnect")
            else -> {
                offered = current
                target = selected
                return current
            }
        }
        target = null
        renderInventory(player, current)
        return null
    }

    private fun isRemovable(plot: PlotData, geometry: ChunkGeometry, position: ChunkPosition): Boolean =
        position != anchor(plot) && geometry.without(position) != null

    private fun anchor(plot: PlotData): ChunkPosition = ChunkPosition.atBlock(plot.x, plot.z)

    private fun text(key: String, replacements: Map<String, String> = emptyMap()) =
        plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", key, replacements)

    private fun error(player: Player, key: String) {
        player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", key))
    }

    private fun item(material: Material, key: String) = ItemStack(material).apply {
        itemMeta = itemMeta.apply { displayName(text(key)) }
    }

    private fun mapItem(material: Material, key: String, position: ChunkPosition) = item(material, key).apply {
        itemMeta = itemMeta.apply {
            lore(listOf(text("remove_chunk.map_coordinates", mapOf(
                "x" to position.x.toString(), "z" to position.z.toString()
            ))))
        }
    }

    private fun offset(origin: ChunkPosition, dx: Int, dz: Int): ChunkPosition? {
        val x = origin.x.toLong() + dx
        val z = origin.z.toLong() + dz
        if (x !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong() ||
            z !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong()) return null
        return ChunkPosition(x.toInt(), z.toInt())
    }
}
