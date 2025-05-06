package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.Helpers
import pl.syntaxdevteam.plotsx.databases.PlotData
import net.kyori.adventure.text.Component
import java.text.SimpleDateFormat
import java.util.*

class PlotGUI(
    private val plugin: PlotsX,
    private val plot: PlotData? = null
) : AbstractGUI(
    title = plugin.messageHandler.getLogMessage("GUI", "plot.title_plot"),
    size  = 45
) {

    private val message = plugin.messageHandler
    private val helpers = Helpers(plugin)
    private val plotIndex  = 13
    private val flagsIndex = 20
    private val tpaIndex   = 24
    private val listIndex  = 31

    override fun open(player: Player) {
        // pobierz działkę (jeśli nie w konstruktorze)
        val targetPlot = plot ?: plugin.databaseHandler.getPlotAtLocation(
            player.location.world!!.name,
            player.location.blockX,
            player.location.blockZ
        )

        if (targetPlot == null) {
            player.sendMessage(message.getMessage("error", "no_in_plot"))
            return
        }

        inventory.setItem(plotIndex,  createPlotHead(player, targetPlot))
        inventory.setItem(flagsIndex, createItem(
            Material.CALIBRATED_SCULK_SENSOR,
            message.getCleanMessage("GUI", "plot.material_name.flags")
        ))
        inventory.setItem(tpaIndex,   createItem(
            Material.MINECART,
            message.getCleanMessage("GUI", "plot.material_name.teleport")
        ))
        inventory.setItem(listIndex,  createItem(
            Material.GRASS_BLOCK,
            message.getCleanMessage("GUI", "plot.material_name.list")
        ))

        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()
        val pd = plot ?: plugin.databaseHandler.getPlotAtLocation(
            player.location.world!!.name,
            player.location.blockX,
            player.location.blockZ
        )

        when (event.slot) {
            flagsIndex -> {
                if (pd == null) {
                    player.sendMessage(message.getMessage("error", "no_in_plot"))
                } else {
                    plugin.guiHandler.registerGui(player, FlagsGUI(plugin, pd))
                }
            }
            tpaIndex -> {
                // tu wstawię logikę TP ale... nieco później xD
                player.sendMessage("Teleportuję na działkę…")
            }
            listIndex -> {
                val uuid = plugin.uuidManager.getUUID(player.name)
                val playerPlots = plugin.databaseHandler.getPlayerPlots(uuid)
                plugin.guiHandler.registerGui(player, PlotListGUI(plugin, playerPlots))
            }
            plotIndex -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    helpers.visualizePlotBorder3D(
                        player    = player,
                        centerX   = pd!!.x,
                        centerZ   = pd.z,
                        radius    = pd.radius,
                        durationSec = 20,
                        stepXZ      = 2,
                        stepY    = 4
                    )
                })
            }
        }
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.displayName(message.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }

    private fun createPlotHead(player: Player, plot: PlotData): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta

        meta.owningPlayer = player
        meta.displayName(message.getLogMessage(
            "GUI", "plot.material_name.info",
            mapOf("plot" to plot.name)
        ))

        val dateFormat   = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val creationTime = dateFormat.format(Date(plot.creationTime))
        val lore = listOf(
            message.getLogMessage("GUI", "plot.info.plot_name",    mapOf("plot" to plot.name)),
            message.getLogMessage("GUI", "plot.info.id",           mapOf("id"   to plot.id.toString())),
            message.getLogMessage("GUI", "plot.info.creation_time",mapOf("time" to creationTime)),
            Component.text(" "),
            message.getLogMessage("GUI", "plot.info.click")
        )
        meta.lore(lore)
        skull.itemMeta = meta
        return skull
    }
}
