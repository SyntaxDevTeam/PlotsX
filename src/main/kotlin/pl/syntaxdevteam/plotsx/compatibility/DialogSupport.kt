package pl.syntaxdevteam.plotsx.compatibility

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

/** Checks dialog availability without loading classes that reference the newer Paper Dialog API. */
object DialogSupport {
    fun canUseDialogs(plugin: PlotsX): Boolean =
        plugin.versionCompatibility.supports(VersionCompatibility.CompatibilityFlag.DIALOGS) &&
            !plugin.config.getString("interactions.mode", "auto").equals("legacy", true) &&
            !plugin.config.getBoolean("interactions.translated-clients", false)

    fun canUseListDialogs(plugin: PlotsX, sender: CommandSender): Boolean =
        sender is Player &&
            plugin.config.getBoolean("dialogs.use-list-views", true) &&
            canUseDialogs(plugin)
}
