package pl.syntaxdevteam.plotsx.databases

import org.bukkit.Particle
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

class Helpers(private var plugin: PlotsX) {
    /**
     * Pokazuje granice działki w postaci cząsteczek END_ROD
     * tylko dla danego gracza i tylko przez określony czas.
     */
    fun visualizePlotBorder3D(
        player: Player,
        centerX: Int,
        centerZ: Int,
        radius: Int,
        durationSec: Int = 10,
        stepXZ: Int = 2,
        stepY: Int = 8
    ) {
        val world = player.world
        val minX = centerX - radius
        val maxX = centerX + radius
        val minZ = centerZ - radius
        val maxZ = centerZ + radius
        val minY = world.minHeight          // np. -64
        val maxY = world.maxHeight - 1      // np. 319

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            // 1) poziome krawędzie na ziemi
            for (x in minX..maxX step stepXZ) {
                listOf(minZ, maxZ).forEach { z ->
                    val y = world.getHighestBlockYAt(x, z) + 1
                    player.spawnParticle(Particle.END_ROD, x + .5, y.toDouble(), z + .5,
                        1, 0.0,0.0,0.0, 0.0, null, true)
                }
            }
            for (z in minZ..maxZ step stepXZ) {
                listOf(minX, maxX).forEach { x ->
                    val y = world.getHighestBlockYAt(x, z) + 1
                    player.spawnParticle(Particle.END_ROD, x + .5, y.toDouble(), z + .5,
                        1, 0.0,0.0,0.0, 0.0, null, true)
                }
            }

            // 2) pionowe słupy cząsteczek w narożnikach
            listOf(
                Pair(minX, minZ),
                Pair(minX, maxZ),
                Pair(maxX, minZ),
                Pair(maxX, maxZ)
            ).forEach { (x, z) ->
                for (y in minY..maxY step stepY) {
                    player.spawnParticle(Particle.END_ROD, x + .5, y + .5, z + .5,
                        1, 0.0,0.0,0.0, 0.0, null, true)
                }
            }
        }, 0L, 2L)

        // anuluj po durationSec sekundach
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            task.cancel()
            player.sendMessage("§e3D–Wizualizacja granic zakończona.")
        }, durationSec * 20L)
    }
}