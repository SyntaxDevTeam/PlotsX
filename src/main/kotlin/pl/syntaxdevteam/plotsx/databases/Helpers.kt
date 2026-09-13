package pl.syntaxdevteam.plotsx.databases

import org.bukkit.Particle
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

class Helpers(private var plugin: PlotsX) {
    fun borderDurationSeconds(): Int = plugin.config.getInt("plots.borderDisplaySeconds", 30).coerceAtLeast(1)

    fun visualizePlotBorder3D(player: Player, plot: PlotData, durationSec: Int = 10, stepXZ: Int = 1, stepY: Int = 4) {
        if (player.world.name != plot.world) return
        plot.geometry.regions().forEach { drawBounds(player, it, durationSec, stepXZ, stepY, plot) }
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
        drawBounds(player, pl.syntaxdevteam.plotsx.geometry.BlockBounds(
            centerX.toLong() - radius, centerZ.toLong() - radius,
            centerX.toLong() + radius, centerZ.toLong() + radius), durationSec, stepXZ, stepY, shape)
    }

    private fun drawBounds(player: Player, bounds: pl.syntaxdevteam.plotsx.geometry.BlockBounds,
                           durationSec: Int, stepXZ: Int, stepY: Int, shape: PlotData?) {
        require(stepXZ > 0 && stepY > 0)
        val world = player.world
        val minX = Math.toIntExact(bounds.minX)
        val maxX = Math.toIntExact(bounds.maxX)
        val minZ = Math.toIntExact(bounds.minZ)
        val maxZ = Math.toIntExact(bounds.maxZ)
        val centerX = ((bounds.minX + bounds.maxX) / 2).toInt()
        val centerZ = ((bounds.minZ + bounds.maxZ) / 2).toInt()
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
        }, durationSec * 20L)
    }
}