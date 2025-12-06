package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry
import pl.syntaxdevteam.plotsx.protection.PlotFlagRegistry

class FlagsGUI(
    private val plugin: PlotsX,
    private val plot: PlotData
) : AbstractGUI(
    title = plugin.messageHandler.stringMessageToComponentNoPrefix("GUI", "flags.title"),
    size  = 54
) {

    private val message = plugin.messageHandler
    private val keyFlag = NamespacedKey(plugin, "plot_flag_key")

    override fun open(player: Player) {
        inventory.clear()

        val flags = plugin.cacheManager.getFlags(plot.id) ?: plugin.databaseHandler.getPlotFlags(plot.id)

        PlotFlagRegistry.allFlags.values.forEachIndexed { idx: Int, flagMeta ->
            if (idx >= inventory.size) return@forEachIndexed

            val flagData = flags.firstOrNull { it.name == flagMeta.name }
            val current = flagData?.value?.toBooleanStrictOrNull() ?: flagMeta.defaultValue

            val name    = message.stringMessageToStringNoPrefix("flags", flagMeta.displayKey)
            val desc    = message.stringMessageToStringNoPrefix("flags", flagMeta.descriptionKey)
            val descTl  = message.stringMessageToStringNoPrefix("flags", "desc_title")
            val valTl   = message.stringMessageToStringNoPrefix("flags", "value_title")
            val valStr  = if (current)
                "<green><bold>✔</bold> ${message.stringMessageToStringNoPrefix("flags", "value_true")}"
            else
                "<red><bold>✘</bold> ${message.stringMessageToStringNoPrefix("flags", "value_false")}"

            // Item
            val item = ItemStack(flagMeta.material)
            val meta = item.itemMeta!!
            meta.persistentDataContainer.set(keyFlag, PersistentDataType.STRING, flagMeta.name)
            meta.displayName(
                message.formatMixedTextToMiniMessage("<gold>=> <bold>$name</bold> <=", TagResolver.empty())
            )
            meta.lore(listOf(
                message.formatMixedTextToMiniMessage("<aqua>     $valTl: $valStr", TagResolver.empty()),
                Component.empty(),
                message.formatMixedTextToMiniMessage("<aqua>$descTl<gray>$desc", TagResolver.empty())
            ))
            item.itemMeta = meta

            inventory.setItem(idx, item)
        }
        inventory.setItem(49, plugin.guiHandler.createItem(Material.BARRIER, message.stringMessageToStringNoPrefix("GUI", "flags.back")))

        super.open(player)
    }

    override fun handleClick(event: InventoryClickEvent) {
        if (!isThisInventory(event.inventory)) return

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val clicked = event.currentItem ?: return
        val meta    = clicked.itemMeta ?: return

        plugin.guiHandler.unregisterGui(player)
        player.closeInventory()

        val flagKey = meta.persistentDataContainer
            .get(keyFlag, PersistentDataType.STRING)

        // Obsługa przycisku "back" (slot 49)
        if (event.slot == 49) {
            plugin.guiHandler.registerGui(player, PlotGUI(plugin, plot, plot.ownerUuid))
            return
        }

        if (flagKey == null) return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val current = plugin.databaseHandler
                .getPlotFlag(plot.id, flagKey)
                ?.value
                ?.toBooleanStrictOrNull() ?: false
            val updated = !current

            if (plugin.databaseHandler.updatePlotFlag(plot.id, flagKey, updated)) {
                player.sendMessage(message.stringMessageToComponent("flags", "toggle", mapOf(
                    "flag" to flagKey,
                    "value" to updated.toString()
                )))

                plugin.cacheManager.updateFlagCacheAsync(plot.id) {
                    plugin.server.scheduler.runTask(plugin, Runnable {
                        plugin.guiHandler.registerGui(player, FlagsGUI(plugin, plot))

                        plugin.databaseHandler.logPlotAction(
                            PlotLogEntry(
                                plotId = plot.id,
                                action = "UpdateFlag: $flagKey: $updated",
                                actorUUID = player.uniqueId,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        if(plugin.config.getBoolean("debug", false)){
                            val cacheFlags = plugin.cacheManager.getFlags(plot.id) ?: emptyList()
                            val dbFlags = plugin.databaseHandler.getPlotFlags(plot.id)
                            val table = formatFlagComparison(cacheFlags, dbFlags)
                            plugin.logger.debug("Flagi dla działki ID=${plot.id}\n$table")
                        }
                    })
                }
            } else {
                player.sendMessage(message.stringMessageToComponent("error", "flag_update_failed"))
            }
        })
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
