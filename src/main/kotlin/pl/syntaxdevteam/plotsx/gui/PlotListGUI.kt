package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

class PlotListGUI(
    private val plugin: PlotsX,
    private val plots: List<PlotData>,
    private val ownerUuid: UUID
) : AbstractGUI(
    title = plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "plot.list_title"),
    size  = calculateSize(plots.size)
) {

    override fun open(player: Player) {
        inventory.clear()

        plots.forEachIndexed { index, plot ->
            if (index >= inventory.size) return@forEachIndexed

            inventory.setItem(index, createPlotItem(plot, index))
        }

        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        val player      = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        val meta        = clickedItem.itemMeta ?: return

        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()

        val displayName = meta.displayName() ?: return
        val plotName    = PlainTextComponentSerializer.plainText().serialize(displayName)

        plots.firstOrNull { it.name.equals(plotName, ignoreCase = true) }
            ?.let { plugin.guiHandler.registerGui(player, PlotGUI(plugin, it, ownerUuid)) }
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
        val item     = ItemStack(material)
        val meta     = item.itemMeta!!

        meta.displayName(Component.text(plot.name, NamedTextColor.GREEN))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val created    = dateFormat.format(Date(plot.creationTime))
        meta.lore(listOf(
            Component.text("Location: ${plot.x}, ${plot.z} (${plot.world})"),
            Component.text("Created: $created"),
            plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "plot.list_click_hint")
        ))

        item.itemMeta = meta
        return item
    }

    companion object {
        private fun calculateSize(amount: Int): Int {
            val rows = ((amount + 8) / 9).coerceAtMost(6)
            return rows * 9
        }
    }
}
