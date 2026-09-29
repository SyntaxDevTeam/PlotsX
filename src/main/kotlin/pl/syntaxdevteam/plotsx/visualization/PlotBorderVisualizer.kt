package pl.syntaxdevteam.plotsx.visualization

import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitTask
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns the single border-display session allowed for each player. */
class PlotBorderVisualizer(private val plugin: PlotsX) : Listener {
    private val sessions = ConcurrentHashMap<UUID, BukkitTask>()

    fun show(player: Player, plot: PlotData, durationSeconds: Int, spacing: Int = 2) {
        val preparationStarted = System.nanoTime()
        cancel(player.uniqueId)
        if (player.world.name != plot.world) return
        val horizontal = BorderOutline.create(plot, spacing.coerceAtLeast(1))
        // Height lookups are intentionally completed once, before the repeating sender starts.
        val points = horizontal.map { point -> Triple(point.x + .5, player.world.getHighestBlockYAt(point.x, point.z) + 1.25, point.z + .5) }
        if (points.isEmpty()) return
        var sendsRemaining = durationSeconds.coerceAtLeast(1)
        var particlesSent = 0L
        lateinit var task: BukkitTask
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (!player.isOnline || player.world.name != plot.world) {
                cancel(player.uniqueId)
                return@Runnable
            }
            points.forEach { (x, y, z) -> player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, null, true) }
            particlesSent += points.size
            sendsRemaining--
            if (sendsRemaining == 0 && sessions.remove(player.uniqueId, task)) {
                task.cancel()
                plugin.logger.debug(
                    "PlotsX border metrics: player=${player.name}, particles=$particlesSent, " +
                        "particles/s=${points.size}, active sessions=${sessions.size}"
                )
            }
        }, 0L, 20L)
        sessions[player.uniqueId] = task
        plugin.logger.debug(
            "PlotsX expansion timings: border visualization preparation=" +
                "${"%.3f".format(java.util.Locale.ROOT, (System.nanoTime() - preparationStarted) / 1_000_000.0)} ms, " +
                "points=${points.size}, active sessions=${sessions.size}"
        )
    }

    fun cancel(playerId: UUID) { sessions.remove(playerId)?.cancel() }
    fun close() { sessions.values.forEach(BukkitTask::cancel); sessions.clear() }
    fun activeSessions(): Int = sessions.size

    @EventHandler fun onQuit(event: PlayerQuitEvent) = cancel(event.player.uniqueId)

}
