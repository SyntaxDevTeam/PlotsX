package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData

class FlagsGUI(
    private val plugin: PlotsX,
    private val plot: PlotData
) : GUI {

    private val message = plugin.messageHandler
    private val keyFlag = NamespacedKey(plugin, "plot_flag_key")

    private val flagMaterials = mapOf(
        "build"          to Material.STONE,
        "pvp"            to Material.WOODEN_SWORD,
        "chest"          to Material.CHEST,
        "ender-chest"    to Material.ENDER_CHEST,
        "lever"          to Material.LEVER,
        "button"         to Material.STONE_BUTTON,
        "door"           to Material.BAMBOO_DOOR,
        "smart-door"     to Material.IRON_DOOR,
        "spawn-monsters" to Material.CARVED_PUMPKIN,
        "spawn-animals"  to Material.EGG,
        "passives"       to Material.SADDLE,
        "flow"           to Material.WATER_BUCKET,
        "flow-damage"    to Material.LAVA_BUCKET,
        "fire"           to Material.FLINT_AND_STEEL,
        "minecart"       to Material.MINECART,
        "allow-home"     to Material.COMPASS,
        "use-potions"    to Material.SPLASH_POTION,
        "mob-loot"       to Material.MYCELIUM,
        "iceform-player" to Material.SNOWBALL,
        "iceform-world"  to Material.ICE,
        "allow-fly"      to Material.ELYTRA,
        "teleport"       to Material.ENDER_PEARL,
        "can-grow"       to Material.WHEAT,
        "allow-spawners" to Material.SPAWNER,
        "leaves-decay"   to Material.OAK_LEAVES,
        "allow-effects"  to Material.BEACON,
        "redstone"       to Material.REDSTONE,
        "block-transform" to Material.MOSS_BLOCK,
        "team"           to Material.NAME_TAG
    )

    override fun open(player: Player) {
        val size = ((flagMaterials.size + 8) / 9) * 9
        val inventory = Bukkit.createInventory(null, size.coerceAtMost(54), getTitle())

        val flags = plugin.databaseHandler.getPlotFlags(plot.id)

        flagMaterials.entries.forEachIndexed { index, (flagKey, material) ->
            val value = flags[flagKey] ?: false

            val displayName = message.getCleanMessage("flags", "$flagKey.name")
            val descTitle = message.getCleanMessage("flags", "desc_title")
            val description = message.getCleanMessage("flags", "$flagKey.description")
            val valueTitle = message.getCleanMessage("flags", "value_title")
            val valueStr = if (value)
                "<green><bold>✔</bold> ${message.getCleanMessage("flags", "value_true")}"
            else
                "<red><bold>✘</bold> ${message.getCleanMessage("flags", "value_false")}"

            val item = ItemStack(material)
            val meta = item.itemMeta!!
            val pdc = meta.persistentDataContainer
            pdc.set(keyFlag, PersistentDataType.STRING, flagKey)

            meta.displayName(
                message.formatMixedTextToMiniMessage("<gold>=> <bold>$displayName</bold> <=", TagResolver.empty())
            )

            meta.lore(
                listOf(
                    message.formatMixedTextToMiniMessage("<aqua>     $valueTitle: $valueStr", TagResolver.empty()),
                    Component.empty(),
                    message.formatMixedTextToMiniMessage("<aqua>$descTitle<gray>$description", TagResolver.empty())
                )
            )

            item.itemMeta = meta
            inventory.setItem(index, item)
        }

        plugin.guiHandler.track(player, this)
        player.openInventory(inventory)
    }


    override fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        event.isCancelled = true

        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return

        val flagKey = meta.persistentDataContainer
            .get(keyFlag, PersistentDataType.STRING) ?: return

        val current = plugin.databaseHandler
            .getPlotFlag(plot.id, flagKey)
            ?.value
            ?.toBooleanStrictOrNull() ?: false

        val updated = !current
        val success = plugin.databaseHandler.updatePlotFlag(plot.id, flagKey, updated)

        if (success) {
            player.sendMessage(
                message.getMessage("flags", "toggle", mapOf(
                    "flag" to flagKey,
                    "value" to updated.toString()
                ))
            )
            open(player)
        } else {
            player.sendMessage(message.getMessage("error", "flag_update_failed"))
        }
    }

    override fun getTitle(): Component =
        message.getLogMessage("GUI", "flags.title")
}
