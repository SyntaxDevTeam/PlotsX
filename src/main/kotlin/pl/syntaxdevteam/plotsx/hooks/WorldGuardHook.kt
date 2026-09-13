package pl.syntaxdevteam.plotsx.hooks

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import org.bukkit.World
import pl.syntaxdevteam.plotsx.PlotsX

/** WorldGuard 7 adapter used only when WorldGuard is enabled. */
class WorldGuardHook(private val plugin: PlotsX) : RegionProtectionHook {

    override fun overlapsProtectedRegion(
        world: World,
        centerX: Int,
        centerZ: Int,
        radius: Int
    ): Boolean = overlapsBounds(world, pl.syntaxdevteam.plotsx.geometry.BlockBounds(
        centerX.toLong() - radius, centerZ.toLong() - radius, centerX.toLong() + radius, centerZ.toLong() + radius))

    override fun overlapsBounds(world: World, bounds: pl.syntaxdevteam.plotsx.geometry.BlockBounds): Boolean = try {
        val regionManager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
        if (regionManager == null) {
            plugin.logger.warning(
                "WorldGuard RegionManager unavailable for ${world.name}; blocking claim for safety."
            )
            return true
        }

        val candidate = ProtectedCuboidRegion(
            "__plotsx_claim_check__",
            true,
            BlockVector3.at(Math.toIntExact(bounds.minX), world.minHeight, Math.toIntExact(bounds.minZ)),
            BlockVector3.at(Math.toIntExact(bounds.maxX), world.maxHeight - 1, Math.toIntExact(bounds.maxZ))
        )

        val intersections = candidate.getIntersectingRegions(regionManager.regions.values)
        val overlap = intersections.any { it.id != "__global__" }
        if (overlap) {
            plugin.logger.debug(
                "WorldGuard blocked claim in ${world.name} at $bounds; " +
                    "regions=${intersections.joinToString { it.id }}"
            )
        }
        overlap
    } catch (exception: RuntimeException) {
        plugin.logger.err(
            "WorldGuard overlap check failed in ${world.name}; blocking claim for safety: ${exception.message}"
        )
        true
    }
}
