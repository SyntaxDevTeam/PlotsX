package pl.syntaxdevteam.plotsx.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.EventPriority
import org.bukkit.scheduler.BukkitTask
import pl.syntaxdevteam.plotsx.PlotsX
import java.util.*

class RenamePlotChatListener(private val plugin: PlotsX) : Listener {

    private val waiting = mutableMapOf<UUID, Pair<Int, BukkitTask>>()
    private val timeoutSeconds = 60

    fun startRenameProcess(player: Player, plotId: Int) {
        waiting[player.uniqueId]?.second?.cancel()
        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            waiting.remove(player.uniqueId)
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_timeout"))
        }, timeoutSeconds * 20L)
        waiting[player.uniqueId] = plotId to task
        player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_hint"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val entry = waiting.remove(player.uniqueId) ?: return
        event.isCancelled = true
        entry.second.cancel()
        val plotId = entry.first
        val rawName = PlainTextComponentSerializer.plainText().serialize(event.originalMessage()).trim()
        val newName = plugin.hookHandler.censorWithCleanerX(rawName)

        Bukkit.getScheduler().runTask(plugin, Runnable {
            val plot = plugin.databaseHandler.getPlotById(plotId)
            if (plot == null) {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_not_found"))
                return@Runnable
            }
            if (!pl.syntaxdevteam.plotsx.permissions.PlotAccess(plugin).allowed(player, plot, "rename")) {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
                return@Runnable
            }
            if (plugin.databaseHandler.getPlotByName(newName, plot.ownerUuid) != null) {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_exists"))
                return@Runnable
            }
            val success = plugin.databaseHandler.updatePlotDetails(plot.id, newName)
            if (success) {
                plugin.cacheManager.updatePlotCacheAsync(plot.id)
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_success", mapOf("name" to newName)))
            } else {
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_fail"))
            }
        })
    }
}
