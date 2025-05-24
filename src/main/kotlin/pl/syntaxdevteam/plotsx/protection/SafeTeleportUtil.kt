package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.databases.PlotData

object SafeTeleportUtil {
    private val unsafeBlocks = setOf(
        Material.LAVA, Material.WATER, Material.CACTUS, Material.FIRE, Material.MAGMA_BLOCK,
        Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW, Material.AIR, Material.VOID_AIR
    )

    fun findSafeLocation(world: World, centerX: Int, centerZ: Int, radius: Int): Location? {
        val yMax = world.maxHeight - 2 // -2, żeby nie wyjść poza świat przy sprawdzaniu dwóch bloków powyżej
        val yMin = world.minHeight
        // Szukaj na całej działce, od środka do krawędzi
        for (dx in -radius..radius) for (dz in -radius..radius) {
            val x = centerX + dx
            val z = centerZ + dz
            for (y in yMax downTo yMin) {
                val block = world.getBlockAt(x, y, z)
                val blockAbove = world.getBlockAt(x, y + 1, z)
                val blockTwoAbove = world.getBlockAt(x, y + 2, z)
                if (block.type !in unsafeBlocks
                    && blockAbove.type == Material.AIR
                    && blockTwoAbove.type == Material.AIR
                ) {
                    return Location(world, x + 0.5, y + 1.0, z + 0.5)
                }
            }
        }
        return null
    }

    fun safeTeleport(player: Player, plot: PlotData): Boolean {
        val world = Bukkit.getWorld(plot.world) ?: return false
        val loc = findSafeLocation(world, plot.x, plot.z, plot.radius)
        return if (loc != null) {
            player.teleport(loc)
            true
        } else {
            false
        }
    }
}