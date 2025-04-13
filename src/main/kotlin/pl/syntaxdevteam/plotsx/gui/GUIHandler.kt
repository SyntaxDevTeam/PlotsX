package pl.syntaxdevteam.plotsx.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import pl.syntaxdevteam.plotsx.PlotsX
import java.util.*

class GUIHandler(private val plugin: PlotsX) : Listener {

    private val activeGUIs = mutableMapOf<UUID, GUI>()

    fun track(player: Player, gui: GUI) {
        activeGUIs[plugin.uuidManager.getUUID(player.name)] = gui
    }

    fun untrack(player: Player) {
        activeGUIs.remove(plugin.uuidManager.getUUID(player.name))
    }

    fun openGUI(player: Player, gui: GUI) {
        activeGUIs[plugin.uuidManager.getUUID(player.name)] = gui
        gui.open(player)
    }


    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val gui = activeGUIs[plugin.uuidManager.getUUID(player.name)] ?: return

        gui.handleClick(event)
    }
}
