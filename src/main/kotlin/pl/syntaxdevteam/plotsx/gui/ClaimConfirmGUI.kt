package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import pl.syntaxdevteam.plotsx.PlotsX

class ClaimConfirmGUI(
    private val plugin: PlotsX,
    private val player: Player,
    private val onConfirm: (Player) -> Unit,
    private val onCancel:  (Player) -> Unit
) : AbstractGUI(plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "claim.title_claim"), 3*9) {

    private val message = plugin.messageHandler

    private val confirmIndex = 11
    private val cancelIndex  = 15
    private var submitted = false

    override fun open(player: Player) {
        val loc = player.location
        val body = plugin.interactions.text(if (plugin.claimMode == pl.syntaxdevteam.plotsx.claiming.ClaimMode.CHUNKS) "claim_chunk_body" else "claim_body", mapOf(
            "chunk_x" to Math.floorDiv(loc.blockX, 16).toString(), "chunk_z" to Math.floorDiv(loc.blockZ, 16).toString(),
            "world" to loc.world.name, "x" to loc.blockX.toString(), "z" to loc.blockZ.toString(),
            "radius" to plugin.hookHandler.getClaimRadius(player).toString()))
        inventory.setItem(13, plugin.guiHandler.createItem(Material.MAP, body))
        openLegacy(player)
    }

    private fun openLegacy(player: Player) { plugin.guiHandler.openLegacy(player, this) }

    private fun submit(player: Player, confirmed: Boolean) {
        if (submitted) return
        submitted = true
        if (confirmed) onConfirm(player) else onCancel(player)
    }

    init {
        inventory.setItem(
            confirmIndex,
            plugin.guiHandler.createItem(
                Material.EMERALD_BLOCK,
                message.stringMessageToComponentNoPrefix("GUI", "claim.material_name.confirm")
            )
        )
        inventory.setItem(
            cancelIndex,
            plugin.guiHandler.createItem(
                Material.REDSTONE_BLOCK,
                message.stringMessageToComponentNoPrefix("GUI", "claim.material_name.cancel")
            )
        )
    }

    override fun handleClick(event: InventoryClickEvent) {
        event.isCancelled = true
        when (event.slot) {
            confirmIndex -> {
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
                submit(player, true)
            }
            cancelIndex  -> {
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
                submit(player, false)
            }
        }
    }
}
