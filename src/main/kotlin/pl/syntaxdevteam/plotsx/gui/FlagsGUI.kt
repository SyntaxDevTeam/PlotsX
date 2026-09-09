package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry

class FlagsGUI(
    private val plugin: PlotsX,
    private val plot: PlotData,
    private val page: Int = 0
) : AbstractGUI(
    title = plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "flags.title"),
    size = 54
) {

    private val message = plugin.messageHandler
    private val keyFlag = NamespacedKey(plugin, "plot_flag_key")

    override fun open(player: Player) {
        val currentPlot = plugin.databaseHandler.getPlotById(plot.id) ?: return
        val access = pl.syntaxdevteam.plotsx.permissions.PlotAccess(plugin)
        if (!access.canOpen(player, currentPlot)) return
        val allowedActions = access.allowedActions(player, currentPlot)
        inventory.clear()

        val flags = plugin.cacheManager.getFlags(plot.id) ?: plugin.databaseHandler.getPlotFlags(plot.id)

        PlotFlagRegistry.allFlags.values.drop(page * 45).take(45).forEachIndexed { idx: Int, flagMeta ->

            val flagData = flags.firstOrNull { it.name == flagMeta.name }
            val current = flagData?.value?.toBooleanStrictOrNull() ?: flagMeta.defaultValue

            val item = SettingItem.create(flagMeta.material, SettingItem.name(plugin, flagMeta),
                SettingItem.description(plugin, flagMeta), current,
                message.stringMessageToComponentNoPrefix("flags", "value_title"),
                message.stringMessageToComponentNoPrefix("flags", if (current) "value_true" else "value_false"),
                message.stringMessageToComponentNoPrefix("flags", "desc_title"),
                if ("flag.${flagMeta.name}" !in allowedActions)
                    listOf(message.stringMessageToComponentNoPrefix("members", "read_only")) else emptyList())
            val meta = item.itemMeta!!
            meta.persistentDataContainer.set(keyFlag, PersistentDataType.STRING, flagMeta.name)
            item.itemMeta = meta
            inventory.setItem(idx, item)
        }
        inventory.setItem(
            49,
            plugin.guiHandler.createItem(
                Material.BARRIER,
                message.stringMessageToComponentNoPrefix("GUI", "flags.back")
            )
        )

        if (page > 0) inventory.setItem(45, plugin.guiHandler.createItem(Material.ARROW,
            message.stringMessageToComponentNoPrefix("members", "previous")))
        if ((page + 1) * 45 < PlotFlagRegistry.allFlags.size) inventory.setItem(53, plugin.guiHandler.createItem(Material.ARROW,
            message.stringMessageToComponentNoPrefix("members", "next")))
        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        if (event.clickedInventory !== inventory) return
        val player = event.whoClicked as? Player ?: return
        val clicked = event.currentItem ?: return
        val meta = clicked.itemMeta ?: return

        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()

        val flagKey = meta.persistentDataContainer
            .get(keyFlag, PersistentDataType.STRING)

        if (event.slot == 45 && page > 0) {
            plugin.guiHandler.registerGui(player, FlagsGUI(plugin, plot, page - 1))
            return
        }
        if (event.slot == 53 && (page + 1) * 45 < PlotFlagRegistry.allFlags.size) {
            plugin.guiHandler.registerGui(player, FlagsGUI(plugin, plot, page + 1))
            return
        }

        if (event.slot == 49) {
            plugin.guiHandler.registerGui(player, PlotGUI(plugin, plot, plot.ownerUuid))
            return
        }

        if (flagKey == null) return

        val currentPlot = plugin.databaseHandler.getPlotById(plot.id) ?: return
        if (!pl.syntaxdevteam.plotsx.permissions.PlotAccess(plugin).allowed(player, currentPlot, "flag.$flagKey")) {
            player.sendMessage(message.stringMessageToComponent("error", "no_permission"))
            return
        }
        val definition = PlotFlagRegistry.allFlags[flagKey] ?: return
        val current = plugin.databaseHandler.getPlotFlag(plot.id, flagKey)?.value?.toBooleanStrictOrNull()
            ?: definition.defaultValue
        val result = plugin.api.setFlag(player, plot.id, flagKey, !current)
        if (result == pl.syntaxdevteam.plotsx.api.FlagUpdateResult.UPDATED ||
            result == pl.syntaxdevteam.plotsx.api.FlagUpdateResult.UNCHANGED) {
            player.sendMessage(message.stringMessageToComponent("flags", "toggle",
                mapOf("flag" to flagKey, "value" to (!current).toString())))
            plugin.guiHandler.registerGui(player, FlagsGUI(plugin, currentPlot, page))
        } else player.sendMessage(message.stringMessageToComponent("error", "flag_update_failed"))
    }

    fun formatFlagComparison(cacheFlags: List<PlotFlagData>, dbFlags: List<PlotFlagData>): String {
        val cacheMap = cacheFlags.associateBy { it.name }
        val dbMap = dbFlags.associateBy { it.name }
        val allFlagNames = (cacheMap.keys + dbMap.keys).toSortedSet()
        val header = String.format("%-18s | %-8s | %-8s | %s", "Flaga", "Cache", "Baza", "OK?")
        val separator = "-".repeat(header.length)
        val rows = allFlagNames.map { name ->
            val cacheVal = cacheMap[name]?.value ?: "-"
            val dbVal = dbMap[name]?.value ?: "-"
            val ok = if (cacheVal == dbVal) "✔" else "✘"
            String.format("%-18s | %-8s | %-8s | %s", name, cacheVal, dbVal, ok)
        }
        return (listOf(header, separator) + rows).joinToString("\n")
    }
}
