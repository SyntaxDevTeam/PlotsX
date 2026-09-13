package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.World

/** Optional bridge to an external region-protection plugin. */
fun interface RegionProtectionHook {
    fun overlapsProtectedRegion(world: World, centerX: Int, centerZ: Int, radius: Int): Boolean

    fun overlapsBounds(world: World, bounds: pl.syntaxdevteam.plotsx.geometry.BlockBounds): Boolean {
        // Legacy adapters cannot safely approximate an even-sided rectangle.
        val side = bounds.maxX - bounds.minX
        if (side != bounds.maxZ - bounds.minZ || side % 2L != 0L) return true
        return overlapsProtectedRegion(world, ((bounds.minX + bounds.maxX) / 2).toInt(),
            ((bounds.minZ + bounds.maxZ) / 2).toInt(), (side / 2).toInt())
    }
}
