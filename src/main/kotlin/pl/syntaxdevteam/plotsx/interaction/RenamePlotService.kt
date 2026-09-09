package pl.syntaxdevteam.plotsx.interaction

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.permissions.PlotAccess

class RenamePlotService(private val plugin: PlotsX) {
    /** Returns a validation error for either UI; success is announced once here. */
    fun rename(player: Player, plotId: Int, input: String): Component? {
        fun error(key: String) = plugin.messageHandler.stringMessageToComponent("plots", key)
        val plot = plugin.databaseHandler.getPlotById(plotId) ?: return error("rename_not_found")
        if (!PlotAccess(plugin).allowed(player, plot, "rename"))
            return plugin.messageHandler.stringMessageToComponent("error", "no_permission")
        val name = input.trim()
        if (name.isBlank() || name.length > 255 || name.any { it.isISOControl() }) return error("rename_rejected")
        val allowed = plugin.hookHandler.isPlotNameAllowed(name)
        if (allowed != true) return error(if (allowed == null) "rename_filter_unavailable" else "rename_rejected")
        val existing = plugin.databaseHandler.getPlotByName(name, plot.ownerUuid)
        if (existing != null && existing.id != plot.id) return error("rename_exists")
        if (name != plot.name && !plugin.databaseHandler.updatePlotDetails(plot.id, name)) return error("rename_fail")
        plugin.cacheManager.updatePlotCacheAsync(plot.id)
        player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_success", mapOf("name" to name)))
        return null
    }
}
