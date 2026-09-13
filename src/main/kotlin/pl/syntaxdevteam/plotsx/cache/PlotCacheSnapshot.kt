package pl.syntaxdevteam.plotsx.cache

import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotMemberData
import pl.syntaxdevteam.plotsx.geometry.SpatialPlotIndex
import java.util.Collections

/** Published as one unit so the plot map and spatial index always describe the same geometry. */
internal class PlotCacheSnapshot private constructor(
    val plots: Map<Int, PlotData>,
    val flags: Map<Int, List<PlotFlagData>>,
    val members: Map<Int, List<PlotMemberData>>,
    private val index: SpatialPlotIndex<PlotData>
) {
    fun at(world: String, x: Int, z: Int): PlotData? = index.at(world, x, z)

    fun withMetadata(flags: Map<Int, List<PlotFlagData>>, members: Map<Int, List<PlotMemberData>>): PlotCacheSnapshot =
        PlotCacheSnapshot(plots, freeze(flags, plots.keys), freeze(members, plots.keys), index)

    companion object {
        fun create(plots: Collection<PlotData> = emptyList(),
                   flags: Map<Int, List<PlotFlagData>> = emptyMap(),
                   members: Map<Int, List<PlotMemberData>> = emptyMap()): PlotCacheSnapshot {
            val byId = plots.associate { it.id to it.copy(extensions = Collections.unmodifiableList(it.segments.drop(1)), chunks = (it.geometry as? pl.syntaxdevteam.plotsx.geometry.ChunkGeometry)?.chunks.orEmpty()) }
            require(byId.size == plots.size) { "Duplicate plot IDs in cache snapshot" }
            return PlotCacheSnapshot(Collections.unmodifiableMap(byId), freeze(flags, byId.keys), freeze(members, byId.keys),
                SpatialPlotIndex(byId.values, { it.geometry }, { it.world }))
        }

        private fun <T> freeze(source: Map<Int, List<T>>, ids: Set<Int>): Map<Int, List<T>> =
            Collections.unmodifiableMap(source.filterKeys { it in ids }.mapValues { Collections.unmodifiableList(it.value.toList()) })
    }
}
