package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.text.SimpleDateFormat
import java.util.*

class PlotGUI(
    private val plugin: PlotsX,
    private val plot: PlotData? = null
) : GUI {

    private val message = plugin.messageHandler
    private val plotIndex = 13

    private val flagsTitleMaterialName = message.getCleanMessage("GUI", "plot.material_name.flags")
    private val flagsMaterial = Material.CALIBRATED_SCULK_SENSOR
    private val flagsIndex = 20

    private val tpaTitleMaterialName = message.getCleanMessage("GUI", "plot.material_name.teleport")
    private val tpaMaterial = Material.MINECART
    private val tpaIndex = 24

    private val listTitleMaterialName = message.getCleanMessage("GUI", "plot.material_name.list")
    private val listMaterial = Material.GRASS_BLOCK
    private val listIndex = 31

    override fun open(player: Player) {
        val inventory = Bukkit.createInventory(null, 45, getTitle())

        val targetPlot = plot ?: plugin.databaseHandler.getPlotAtLocation(
            player.location.world.name,
            player.location.blockX,
            player.location.blockZ
        )

        if (targetPlot == null) {
            player.sendMessage(message.getMessage("error", "no_in_plot"))
            return
        }

        val flagsItem = createItem(flagsMaterial, flagsTitleMaterialName)
        val tpaItem = createItem(tpaMaterial, tpaTitleMaterialName)
        val plotItem = createPlotHead(player, targetPlot)
        val listItem = createItem(listMaterial, listTitleMaterialName)

        inventory.setItem(plotIndex, plotItem)
        inventory.setItem(flagsIndex, flagsItem)
        inventory.setItem(tpaIndex, tpaItem)
        inventory.setItem(listIndex, listItem)

        plugin.guiHandler.track(player, this)
        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return
        event.isCancelled = true

        when (clickedItem.type) {
            flagsMaterial -> {
                if (plot != null) {
                    plugin.guiHandler.openGUI(player, FlagsGUI(plugin, plot))
                } else {
                    player.sendMessage(message.getMessage("error", "no_in_plot"))
                }

                player.sendMessage("Otwieram GUI z flagami")
                //player.closeInventory()
            }
            tpaMaterial -> {

                player.sendMessage("Teleportuje na działkę")
                player.closeInventory()
            }
            listMaterial -> {
                val uuid = plugin.uuidManager.getUUID(player.name)
                val playerPlots = plugin.databaseHandler.getPlayerPlots(uuid)
                plugin.guiHandler.openGUI(player, PlotListGUI(plugin, playerPlots))
            }
            else -> {}
        }
    }

    override fun getTitle(): Component {
        return message.getLogMessage("GUI", "plot.title_plot")
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(message.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }

    private fun createPlotHead(player: Player, plot: PlotData): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta

        meta.owningPlayer = player
        meta.displayName(message.getLogMessage("GUI", "plot.material_name.info", mapOf("plot" to plot.name)))

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val creationTime = dateFormat.format(Date(plot.creationTime))
        val lore = listOf(
            message.getLogMessage("GUI", "plot.info.plot_name", mapOf("plot" to plot.name)),
            message.getLogMessage("GUI", "plot.info.id", mapOf("id" to plot.id.toString())),
            message.getLogMessage("GUI", "plot.info.creation_time", mapOf("time" to creationTime))
        )

        meta.lore(lore)
        skull.itemMeta = meta
        return skull
    }
}