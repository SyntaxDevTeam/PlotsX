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

    /** Grow a ray from the original plot, adding exactly one original-sized segment. */
    fun expansion(direction: ExpansionDirection): PlotSegment? {
        val side = radius.toLong() * 2 + 1
        var distance = 1L
        while (true) {
            val nx = x.toLong() + direction.dx * side * distance
            val nz = z.toLong() + direction.dz * side * distance
            if (nx - radius < Int.MIN_VALUE || nx + radius > Int.MAX_VALUE ||
                nz - radius < Int.MIN_VALUE || nz + radius > Int.MAX_VALUE) return null
            val candidate = PlotSegment(nx.toInt(), nz.toInt(), radius)
            if (candidate !in segments) return candidate
            distance++
        }
    }
}

enum class ExpansionDirection(val dx: Int, val dz: Int) {
    NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0)
}

data class PlotSegment(val x: Int, val z: Int, val radius: Int) {
    val area: Long get() = (radius.toLong() * 2 + 1).let {
        if (it > 3037000499L) Long.MAX_VALUE else it * it
    }
    fun contains(px: Int, pz: Int): Boolean =
        px.toLong() in x.toLong() - radius..x.toLong() + radius &&
        pz.toLong() in z.toLong() - radius..z.toLong() + radius
}