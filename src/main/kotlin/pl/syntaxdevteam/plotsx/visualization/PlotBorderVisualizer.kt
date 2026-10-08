package pl.syntaxdevteam.plotsx.visualization

import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns the single border-display session allowed for each player. */
class PlotBorderVisualizer(private val plugin: PlotsX) : Listener {
    private data class Session(val task: ScheduledTask)

    private companion object {
        // This API call creates a client-bound particle packet. Bound cosmetic traffic for
        // long or fragmented plots independently of their total perimeter.
        const val MAX_POINTS_PER_SEND = 512
    }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun show(player: Player, plot: PlotData, durationSeconds: Int, spacing: Int = 2) {
        val preparationStarted = System.nanoTime()
        cancel(player.uniqueId)
        if (player.world.name != plot.world) return
        val world = player.world
        val horizontal = BorderOutline.create(plot, spacing.coerceAtLeast(1))
        // A height lookup may synchronously generate/load a chunk. Border particles must never
        // turn a claim into a mass chunk-load operation; unloaded parts become visible naturally
        // when the player asks to display the border again from that area.
        val loaded = horizontal.asSequence()
            .filter { point -> world.isChunkLoaded(blockToChunk(point.x), blockToChunk(point.z)) &&
                org.bukkit.Bukkit.isOwnedByCurrentRegion(world, blockToChunk(point.x), blockToChunk(point.z)) }
            .toList()
        val sampled = evenlySample(loaded, MAX_POINTS_PER_SEND)
        val points = sampled.asSequence()
            .map { point -> Triple(point.x + .5, world.getHighestBlockYAt(point.x, point.z) + 1.25, point.z + .5) }
            .toList()
        if (points.isEmpty()) return
        var sendsRemaining = durationSeconds.coerceAtLeast(1)
        var particlesSent = 0L
        lateinit var task: ScheduledTask
        task = player.scheduler.runAtFixedRate(plugin, { scheduled ->
            if (!player.isOnline || player.world.name != plot.world) {
                removeIfCurrent(player.uniqueId, scheduled)
                return@runAtFixedRate
            }
            points.forEach { (x, y, z) -> player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, null, true) }
            particlesSent += points.size
            sendsRemaining--
            if (sendsRemaining == 0 && removeIfCurrent(player.uniqueId, scheduled)) {
                plugin.logger.debug(
                    "PlotsX border metrics: player=${player.name}, particles=$particlesSent, " +
                        "particles/s=${points.size}, active sessions=${sessions.size}"
                )
            }
        }, { sessions.remove(player.uniqueId) }, 1L, 20L) ?: return
        sessions[player.uniqueId] = Session(task)
        plugin.logger.debug(
            "PlotsX expansion timings: border visualization preparation=" +
                "${"%.3f".format(java.util.Locale.ROOT, (System.nanoTime() - preparationStarted) / 1_000_000.0)} ms, " +
                "points=${points.size}, skipped-unavailable=${horizontal.size - loaded.size}, " +
                "sampled-out=${loaded.size - sampled.size}, active sessions=${sessions.size}"
        )
    }

    private fun <T> evenlySample(points: List<T>, limit: Int): List<T> {
        if (points.size <= limit) return points
        return List(limit) { index -> points[(index.toLong() * points.size / limit).toInt()] }
    }

    private fun removeIfCurrent(playerId: UUID, task: ScheduledTask): Boolean {
        val current = sessions[playerId] ?: return false
        if (current.task !== task || !sessions.remove(playerId, current)) return false
        task.cancel()
        return true
    }

    fun cancel(playerId: UUID) { sessions.remove(playerId)?.task?.cancel() }
    fun close() { sessions.values.forEach { it.task.cancel() }; sessions.clear() }
    fun activeSessions(): Int = sessions.size

    private fun blockToChunk(block: Int): Int = Math.floorDiv(block, 16)

    @EventHandler fun onQuit(event: PlayerQuitEvent) = cancel(event.player.uniqueId)

}
