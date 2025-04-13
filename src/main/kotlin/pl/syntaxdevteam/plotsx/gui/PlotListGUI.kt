package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.text.SimpleDateFormat
import java.util.*

class PlotListGUI(private val plugin: PlotsX, private val plots: List<PlotData>) : GUI {

    override fun open(player: Player) {
        val size = calculateInventorySize(plots.size)
        val inventory = Bukkit.createInventory(null, size, getTitle())

        plots.forEachIndexed { index, plot ->
            val head = createPlotItem(plot, index)
            inventory.setItem(index, head)
        }

        plugin.guiHandler.track(player, this)
        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        event.isCancelled = true

        val displayName = clickedItem.itemMeta?.displayName() ?: return
        val plotName = PlainTextComponentSerializer.plainText().serialize(displayName)
        val plot = plots.firstOrNull { it.name.equals(plotName, ignoreCase = true) } ?: return

        plugin.guiHandler.openGUI(player, PlotGUI(plugin, plot))
    }

    override fun getTitle(): Component {
        return plugin.messageHandler.getLogMessage("GUI", "plot.list_title")
    }

    private fun createPlotItem(plot: PlotData, index: Int): ItemStack {
        val dirtVariants = listOf(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.ROOTED_DIRT,
            Material.MYCELIUM
        )

        val material = dirtVariants[index % dirtVariants.size]
        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(Component.text(plot.name, NamedTextColor.GREEN))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val createdDate = dateFormat.format(Date(plot.creationTime))
        meta.lore(listOf(
            Component.text("Location: ${plot.x}, ${plot.z} (${plot.world})"),
            Component.text("Created: $createdDate"),
            Component.text("Click to manage this plot.")
        ))

        item.itemMeta = meta
        return item
    }

    private fun calculateInventorySize(amount: Int): Int {
        return ((amount / 9) + 1).coerceAtMost(6) * 9
    }
}
