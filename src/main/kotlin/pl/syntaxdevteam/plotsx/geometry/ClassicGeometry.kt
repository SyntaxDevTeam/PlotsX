package pl.syntaxdevteam.plotsx.geometry

import pl.syntaxdevteam.plotsx.databases.ExpansionDirection
import pl.syntaxdevteam.plotsx.databases.PlotSegment
import java.util.Collections

/** Adapter preserving the original base-radius expansion and summed segment area semantics. */
class ClassicGeometry(val base: PlotSegment, extensions: List<PlotSegment> = emptyList()) : PlotGeometry {
    val segments: List<PlotSegment> = Collections.unmodifiableList(listOf(base) + extensions)

    override val area: Long get() = segments.fold(0L) { total, segment ->
        if (Long.MAX_VALUE - total < segment.area) Long.MAX_VALUE else total + segment.area
    }

    override val bounds: BlockBounds = BlockBounds(
        segments.minOf { it.x.toLong() - it.radius },
        segments.minOf { it.z.toLong() - it.radius },
        segments.maxOf { it.x.toLong() + it.radius },
        segments.maxOf { it.z.toLong() + it.radius }
    )

    override fun contains(x: Int, z: Int): Boolean = segments.any { it.contains(x, z) }

    override fun regions(): Sequence<BlockBounds> = segments.asSequence().map {
        BlockBounds(it.x.toLong() - it.radius, it.z.toLong() - it.radius,
            it.x.toLong() + it.radius, it.z.toLong() + it.radius)
    }

    fun segmentAt(x: Int, z: Int): PlotSegment? = segments.firstOrNull { it.contains(x, z) }

    fun expansion(direction: ExpansionDirection, source: PlotSegment = base): PlotSegment? {
        if (source !in segments) return null
        val radius = base.radius
        val side = radius.toLong() * 2 + 1
        val nx = source.x.toLong() + direction.dx * side
        val nz = source.z.toLong() + direction.dz * side
        if (nx - radius < Int.MIN_VALUE || nx + radius > Int.MAX_VALUE ||
            nz - radius < Int.MIN_VALUE || nz + radius > Int.MAX_VALUE) return null
        val candidate = PlotSegment(nx.toInt(), nz.toInt(), radius)
        return candidate.takeUnless { next -> segments.any { it.overlaps(next) } }
    }
}
