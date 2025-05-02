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
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
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

    // sloty
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

        // przygotuj itemy
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

        super.open(player)  // otwiera inventory i zostawia ślad w GUIHandlerze
    }

    override fun handleClick(event: InventoryClickEvent) {
        // tylko nasze inventory
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        // najpierw wyrejestruj i zamknij
        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()

        when (event.slot) {
            flagsIndex -> {
                val pd = plot ?: plugin.databaseHandler.getPlotAtLocation(
                    player.location.world!!.name,
                    player.location.blockX,
                    player.location.blockZ
                )
                if (pd == null) {
                    player.sendMessage(message.getMessage("error", "no_in_plot"))
                } else {
                    plugin.guiHandler.registerGui(player, FlagsGUI(plugin, pd))
                }
            }
            tpaIndex -> {
                // tu wstaw swoją logikę TP
                player.sendMessage("Teleportuję na działkę…")
            }
            listIndex -> {
                val uuid = plugin.uuidManager.getUUID(player.name)
                val playerPlots = plugin.databaseHandler.getPlayerPlots(uuid)
                plugin.guiHandler.registerGui(player, PlotListGUI(plugin, playerPlots))
            }
            plotIndex -> {
                // np. otwarcie dodatkowych info albo nic
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
            message.getLogMessage("GUI", "plot.info.creation_time",mapOf("time" to creationTime))
        )
        meta.lore(lore)
        skull.itemMeta = meta
        return skull
    }
}
