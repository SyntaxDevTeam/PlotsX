package pl.syntaxdevteam.plotsx.hooks

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.RegionResultSet
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.World
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.geometry.BlockBounds

/** WorldGuard 7 adapter used only when WorldGuard is enabled. */
class WorldGuardHook(private val plugin: PlotsX) : RegionProtectionHook {

    override fun overlapsProtectedRegion(
        world: World,
        centerX: Int,
        centerZ: Int,
        radius: Int
    ): Boolean = overlapsBounds(
        world,
        BlockBounds(
            centerX.toLong() - radius,
            centerZ.toLong() - radius,
            centerX.toLong() + radius,
            centerZ.toLong() + radius
        )
    )

    override fun overlapsBounds(world: World, bounds: BlockBounds): Boolean = try {
        val regionManager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
        if (regionManager == null) {
            plugin.logger.warning(
                "WorldGuard RegionManager unavailable for ${world.name}; blocking claim for safety."
            )
            return true
        }

        val regions = regionManager.regions.values.filter { it.id != GLOBAL_REGION_ID }
        val blocked = blocksClaim(world, bounds, regions)
        if (blocked) {
            plugin.logger.debug(
                "WorldGuard blocked claim in ${world.name} at $bounds; " +
                    "'$CLAIM_FLAG_NAME' must resolve to ALLOW for every applicable protected area."
            )
        }
        blocked
    } catch (exception: RuntimeException) {
        plugin.logger.err(
            "WorldGuard claim check failed in ${world.name}; blocking claim for safety: ${exception.message}"
        )
        true
    }

    override fun overlappingBounds(
        world: World,
        bounds: Collection<BlockBounds>
    ): Set<BlockBounds> {
        if (bounds.isEmpty()) return emptySet()
        return try {
            val regionManager = WorldGuard.getInstance().platform.regionContainer.get(BukkitAdapter.adapt(world))
            if (regionManager == null) {
                plugin.logger.warning("WorldGuard RegionManager unavailable for ${world.name}; blocking preview for safety.")
                return bounds.toSet()
            }

            val regions = regionManager.regions.values.filter { it.id != GLOBAL_REGION_ID }
            bounds.filterTo(linkedSetOf()) { candidateBounds ->
                blocksClaim(world, candidateBounds, regions)
            }
        } catch (exception: RuntimeException) {
            plugin.logger.err("WorldGuard preview check failed in ${world.name}; blocking preview for safety: ${exception.message}")
            bounds.toSet()
        }
    }

    private fun blocksClaim(
        world: World,
        bounds: BlockBounds,
        regions: Collection<ProtectedRegion>
    ): Boolean {
        if (regions.isEmpty()) return false

        val candidate = ProtectedCuboidRegion(
            CLAIM_CHECK_REGION_ID,
            true,
            BlockVector3.at(Math.toIntExact(bounds.minX), world.minHeight, Math.toIntExact(bounds.minZ)),
            BlockVector3.at(Math.toIntExact(bounds.maxX), world.maxHeight - 1, Math.toIntExact(bounds.maxZ))
        )
        val intersections = candidate.getIntersectingRegions(regions)
        if (intersections.isEmpty()) return false

        val claimFlag = WorldGuardFlags.claimFlag ?: return true
        val state = RegionResultSet(intersections.toMutableList(), null).queryState(null, claimFlag)
        return state != StateFlag.State.ALLOW
    }

    private companion object {
        const val GLOBAL_REGION_ID = "__global__"
        const val CLAIM_CHECK_REGION_ID = "__plotsx_claim_check__"
        const val CLAIM_FLAG_NAME = WorldGuardFlags.CLAIM_FLAG_NAME
    }
}
