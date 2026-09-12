package pl.syntaxdevteam.plotsx.databases

import pl.syntaxdevteam.plotsx.geometry.ClassicGeometry
import java.util.*

data class PlotData @JvmOverloads constructor(
    val id: Int,
    val ownerUuid: UUID,
    val x: Int,
    val z: Int,
    val y: Int,
    val radius: Int,
    val world: String,
    val name: String,
    val creationTime: Long,
    val extensions: List<PlotSegment> = emptyList()
) {
    val geometry = ClassicGeometry(PlotSegment(x, z, radius), extensions)
    val segments: List<PlotSegment> get() = geometry.segments
    val area: Long get() = geometry.area
    fun contains(x: Int, z: Int): Boolean = geometry.contains(x, z)

    fun segmentAt(x: Int, z: Int): PlotSegment? = geometry.segmentAt(x, z)

    /** Add the immediate neighbour of an existing segment; never skip occupied land. */
    fun expansion(direction: ExpansionDirection, source: PlotSegment = segments.first()): PlotSegment? =
        geometry.expansion(direction, source)

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
