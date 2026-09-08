package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.Helpers
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.protection.SafeTeleportUtil
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import java.text.SimpleDateFormat
import java.util.*

class PlotGUI(
    private val plugin: PlotsX,
    private val plot: PlotData? = null,
    private val ownerUuid: UUID
) : AbstractGUI(
    title = plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "plot.title_plot"),
    size = 45
) {

    private val message = plugin.messageHandler
    private val helpers = Helpers(plugin)
    private val plotIndex = 4
    private val flagsIndex = 11
    private val tpaIndex = 15
    private val renameIndex = 29
    private val listIndex = 31
    private val expandIndex = 33
    private val membersIndex = 13

    override fun open(player: Player) {
        val targetPlot = plot ?: plugin.databaseHandler.getPlotAtLocation(
            player.location.world!!.name,
            player.location.blockX,
            player.location.blockZ
        )

        if (targetPlot == null) {
            player.sendMessage(message.stringMessageToComponent("error", "no_in_plot"))
            return
        }

        val current = plugin.databaseHandler.getPlotById(targetPlot.id) ?: return
        if (!PlotAccess(plugin).canOpen(player, current)) {
            player.sendMessage(message.stringMessageToComponent("error", "no_permission"))
            return
        }
        inventory.setItem(membersIndex, createItem(Material.PLAYER_HEAD,
            message.stringMessageToComponentNoPrefix("members", "title")))

        inventory.setItem(plotIndex, createPlotHead(player, targetPlot))
        inventory.setItem(
            flagsIndex,
            createItem(
                Material.CALIBRATED_SCULK_SENSOR,
                message.stringMessageToComponentNoPrefix("GUI", "plot.material_name.flags")
            )
        )
        inventory.setItem(
            tpaIndex,
            createItem(
                Material.MINECART,
                message.stringMessageToComponentNoPrefix("GUI", "plot.material_name.teleport")
            )
        )
        inventory.setItem(
            renameIndex,
            createItem(
                Material.NAME_TAG,
                message.stringMessageToComponentNoPrefix("GUI", "plot.material_name.rename")
            )
        )
        inventory.setItem(
            listIndex,
            createItem(
                Material.GRASS_BLOCK,
                message.stringMessageToComponentNoPrefix("GUI", "plot.material_name.list")
            )
        )
        inventory.setItem(
            expandIndex,
            createItem(
                Material.DIAMOND_PICKAXE,
                message.stringMessageToComponentNoPrefix("GUI", "plot.material_name.expand")
            )
        )

        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        if (event.clickedInventory !== inventory) return
        val player = event.whoClicked as? Player ?: return

        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()
        val pd = if (plot != null) plugin.databaseHandler.getPlotById(plot.id) else plugin.databaseHandler.getPlotAtLocation(
            player.location.world!!.name,
            player.location.blockX,
            player.location.blockZ
        )

        if (pd == null || !PlotAccess(plugin).canOpen(player, pd)) {
            player.sendMessage(message.stringMessageToComponent("error", "no_permission"))
            return
        }

        when (event.slot) {
            membersIndex -> plugin.guiHandler.registerGui(player, MembersGUI(plugin, pd.id))
            flagsIndex -> {
                plugin.guiHandler.registerGui(player, FlagsGUI(plugin, pd))
            }

            tpaIndex -> {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        val success = SafeTeleportUtil.safeTeleport(player, pd)
                        if (success) {
                            player.sendMessage(message.stringMessageToComponent("plots", "teleport"))
                        } else {
                            player.sendMessage(message.stringMessageToComponent("plots", "teleport_failed"))
                        }
                    })
            }

            renameIndex -> {
                if (!PlotAccess(plugin).allowed(player, pd, "rename")) {
                    player.sendMessage(message.stringMessageToComponent("error", "no_permission"))
                    return
                }
                plugin.renamePlotChatListener.startRenameProcess(player, pd.id)
            }

            listIndex -> {
                val playerPlots = plugin.databaseHandler.getPlotsFromAllUsers().filter { PlotAccess(plugin).canOpen(player, it) }
                if (playerPlots.isNotEmpty()) {
                    plugin.guiHandler.registerGui(player, PlotListGUI(plugin, playerPlots, ownerUuid, pd))
                } else {
                    player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_plot_found"))
                    player.closeInventory()
                }
            }

            expandIndex -> {
                if (!PermissionChecker.canExpandPlot(player)) {
                    player.sendMessage(message.stringMessageToComponent("error", "no_permission"))
                } else if (pd.ownerUuid != plugin.uuidManager.getUUID(player.name)) {
                    player.sendMessage(message.stringMessageToComponent("error", "not_owner"))
                } else {
                    plugin.guiHandler.registerGui(player, ExpandGUI(plugin, pd.id))
                }
            }

            plotIndex -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    helpers.visualizePlotBorder3D(
                        player = player,
                        centerX = pd.x,
                        centerZ = pd.z,
                        radius = pd.radius,
                        durationSec = 20,
                        stepXZ = 2,
                        stepY = 4
                    )
                })
            }
        }
    }

    private fun createItem(material: Material, name: Component): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.displayName(name)
        item.itemMeta = meta
        return item
    }

    private fun createPlotHead(player: Player, plot: PlotData): ItemStack {
        val skull = ItemStack(Material.PLAYER_HEAD)
        val meta = skull.itemMeta as SkullMeta

        meta.owningPlayer = player
        meta.displayName(
            message.stringMessageToComponentNoPrefix(
                "GUI", "plot.material_name.info",
                mapOf("plot" to plot.name)
            )
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
        val creationTime = dateFormat.format(Date(plot.creationTime))
        plugin.logger.debug("plot.ownerUuid.toString() to ${plot.ownerUuid}")
        val owner = plugin.uuidManager.getPlayerName(plot.ownerUuid)
            ?: plot.ownerUuid.toString()
        val lore = listOf(
            message.stringMessageToComponentNoPrefix("GUI", "plot.info.owner", mapOf("owner" to owner)),
            message.stringMessageToComponentNoPrefix("GUI", "plot.info.plot_name", mapOf("plot" to plot.name)),
            message.stringMessageToComponentNoPrefix("GUI", "plot.info.id", mapOf("id" to plot.id.toString())),
            message.stringMessageToComponentNoPrefix(
                "GUI",
                "plot.info.creation_time",
                mapOf("time" to creationTime)
            ),
            Component.text(" "),
            message.stringMessageToComponentNoPrefix("GUI", "plot.info.click")
        )
        meta.lore(lore)
        skull.itemMeta = meta
        return skull
    }
}
