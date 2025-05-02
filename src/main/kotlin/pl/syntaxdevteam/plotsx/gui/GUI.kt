package pl.syntaxdevteam.plotsx.gui

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

interface GUI {
    val inventory: Inventory
    fun open(player: Player)
    fun handleClick(event: InventoryClickEvent)
    fun isThisInventory(inv: Inventory): Boolean
}
