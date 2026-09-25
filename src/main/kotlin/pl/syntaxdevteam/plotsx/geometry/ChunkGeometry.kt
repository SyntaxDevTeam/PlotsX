package pl.syntaxdevteam.plotsx.geometry

import pl.syntaxdevteam.plotsx.databases.ExpansionDirection
import java.util.Collections

/** Only chunks whose entire block range fits the block-coordinate API are accepted. */
data class ChunkPosition(val x: Int, val z: Int) {
    init {
        require(x in MIN_COORDINATE..MAX_COORDINATE && z in MIN_COORDINATE..MAX_COORDINATE) {
            "Chunk coordinates exceed the supported block range: $x, $z"
        }
    }

    val bounds: BlockBounds get() = BlockBounds(x.toLong() * SIZE, z.toLong() * SIZE,
        x.toLong() * SIZE + SIZE - 1, z.toLong() * SIZE + SIZE - 1)

    fun neighbour(direction: ExpansionDirection): ChunkPosition? {
        val nx = x + direction.dx
        val nz = z + direction.dz
        return if (nx in MIN_COORDINATE..MAX_COORDINATE && nz in MIN_COORDINATE..MAX_COORDINATE)
            ChunkPosition(nx, nz) else null
    }

    companion object {
        const val SIZE = 16
        const val AREA = 256L
        const val MIN_COORDINATE = Int.MIN_VALUE / SIZE
        const val MAX_COORDINATE = Int.MAX_VALUE / SIZE

        fun atBlock(x: Int, z: Int): ChunkPosition = ChunkPosition(Math.floorDiv(x, SIZE), Math.floorDiv(z, SIZE))
    }
}

/** A nonempty, side-connected set of complete chunks; holes inside its bounds remain unclaimed. */
class ChunkGeometry(chunks: Collection<ChunkPosition>) : PlotGeometry {
    data class Expansion(val source: ChunkPosition, val direction: ExpansionDirection, val target: ChunkPosition)

    val chunks: Set<ChunkPosition> = Collections.unmodifiableSet(LinkedHashSet(chunks))

    init {
        require(this.chunks.isNotEmpty()) { "A chunk plot must contain at least one chunk" }
        val remaining = this.chunks.toMutableSet()
        val pending = ArrayDeque<ChunkPosition>()
        pending.add(remaining.first().also { remaining.remove(it) })
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (direction in ExpansionDirection.entries) {
                val next = current.neighbour(direction) ?: continue
                if (remaining.remove(next)) pending.addLast(next)
            }
        }
        require(remaining.isEmpty()) { "Chunks of a plot must be connected by shared sides" }
    }

    override val area: Long get() = chunks.size.toLong() * ChunkPosition.AREA
    override val bounds: BlockBounds = BlockBounds(
        this.chunks.minOf { it.bounds.minX }, this.chunks.minOf { it.bounds.minZ },
        this.chunks.maxOf { it.bounds.maxX }, this.chunks.maxOf { it.bounds.maxZ }
    )

    override fun contains(x: Int, z: Int): Boolean = ChunkPosition.atBlock(x, z) in chunks
    override fun regions(): Sequence<BlockBounds> = chunks.asSequence().map { it.bounds }

    override fun intersects(other: PlotGeometry): Boolean = when (other) {
        is ChunkGeometry -> if (chunks.size <= other.chunks.size) chunks.any { it in other.chunks }
            else other.chunks.any { it in chunks }
        else -> super.intersects(other)
    }

    /** Geometry-only candidate; ownership, external regions and limits belong to the service. */
    fun expansion(direction: ExpansionDirection, source: ChunkPosition): ChunkPosition? {
        if (source !in chunks) return null
        return source.neighbour(direction)?.takeUnless { it in chunks }
    }

    /** Resolves a free chunk selected by standing on it to an expansion from this plot. */
    fun expansionTo(target: ChunkPosition): Expansion? {
        if (target in chunks) return null
        for (direction in ExpansionDirection.entries) {
            val sourceX = target.x.toLong() - direction.dx
            val sourceZ = target.z.toLong() - direction.dz
            if (sourceX !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong() ||
                sourceZ !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong()) continue
            val source = ChunkPosition(sourceX.toInt(), sourceZ.toInt())
            if (source in chunks) return Expansion(source, direction, target)
        }
        return null
    }

    /**
     * Returns the geometry after removing [target], or null when the target is not owned,
     * is the last chunk, or its removal would split the plot into disconnected islands.
     */
    fun without(target: ChunkPosition): ChunkGeometry? {
        if (target !in chunks || chunks.size <= 1) return null
        return runCatching { ChunkGeometry(chunks - target) }.getOrNull()
    }
}
