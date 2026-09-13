package pl.syntaxdevteam.plotsx.geometry

import java.util.Locale

/** Immutable broad-phase index. T and the supplied geometries must be immutable snapshots. */
internal class SpatialPlotIndex<T>(
    plots: Collection<T>,
    private val geometry: (T) -> PlotGeometry,
    world: (T) -> String,
    maxBucketsPerClassicPlot: Int = 1024
) {
    private val buckets = mutableMapOf<String, MutableMap<ChunkPosition, MutableList<T>>>()
    private val largeClassicPlots = mutableMapOf<String, MutableList<T>>()

    init {
        require(maxBucketsPerClassicPlot > 0)
        for (plot in plots) {
            val key = worldKey(world(plot))
            val shape = geometry(plot)
            val positions = when (shape) {
                is ChunkGeometry -> shape.chunks
                is ClassicGeometry -> {
                    val covered = linkedSetOf<ChunkPosition>()
                    var tooLarge = false
                    for (region in shape.regions()) {
                        // Classic data may extend past Int bounds; only index addressable block columns.
                        val minX = maxOf(region.minX, Int.MIN_VALUE.toLong())
                        val maxX = minOf(region.maxX, Int.MAX_VALUE.toLong())
                        val minZ = maxOf(region.minZ, Int.MIN_VALUE.toLong())
                        val maxZ = minOf(region.maxZ, Int.MAX_VALUE.toLong())
                        if (minX > maxX || minZ > maxZ) continue
                        val from = ChunkPosition.atBlock(minX.toInt(), minZ.toInt())
                        val to = ChunkPosition.atBlock(maxX.toInt(), maxZ.toInt())
                        val count = (to.x.toLong() - from.x + 1) * (to.z.toLong() - from.z + 1)
                        if (count > maxBucketsPerClassicPlot) { tooLarge = true; break }
                        for (x in from.x..to.x) for (z in from.z..to.z) covered.add(ChunkPosition(x, z))
                        if (covered.size > maxBucketsPerClassicPlot) { tooLarge = true; break }
                    }
                    if (tooLarge) {
                        largeClassicPlots.getOrPut(key) { mutableListOf() }.add(plot)
                        continue
                    }
                    covered
                }
            }
            val index = buckets.getOrPut(key) { mutableMapOf() }
            for (position in positions) index.getOrPut(position) { mutableListOf() }.add(plot)
        }
    }

    fun at(world: String, x: Int, z: Int): T? {
        val key = worldKey(world)
        val candidates = buckets[key]?.get(ChunkPosition.atBlock(x, z)).orEmpty()
        return candidates.firstOrNull { geometry(it).contains(x, z) }
            ?: largeClassicPlots[key]?.firstOrNull { geometry(it).contains(x, z) }
    }

    companion object {
        fun worldKey(world: String): String = world.lowercase(Locale.ROOT)
    }
}
