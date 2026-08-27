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
    ): Boolean = try {
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
            BlockVector3.at(centerX - radius, world.minHeight, centerZ - radius),
            BlockVector3.at(centerX + radius, world.maxHeight - 1, centerZ + radius)
        )

        val intersections = candidate.getIntersectingRegions(regionManager.regions.values)
        val overlap = intersections.any { it.id != "__global__" }
        if (overlap) {
            plugin.logger.debug(
                "WorldGuard blocked claim in ${world.name} at $centerX,$centerZ radius=$radius; " +
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
