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
) : AbstractGUI(plugin.messageHandler.getLogMessage("GUI", "claim.title_claim"), 3*9) {

    private val message = plugin.messageHandler

    private val confirmIndex = 11
    private val cancelIndex  = 15

    init {
        inventory.setItem(confirmIndex,
            plugin.guiHandler.createItem(Material.EMERALD_BLOCK,
                message.getCleanMessage("GUI", "claim.material_name.confirm"))
        )
        inventory.setItem(cancelIndex,
            plugin.guiHandler.createItem(Material.REDSTONE_BLOCK,
                message.getCleanMessage("GUI", "claim.material_name.cancel"))
        )
    }

    override fun handleClick(event: InventoryClickEvent) {
        event.isCancelled = true
        when (event.slot) {
            confirmIndex -> {
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
                onConfirm(player)
            }
            cancelIndex  -> {
                player.closeInventory()
                plugin.guiHandler.unregisterGui(player)
                onCancel(player)
            }
        }
    }
}
