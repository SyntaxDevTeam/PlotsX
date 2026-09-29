package pl.syntaxdevteam.plotsx.visualization

import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.geometry.ChunkGeometry
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition

internal object BorderOutline {
    data class Point(val x: Int, val z: Int)

    fun create(plot: PlotData, spacing: Int): List<Point> {
        val geometry = plot.geometry
        if (geometry is ChunkGeometry) return chunks(geometry, spacing)
        val result = linkedSetOf<Point>()
        geometry.regions().forEach { bounds ->
            val minX = Math.toIntExact(bounds.minX); val maxX = Math.toIntExact(bounds.maxX)
            val minZ = Math.toIntExact(bounds.minZ); val maxZ = Math.toIntExact(bounds.maxZ)
            for (x in minX..maxX step spacing) {
                if (!geometry.contains(x, minZ - 1)) result += Point(x, minZ)
                if (!geometry.contains(x, maxZ + 1)) result += Point(x, maxZ)
            }
            for (z in minZ..maxZ step spacing) {
                if (!geometry.contains(minX - 1, z)) result += Point(minX, z)
                if (!geometry.contains(maxX + 1, z)) result += Point(maxX, z)
            }
        }
        return result.toList()
    }

    private fun chunks(geometry: ChunkGeometry, spacing: Int): List<Point> {
        val result = linkedSetOf<Point>()
        geometry.chunks.forEach { chunk ->
            val b = chunk.bounds
            val minX = b.minX.toInt(); val maxX = b.maxX.toInt(); val minZ = b.minZ.toInt(); val maxZ = b.maxZ.toInt()
            fun absent(dx: Int, dz: Int): Boolean {
                val x = chunk.x.toLong() + dx; val z = chunk.z.toLong() + dz
                if (x !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong() ||
                    z !in ChunkPosition.MIN_COORDINATE.toLong()..ChunkPosition.MAX_COORDINATE.toLong()) return true
                return ChunkPosition(x.toInt(), z.toInt()) !in geometry.chunks
            }
            if (absent(0, -1)) for (x in minX..maxX step spacing) result += Point(x, minZ)
            if (absent(0, 1)) for (x in minX..maxX step spacing) result += Point(x, maxZ)
            if (absent(-1, 0)) for (z in minZ..maxZ step spacing) result += Point(minX, z)
            if (absent(1, 0)) for (z in minZ..maxZ step spacing) result += Point(maxX, z)
        }
        return result.toList()
    }
}
