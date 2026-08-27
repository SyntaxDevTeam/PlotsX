package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.ArrayDeque

object SafeTeleportUtil {
    private val unsafeBlocks: Set<Material> by lazy {
        PlotCompat.loadUnsafeBlocks()
    }

    /**
     * Buduje kolejkę wysokości do przeszukania.
     * Najpierw zadaną wysokość `baseY`, potem pary (baseY + delta, baseY - delta),
     * przy równym delta wybieramy większe Y jako pierwsze.
     */
    private fun buildYQueue(baseY: Int, yMin: Int, yMax: Int): ArrayDeque<Int> {
        val queue = ArrayDeque<Int>()
        val maxDelta = maxOf(yMax - baseY, baseY - yMin)
        for (delta in 0..maxDelta) {
            val up = baseY + delta
            val down = baseY - delta
            if (delta == 0) {
                if (up in yMin..yMax) queue.add(up)
            } else {
                if (up in yMin..yMax) queue.add(up)
                if (down in yMin..yMax) queue.add(down)
            }
        }
        return queue
    }

    /**
     * Sprawdza, czy można się teleportować na blok na pozycji y:
     * - blok `ground` (y-1) nie jest na liście unsafeBlocks
     * - blok docelowy (y) nie jest na liście unsafeBlocks
     * - dwa bloki nad (y+1, y+2) są powietrzem
     */
    private fun isSafeBlock(world: World, x: Int, y: Int, z: Int): Boolean {
        val ground: Block = world.getBlockAt(x, y - 1, z)
        val block: Block = world.getBlockAt(x, y, z)
        val above: Block = world.getBlockAt(x, y + 1, z)
        val twoAbove: Block = world.getBlockAt(x, y + 2, z)

        return ground.type.isSolid
                && ground.type !in unsafeBlocks
                && block.isPassable
                && block.type !in unsafeBlocks
                && above.isPassable
                && above.type !in unsafeBlocks
                && twoAbove.isPassable
                && twoAbove.type !in unsafeBlocks
    }

    /**
     * Szuka bezpiecznej lokalizacji:
     * 1) Priorytet: poziomy wg kolejki buildYQueue (blisko baseY)
     * 2) Fallback: od góry świata w dół (yMax..yMin)
     */
    fun findSafeLocation(
        world: World,
        centerX: Int, centerZ: Int, radius: Int,
        baseY: Int
    ): Location? {
        val yMax = world.maxHeight - 2
        val yMin = world.minHeight + 1
        val yQueue = buildYQueue(baseY, yMin, yMax)

        // 1) priorytet: wysokości blisko baseY
        for (y in yQueue) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = centerX + dx
                    val z = centerZ + dz
                    if (isSafeBlock(world, x, y, z)) {
                        return Location(world, x + 0.5, y.toDouble(), z + 0.5)
                    }
                }
            }
        }

        // 2) fallback: od góry świata w dół
        for (y in yMax downTo yMin) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = centerX + dx
                    val z = centerZ + dz
                    if (isSafeBlock(world, x, y, z)) {
                        return Location(world, x + 0.5, y.toDouble(), z + 0.5)
                    }
                }
            }
        }

        return null
    }

    /**
     * Teleportuje gracza na bezpieczną lokalizację wg PlotData.y.
     */
    fun safeTeleport(player: Player, plot: PlotData): Boolean {
        val world = Bukkit.getWorld(plot.world) ?: return false
        val loc = findSafeLocation(world, plot.x, plot.z, plot.radius, plot.y)
        return loc?.let {
            player.teleport(it)
            true
        } ?: false
    }
}
