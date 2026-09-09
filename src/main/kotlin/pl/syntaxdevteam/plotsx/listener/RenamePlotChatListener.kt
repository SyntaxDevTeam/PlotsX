package pl.syntaxdevteam.plotsx.listener

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.interaction.RenamePlotService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RenamePlotChatListener(private val plugin: PlotsX) : Listener {
    private data class Pending(val plotId: Int, val token: UUID = UUID.randomUUID())
    private val waiting = ConcurrentHashMap<UUID, Pending>()

    fun startRenameProcess(player: Player, plotId: Int) {
        val pending = Pending(plotId)
        waiting[player.uniqueId] = pending
        player.scheduler.runDelayed(plugin, {
            if (waiting.remove(player.uniqueId, pending))
                player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_timeout"))
        }, { waiting.remove(player.uniqueId, pending) }, 1200L)
        player.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "rename_hint"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val pending = waiting.remove(player.uniqueId) ?: return
        event.isCancelled = true
        val input = PlainTextComponentSerializer.plainText().serialize(event.originalMessage())
        player.scheduler.run(plugin, {
            RenamePlotService(plugin).rename(player, pending.plotId, input)?.let(player::sendMessage)
        }, null)
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) { waiting.remove(event.player.uniqueId) }
    fun cancel(player: Player) { waiting.remove(player.uniqueId) }
    fun close() { waiting.clear() }
}
