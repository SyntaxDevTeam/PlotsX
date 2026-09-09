package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.protection.FlagMeta

/** Shared presentation for plot flags and the permission to edit those flags. */
internal object SettingItem {
    fun name(plugin: PlotsX, flag: FlagMeta): Component = flag.customDisplayName?.let(Component::text)
        ?: plugin.messageHandler.stringMessageToComponentNoPrefix("flags", flag.displayKey)

    fun description(plugin: PlotsX, flag: FlagMeta): Component = flag.customDescription?.let(Component::text)
        ?: plugin.messageHandler.stringMessageToComponentNoPrefix("flags", flag.descriptionKey)

    fun create(material: Material, name: Component, description: Component, enabled: Boolean,
               value: Component,
               extra: List<Component> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.displayName(name.colorIfAbsent(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore((listOf(
            Component.text(if (enabled) "✔ " else "✘ ",
                    if (enabled) NamedTextColor.GREEN else NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(value.color(if (enabled) NamedTextColor.GREEN else NamedTextColor.RED)),
            Component.empty(),
            description.color(NamedTextColor.GRAY)
        ) + extra).map { it.decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        return item
    }
}
