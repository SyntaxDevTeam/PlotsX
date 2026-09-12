package pl.syntaxdevteam.plotsx.databases

import org.bukkit.Particle
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

class Helpers(private var plugin: PlotsX) {
    fun visualizePlotBorder3D(player: Player, plot: PlotData, durationSec: Int = 10, stepXZ: Int = 1, stepY: Int = 4) {
        if (player.world.name != plot.world) return
        plot.segments.forEach { visualizePlotBorder3D(player, it.x, it.z, it.radius, durationSec, stepXZ, stepY, plot) }
    }

    /**
     * Pokazuje granice działki w postaci cząsteczek END_ROD
     * tylko dla danego gracza i tylko przez określony czas.
     */
    fun visualizePlotBorder3D(
        player: Player,
        centerX: Int, centerZ: Int,
        radius: Int,
        durationSec: Int = 10,
        stepXZ: Int = 1,
        stepY: Int = 4,
        shape: PlotData? = null
    ) {
        val world = player.world
        val minX = centerX - radius
        val maxX = centerX + radius
        val minZ = centerZ - radius
        val maxZ = centerZ + radius
        val minY = world.minHeight
        val maxY = world.maxHeight - 1

        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            fun drawHorizontalAt(y: Int) {
                for (x in minX..maxX step stepXZ) {
                    listOf(minZ, maxZ).forEach { z ->
                        if (shape == null || (if (z == minZ) !shape.contains(x, z - 1) else !shape.contains(x, z + 1))) player.spawnParticle(
                            Particle.END_ROD,
                            x + .5, y + .5, z + .5,
                            1, 0.0, 0.0, 0.0, 0.0, null, true
                        )
                    }
                }
                for (z in minZ..maxZ step stepXZ) {
                    listOf(minX, maxX).forEach { x ->
                        if (shape == null || (if (x == minX) !shape.contains(x - 1, z) else !shape.contains(x + 1, z))) player.spawnParticle(
                            Particle.END_ROD,
                            x + .5, y + .5, z + .5,
                            1, 0.0, 0.0, 0.0, 0.0, null, true
                        )
                    }
                }
            }

            val groundY = world.getHighestBlockYAt(centerX, centerZ) + 1
            drawHorizontalAt(groundY)

            for (y in minY..maxY step stepY) {
                drawHorizontalAt(y)

            }
        }, 0L, 2L)

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            task.cancel()
            player.sendRichMessage("[DEBUG] " + plugin.messageHandler.getPrefix() + " <gold>3D‐wizualizacja granic zakończona.")
        }, durationSec * 20L)
    }
}