package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData

class FlagsGUI(
    private val plugin: PlotsX,
    private val plot: PlotData
) : GUI {

    private val message = plugin.messageHandler
    private val flagMaterials = mapOf( // przypisane ikony
        "build" to Material.STONE,
        "pvp" to Material.IRON_SWORD,
        "chest" to Material.CHEST,
        "ender-chest" to Material.ENDER_CHEST,
        "lever" to Material.LEVER,
        "button" to Material.STONE_BUTTON,
        "door" to Material.OAK_DOOR,
        "spawn-monsters" to Material.SKELETON_SPAWN_EGG,
        "spawn-animals" to Material.SHEEP_SPAWN_EGG,
        "passives" to Material.RABBIT_SPAWN_EGG,
        "flow" to Material.WATER_BUCKET,
        "fire" to Material.FLINT_AND_STEEL,
        "minecart" to Material.MINECART,
        "allow-home" to Material.COMPASS,
        "smart-door" to Material.REDSTONE_TORCH,
        "use-potions" to Material.POTION,
        "mob-loot" to Material.ROTTEN_FLESH,
        "flow-damage" to Material.LAVA_BUCKET,
        "iceform-player" to Material.SNOWBALL,
        "iceform-world" to Material.ICE,
        "allow-fly" to Material.ELYTRA,
        "teleport" to Material.ENDER_PEARL,
        "can-grow" to Material.BONE_MEAL,
        "allow-spawners" to Material.SPAWNER,
        "leaves-decay" to Material.OAK_LEAVES,
        "allow-effects" to Material.GLOWSTONE_DUST,
        "redstone" to Material.REDSTONE,
        "block-transform" to Material.MOSS_BLOCK,
        "team" to Material.NAME_TAG
    )

    override fun open(player: Player) {
        val inventory = Bukkit.createInventory(null, 36, getTitle())

        val flags = plugin.databaseHandler.getPlotFlags(plot.id)

        flags.entries.forEachIndexed { index, (name, value) ->
            val icon = flagMaterials[name] ?: Material.BARRIER
            val title = "<green>${name.replace("-", " ").replaceFirstChar { it.uppercaseChar() }}"
            val desc = message.getCleanMessage("flags", name)
            val item = ItemStack(icon)
            val meta = item.itemMeta

            meta.displayName(message.formatMixedTextToMiniMessage(title, TagResolver.empty()))
            meta.lore(
                listOf(
                    // TODO: Zmienić na formatowanie z message.yml
                    message.formatMixedTextToMiniMessage("<white>$desc", TagResolver.empty()),
                    message.formatMixedTextToMiniMessage("<yellow>Status: ${if (value) "<green>ON" else "<red>OFF"}", TagResolver.empty())
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

        val slot = event.slot
        val item = event.currentItem ?: return
        val meta = item.itemMeta ?: return
        val displayName = meta.displayName()?.toString() ?: return

        val flagName = extractFlagName(displayName) ?: return

        val current = plugin.databaseHandler.getPlotFlag(plot.id, flagName)?.value?.toBooleanStrictOrNull() ?: return
        val updated = !current

        if (plugin.databaseHandler.updatePlotFlag(plot.id, flagName, updated)) {
            player.sendMessage(message.getMessage("flags", "toggle", mapOf("flag" to flagName, "value" to updated.toString())))
        } else {
            player.sendMessage(message.getMessage("error", "flag_update_failed"))
        }

        open(player) // odśwież GUI
    }

    override fun getTitle(): Component {
        return message.getLogMessage("GUI", "flags.title")
    }

    private fun extractFlagName(displayName: String): String? {
        return flagMaterials.keys.firstOrNull { key ->
            displayName.contains(key.replace("-", " "), ignoreCase = true)
        }
    }
}
