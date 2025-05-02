package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import net.kyori.adventure.text.Component

abstract class AbstractGUI(
    title: Component,
    size: Int
) : GUI {
    override val inventory: Inventory = Bukkit.createInventory(null, size.coerceAtMost(54), title)

    override fun open(player: Player) {
        player.openInventory(inventory)
    }

    override fun isThisInventory(inv: Inventory): Boolean {
        return inv === inventory
    }
}
