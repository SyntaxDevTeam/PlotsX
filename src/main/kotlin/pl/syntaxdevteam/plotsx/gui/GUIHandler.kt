package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import pl.syntaxdevteam.plotsx.PlotsX

class GUIHandler(private val plugin: PlotsX) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val title = event.view.title()

        when (title) {
            Component.text(plugin.messageHandler.getCleanMessage("GUI", "title_claimGUI")) -> ClaimConfirmGUI(plugin).handleClick(event)
            // miejsce na kolejne GUI
        }
    }
}