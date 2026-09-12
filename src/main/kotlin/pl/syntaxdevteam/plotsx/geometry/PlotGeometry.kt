package pl.syntaxdevteam.plotsx.geometry

/** Inclusive block-column bounds. A bounding box is not necessarily all claimed land. */
data class BlockBounds(val minX: Long, val minZ: Long, val maxX: Long, val maxZ: Long) {
    init {
        require(minX <= maxX && minZ <= maxZ) { "Invalid block bounds" }
    }

    fun contains(x: Int, z: Int): Boolean = x.toLong() in minX..maxX && z.toLong() in minZ..maxZ

    fun intersects(other: BlockBounds): Boolean =
        minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ
}

/** World-independent, immutable geometry. Callers must check world identity before intersecting. */
sealed interface PlotGeometry {
    val area: Long
    val bounds: BlockBounds
    fun contains(x: Int, z: Int): Boolean

    /** Exact occupied rectangles, not the overall bounding box. Does not load Minecraft chunks. */
    fun regions(): Sequence<BlockBounds>

    fun intersects(other: PlotGeometry): Boolean = bounds.intersects(other.bounds) &&
        regions().any { region -> other.regions().any(region::intersects) }
}
