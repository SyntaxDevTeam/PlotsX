package pl.syntaxdevteam.plotsx.databases

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
    val segments: List<PlotSegment> = listOf(PlotSegment(x, z, radius)) + extensions
    val area: Long get() = segments.fold(0L) { total, segment ->
        if (Long.MAX_VALUE - total < segment.area) Long.MAX_VALUE else total + segment.area
    }
    fun contains(x: Int, z: Int): Boolean = segments.any { it.contains(x, z) }

    fun segmentAt(x: Int, z: Int): PlotSegment? = segments.firstOrNull { it.contains(x, z) }

    /** Add the immediate neighbour of an existing segment; never skip occupied land. */
    fun expansion(direction: ExpansionDirection, source: PlotSegment = segments.first()): PlotSegment? {
        if (source !in segments) return null
        val side = radius.toLong() * 2 + 1
        val nx = source.x.toLong() + direction.dx * side
        val nz = source.z.toLong() + direction.dz * side
        if (nx - radius < Int.MIN_VALUE || nx + radius > Int.MAX_VALUE ||
            nz - radius < Int.MIN_VALUE || nz + radius > Int.MAX_VALUE) return null
        val candidate = PlotSegment(nx.toInt(), nz.toInt(), radius)
        return candidate.takeUnless { next -> segments.any { it.overlaps(next) } }
    }

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