package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import java.util.*

class GUIHandler(private var plugin: PlotsX) : Listener {

    private val openGuis: MutableMap<UUID, GUI> = mutableMapOf()
    private val message = plugin.messageHandler

    fun registerGui(player: Player, gui: GUI) {
        openGuis[player.uniqueId] = gui
        gui.open(player)
    }

    fun unregisterGui(player: Player) {
        openGuis.remove(player.uniqueId)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val gui = openGuis[player.uniqueId] ?: return
        if (!gui.isThisInventory(event.inventory)) return

        event.isCancelled = true

        gui.handleClick(event)
    }

    fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(message.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }
}
