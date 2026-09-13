package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import pl.syntaxdevteam.plotsx.geometry.PlotGeometry
import java.util.*

data class PlotData @JvmOverloads constructor(
    val id: Int,
    val ownerUuid: UUID,
    val x: Int,
    val z: Int,
    val y: Int,
    val radius: Int?,
    val world: String,
    val name: String,
    val creationTime: Long,
    val extensions: List<PlotSegment> = emptyList(),
    val chunks: Set<ChunkPosition> = emptySet(),
    val geometryRevision: Long = 0
) {
    val geometry: PlotGeometry = if (radius != null) {
        require(chunks.isEmpty()) { "Classic plots cannot have chunks" }
        ClassicGeometry(PlotSegment(x, z, radius), extensions)
    } else {
        require(extensions.isEmpty()) { "Chunk plots cannot have classic extensions" }
        ChunkGeometry(chunks)
    }
    val segments: List<PlotSegment> get() = (geometry as? ClassicGeometry)?.segments.orEmpty()
    val area: Long get() = geometry.area
    fun contains(x: Int, z: Int): Boolean = geometry.contains(x, z)

    fun segmentAt(x: Int, z: Int): PlotSegment? = (geometry as? ClassicGeometry)?.segmentAt(x, z)

    /** Add the immediate neighbour of an existing segment; never skip occupied land. */
    fun expansion(direction: ExpansionDirection, source: PlotSegment? = segments.firstOrNull()): PlotSegment? =
        source?.let { (geometry as? ClassicGeometry)?.expansion(direction, it) }

}

enum class ExpansionDirection(val dx: Int, val dz: Int) {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0)
}

data class PlotSegment(val x: Int, val z: Int, val radius: Int) {
    val area: Long get() = (radius.toLong() * 2 + 1).let {
        if (it > 3037000499L) Long.MAX_VALUE else it * it
    }
    fun overlaps(other: PlotSegment): Boolean =
        kotlin.math.abs(x.toLong() - other.x) <= radius.toLong() + other.radius &&
        kotlin.math.abs(z.toLong() - other.z) <= radius.toLong() + other.radius

    fun contains(px: Int, pz: Int): Boolean =
        px.toLong() in x.toLong() - radius..x.toLong() + radius &&
        pz.toLong() in z.toLong() - radius..z.toLong() + radius
}
