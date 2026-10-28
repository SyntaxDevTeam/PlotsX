package pl.syntaxdevteam.plotsx.cache

import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotMemberData
import pl.syntaxdevteam.plotsx.geometry.SpatialPlotIndex
import java.util.Collections
import java.util.UUID

/** Published as one unit so models, policy lookups and geometry always describe the same state. */
internal class PlotCacheSnapshot private constructor(
    val plots: Map<Int, PlotData>,
    val flags: Map<Int, List<PlotFlagData>>,
    val members: Map<Int, List<PlotMemberData>>,
    private val index: SpatialPlotIndex<Int>,
    private val flagsByName: Map<Int, Map<String, PlotFlagData>>,
    private val membersByUuid: Map<Int, Map<String, PlotMemberData>>
) {
    // The index stores IDs, allowing names, owners and spawns to change without rebuilding geometry.
    fun at(world: String, x: Int, z: Int): PlotData? = index.at(world, x, z)?.let(plots::get)
    fun atChunk(world: String, position: pl.syntaxdevteam.plotsx.geometry.ChunkPosition): PlotData? =
        index.atChunk(world, position)?.let(plots::get)
    fun flagValue(plotId: Int, name: String): Boolean? = flagsByName[plotId]?.get(name)?.value?.toBooleanStrictOrNull()
    fun member(plotId: Int, player: UUID): PlotMemberData? = membersByUuid[plotId]?.get(player.toString())

    fun withMetadata(flags: Map<Int, List<PlotFlagData>>, members: Map<Int, List<PlotMemberData>>): PlotCacheSnapshot {
        val frozenFlags = freeze(flags, plots.keys)
        val frozenMembers = freeze(members, plots.keys)
        return PlotCacheSnapshot(plots, frozenFlags, frozenMembers, index,
            flagIndex(frozenFlags), memberIndex(frozenMembers))
    }

    /** Replaces one complete plot while retaining unrelated immutable models and policy lists. */
    fun withPlot(plotId: Int, plot: PlotData?, plotFlags: List<PlotFlagData>, plotMembers: List<PlotMemberData>): PlotCacheSnapshot {
        require(plot == null || plot.id == plotId)
        val previous = plots[plotId]
        val replacement = if (plot == previous) previous else plot?.let(::freezePlot)
        val nextPlots = Collections.unmodifiableMap(plots.toMutableMap().apply {
            if (replacement == null) remove(plotId) else put(plotId, replacement)
        })
        val nextFlags = replace(flags, plotId, replacement?.let { plotFlags })
        val nextMembers = replace(members, plotId, replacement?.let { plotMembers })
        val nextFlagsByName = replaceIndex(flagsByName, plotId, replacement?.let { byFirst(nextFlags.getValue(plotId)) { it.name } })
        val nextMembersByUuid = replaceIndex(membersByUuid, plotId, replacement?.let { byFirst(nextMembers.getValue(plotId)) { it.memberUuid } })
        val sameGeometry = previous != null && replacement != null &&
            previous.world.equals(replacement.world, true) && previous.x == replacement.x && previous.z == replacement.z &&
            previous.radius == replacement.radius && previous.extensions == replacement.extensions && previous.chunks == replacement.chunks
        return PlotCacheSnapshot(nextPlots, nextFlags, nextMembers,
            if (sameGeometry || (previous == null && replacement == null)) index else spatialIndex(nextPlots),
            nextFlagsByName, nextMembersByUuid)
    }

    companion object {
        fun create(plots: Collection<PlotData> = emptyList(),
                   flags: Map<Int, List<PlotFlagData>> = emptyMap(),
                   members: Map<Int, List<PlotMemberData>> = emptyMap()): PlotCacheSnapshot {
            val byId = Collections.unmodifiableMap(plots.associate { it.id to freezePlot(it) })
            require(byId.size == plots.size) { "Duplicate plot IDs in cache snapshot" }
            val frozenFlags = freeze(flags, byId.keys)
            val frozenMembers = freeze(members, byId.keys)
            return PlotCacheSnapshot(byId, frozenFlags, frozenMembers, spatialIndex(byId),
                flagIndex(frozenFlags), memberIndex(frozenMembers))
        }

        private fun spatialIndex(plots: Map<Int, PlotData>) = SpatialPlotIndex(plots.keys,
            { plots.getValue(it).geometry }, { plots.getValue(it).world })
        private fun freezePlot(plot: PlotData) = plot.copy(
            extensions = Collections.unmodifiableList(plot.segments.drop(1)),
            chunks = (plot.geometry as? pl.syntaxdevteam.plotsx.geometry.ChunkGeometry)?.chunks.orEmpty())
        private fun <T> freeze(source: Map<Int, List<T>>, ids: Set<Int>): Map<Int, List<T>> =
            Collections.unmodifiableMap(source.filterKeys { it in ids }.mapValues { Collections.unmodifiableList(it.value.toList()) })
        private fun <T> replace(source: Map<Int, List<T>>, id: Int, values: List<T>?): Map<Int, List<T>> =
            replaceIndex(source, id, values?.let { Collections.unmodifiableList(it.toList()) })
        private fun <T> replaceIndex(source: Map<Int, T>, id: Int, value: T?): Map<Int, T> =
            Collections.unmodifiableMap(source.toMutableMap().apply { if (value == null) remove(id) else put(id, value) })
        private fun <T> byFirst(values: List<T>, key: (T) -> String): Map<String, T> =
            Collections.unmodifiableMap(linkedMapOf<String, T>().apply { values.forEach { putIfAbsent(key(it), it) } })
        private fun flagIndex(flags: Map<Int, List<PlotFlagData>>) =
            Collections.unmodifiableMap(flags.mapValues { byFirst(it.value) { flag -> flag.name } })
        private fun memberIndex(members: Map<Int, List<PlotMemberData>>) =
            Collections.unmodifiableMap(members.mapValues { byFirst(it.value) { member -> member.memberUuid } })
    }
}
