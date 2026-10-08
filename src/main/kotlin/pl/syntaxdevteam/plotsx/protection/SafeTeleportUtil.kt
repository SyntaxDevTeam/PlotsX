package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotTeleportSpawn
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import pl.syntaxdevteam.plotsx.PlotsX

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
        if (y < world.minHeight + 1 || y > world.maxHeight - 3) return false
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

    /** Validates a player-selected teleport point against current plot geometry and blocks. */
    fun isSafeTeleportSpawn(world: World, plot: PlotData, spawn: PlotTeleportSpawn): Boolean =
        world.name.equals(plot.world, ignoreCase = true) &&
            plot.contains(spawn.x, spawn.z) &&
            isSafeBlock(world, spawn.x, spawn.y, spawn.z)

    private fun configuredSpawn(world: World, plot: PlotData): Location? {
        val spawn = plot.teleportSpawn ?: return null
        if (!isSafeTeleportSpawn(world, plot, spawn)) return null
        return Location(world, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5)
    }

    /**
     * Szuka bezpiecznej lokalizacji:
     * 1) Priorytet: poziomy wg kolejki buildYQueue (blisko baseY)
     * 2) Fallback: od góry świata w dół (yMax..yMin)
     */
    fun findSafeLocation(
        world: World,
        centerX: Int, centerZ: Int, radius: Int,
        baseY: Int,
        contains: (Int, Int) -> Boolean = { _, _ -> true }
    ): Location? {
        val yMax = world.maxHeight - 3
        val yMin = world.minHeight + 1
        val yQueue = buildYQueue(baseY, yMin, yMax)

        // 1) priorytet: wysokości blisko baseY
        for (y in yQueue) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val x = centerX + dx
                    val z = centerZ + dz
                    if (contains(x, z) && isSafeBlock(world, x, y, z)) {
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
                    if (contains(x, z) && isSafeBlock(world, x, y, z)) {
                        return Location(world, x + 0.5, y.toDouble(), z + 0.5)
                    }
                }
            }
        }

        return null
    }

    /**
     * Teleportuje gracza najpierw do zapisanego punktu działki, a jeśli ten nie jest już
     * bezpieczny lub nie należy do geometrii, używa dotychczasowego wyszukiwania awaryjnego.
     */
    fun safeTeleport(plugin: PlotsX, player: Player, plot: PlotData, completion: (Boolean) -> Unit) {
        val world = Bukkit.getWorld(plot.world)
        if (world == null) { completion(false); return }
        val radius = plot.radius ?: 16
        val chunks = linkedSetOf<Pair<Int, Int>>()
        plot.teleportSpawn?.let { chunks.add(Math.floorDiv(it.x, 16) to Math.floorDiv(it.z, 16)) }
        chunks.add(Math.floorDiv(plot.x, 16) to Math.floorDiv(plot.z, 16))
        for (x in Math.floorDiv(plot.x - radius, 16)..Math.floorDiv(plot.x + radius, 16)) {
            for (z in Math.floorDiv(plot.z - radius, 16)..Math.floorDiv(plot.z + radius, 16)) chunks.add(x to z)
        }
        val iterator = chunks.iterator()
        fun searchNext(): CompletableFuture<Location?> {
            if (!plugin.isEnabled || !iterator.hasNext()) return CompletableFuture.completedFuture(null)
            val (chunkX, chunkZ) = iterator.next()
            return world.getChunkAtAsync(chunkX, chunkZ).thenCompose {
                val found = CompletableFuture<Location?>()
                if (!plugin.isEnabled) return@thenCompose CompletableFuture.completedFuture<Location?>(null)
                plugin.server.regionScheduler.execute(plugin, world, chunkX, chunkZ) {
                    try {
                        val spawn = plot.teleportSpawn
                        val configured = if (spawn != null && Math.floorDiv(spawn.x, 16) == chunkX &&
                            Math.floorDiv(spawn.z, 16) == chunkZ) configuredSpawn(world, plot) else null
                        found.complete(configured ?: findSafeLocation(world, chunkX * 16 + 8, chunkZ * 16 + 8, 8, plot.y) { x, z ->
                            x.toLong() in (plot.x.toLong() - radius)..(plot.x.toLong() + radius) &&
                            z.toLong() in (plot.z.toLong() - radius)..(plot.z.toLong() + radius) &&
                            Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ && plot.contains(x, z)
                        })
                    } catch (failure: Exception) { found.completeExceptionally(failure) }
                }
                found
            }.thenCompose { location ->
                if (location != null) CompletableFuture.completedFuture(location) else searchNext()
            }
        }
        searchNext().whenComplete { location, failure ->
            if (!plugin.isEnabled) return@whenComplete
            plugin.schedulerAdapter.runForPlayer(player, Runnable {
                if (failure != null || location == null) { completion(false); return@Runnable }
                player.teleportAsync(location).whenComplete { success, teleportFailure ->
                    if (plugin.isEnabled) plugin.schedulerAdapter.runForPlayer(player, Runnable {
                        completion(teleportFailure == null && success == true)
                    })
                }
            })
        }
    }
}
