package pl.syntaxdevteam.plotsx.interaction

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.permissions.PlotAccess

class RenamePlotService(private val plugin: PlotsX) {
    /**
     * Validates against the atomic cache on the server thread and persists the rename on a worker.
     * Completion is always invoked back on the player's scheduler/server thread.
     */
    fun rename(player: Player, plotId: Int, input: String, completion: (Component?) -> Unit) {
        fun error(key: String) = plugin.messageHandler.stringMessageToComponent("plots", key)
        val plot = plugin.cacheManager.getPlot(plotId) ?: run { completion(error("rename_not_found")); return }
        if (!PlotAccess(plugin).allowed(player, plot, "rename")) {
            completion(plugin.messageHandler.stringMessageToComponent("error", "no_permission")); return
        }
        val name = input.trim()
        if (name.isBlank() || name.length > 255 || name.any { it.isISOControl() }) {
            completion(error("rename_rejected")); return
        }
        val allowed = plugin.hookHandler.isPlotNameAllowed(name)
        if (allowed != true) {
            completion(error(if (allowed == null) "rename_filter_unavailable" else "rename_rejected")); return
        }
        val existing = plugin.cacheManager.getCachedPlots()
            .firstOrNull { it.ownerUuid == plot.ownerUuid && it.name.equals(name, true) }
        if (existing != null && existing.id != plot.id) { completion(error("rename_exists")); return }
        if (name == plot.name) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_success", mapOf("name" to name)))
            completion(null)
            return
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val success = try { plugin.databaseHandler.updatePlotDetails(plot.id, name) }
            catch (failure: Exception) {
                plugin.logger.err("Rename failed for plot ${plot.id}: ${failure.message}")
                false
            }
            if (!plugin.isEnabled) return@Runnable
            player.scheduler.run(plugin, {
                if (!player.isOnline) return@run
                if (success) {
                    player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                        "plots", "rename_success", mapOf("name" to name)
                    ))
                    completion(null)
                } else completion(error("rename_fail"))
            }, null)
        })
    }
}
