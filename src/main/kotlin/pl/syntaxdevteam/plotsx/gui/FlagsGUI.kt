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
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry

class FlagsGUI(
    private val plugin: PlotsX,
    private val plot: PlotData
) : AbstractGUI(
    title = plugin.messageHandler.getLogMessage("GUI", "flags.title"),
    size  = 54
) {

    private val message = plugin.messageHandler
    private val keyFlag = NamespacedKey(plugin, "plot_flag_key")

    private val flagMaterials = mapOf(
        "build"           to Material.STONE,
        "pvp"             to Material.WOODEN_SWORD,
        "chest"           to Material.CHEST,
        "ender-chest"     to Material.ENDER_CHEST,
        "lever"           to Material.LEVER,
        "button"          to Material.STONE_BUTTON,
        "door"            to Material.BAMBOO_DOOR,
        "smart-door"      to Material.IRON_DOOR,
        "spawn-monsters"  to Material.CARVED_PUMPKIN,
        "spawn-animals"   to Material.EGG,
        "passives"        to Material.SADDLE,
        "flow"            to Material.WATER_BUCKET,
        "flow-damage"     to Material.LAVA_BUCKET,
        "fire"            to Material.FLINT_AND_STEEL,
        "minecart"        to Material.MINECART,
        "allow-home"      to Material.COMPASS,
        "use-potions"     to Material.EXPERIENCE_BOTTLE,
        "mob-loot"        to Material.MYCELIUM,
        "iceform-player"  to Material.SNOWBALL,
        "iceform-world"   to Material.ICE,
        "allow-fly"       to Material.ELYTRA,
        "teleport"        to Material.ENDER_PEARL,
        "can-grow"        to Material.WHEAT,
        "allow-spawners"  to Material.SPAWNER,
        "leaves-decay"    to Material.OAK_LEAVES,
        "allow-effects"   to Material.BEACON,
        "redstone"        to Material.REDSTONE,
        "utility"         to Material.FURNACE,
        "block-transform" to Material.MOSS_BLOCK,
        "team"            to Material.NAME_TAG
    )

    override fun open(player: Player) {
        inventory.clear()

        val flags = plugin.cacheManager.getFlags(plot.id) ?: plugin.databaseHandler.getPlotFlags(plot.id)

        flagMaterials.entries.forEachIndexed { idx, (flagKey, material) ->
            if (idx >= inventory.size) return@forEachIndexed

            val current = flags[flagKey] ?: false

            // Teksty
            val name    = message.getCleanMessage("flags", "$flagKey.name")
            val desc    = message.getCleanMessage("flags", "$flagKey.description")
            val descTl  = message.getCleanMessage("flags", "desc_title")
            val valTl   = message.getCleanMessage("flags", "value_title")
            val valStr  = if (current)
                "<green><bold>✔</bold> ${message.getCleanMessage("flags", "value_true")}"
            else
                "<red><bold>✘</bold> ${message.getCleanMessage("flags", "value_false")}"

            // Item
            val item = ItemStack(material)
            val meta = item.itemMeta!!
            meta.persistentDataContainer.set(keyFlag, PersistentDataType.STRING, flagKey)
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
            ?: return

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val current = plugin.databaseHandler
                .getPlotFlag(plot.id, flagKey)
                ?.value
                ?.toBooleanStrictOrNull() ?: false
            val updated = !current

            if (plugin.databaseHandler.updatePlotFlag(plot.id, flagKey, updated)) {
                player.sendMessage(message.getMessage("flags", "toggle", mapOf(
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
                            val cacheFlags = plugin.cacheManager.getFlags(plot.id) ?: emptyMap()
                            val dbFlags = plugin.databaseHandler.getPlotFlags(plot.id)

                            plugin.logger.debug("Flagi dla działki ID=${plot.id}")
                            plugin.logger.debug(" → Z cache: ${cacheFlags.entries.joinToString()}")
                            plugin.logger.debug(" → Z bazy: ${dbFlags.entries.joinToString()}")
                        }
                    })
                }
            } else {
                player.sendMessage(message.getMessage("error", "flag_update_failed"))
            }
        })
    }
}
