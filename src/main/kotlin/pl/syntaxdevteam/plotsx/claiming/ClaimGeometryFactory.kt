package pl.syntaxdevteam.plotsx.claiming

import pl.syntaxdevteam.plotsx.databases.PlotSegment
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import pl.syntaxdevteam.plotsx.geometry.PlotGeometry

/** Prepares geometry only. Does not grant land, charge money, check worlds or persist anything. */
object ClaimGeometryFactory {
    /** classicRadius is required for classic claims and has no meaning for chunk claims. */
    fun create(mode: ClaimMode, blockX: Int, blockZ: Int, classicRadius: Int? = null): PlotGeometry =
        when (mode) {
            ClaimMode.CLASSIC -> {
                require(classicRadius != null && classicRadius >= 1) { "Classic claim radius must be positive" }
                val geometry = ClassicGeometry(PlotSegment(blockX, blockZ, classicRadius))
                require(geometry.bounds.minX >= Int.MIN_VALUE && geometry.bounds.maxX <= Int.MAX_VALUE &&
                    geometry.bounds.minZ >= Int.MIN_VALUE && geometry.bounds.maxZ <= Int.MAX_VALUE) {
                    "Classic claim exceeds the supported block range"
                }
                geometry
            }
            ClaimMode.CHUNKS -> ChunkGeometry(listOf(ChunkPosition.atBlock(blockX, blockZ)))
        }
}
