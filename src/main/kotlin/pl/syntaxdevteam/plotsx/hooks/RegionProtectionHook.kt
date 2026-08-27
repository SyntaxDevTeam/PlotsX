package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.World

/** Optional bridge to an external region-protection plugin. */
fun interface RegionProtectionHook {
    fun overlapsProtectedRegion(world: World, centerX: Int, centerZ: Int, radius: Int): Boolean
}
