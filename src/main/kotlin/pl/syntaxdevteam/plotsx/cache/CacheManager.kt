package pl.syntaxdevteam.plotsx.cache

import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotMemberData

class CacheManager(private val plugin: PlotsX) {
    private val databaseHandler = plugin.databaseHandler
    private val snapshots = SnapshotStore(PlotCacheSnapshot.create())
    private val decisionSnapshot = ThreadLocal<PlotCacheSnapshot>()

    internal fun <T> withDecisionSnapshot(action: () -> T): T {
        val previous = decisionSnapshot.get()
        decisionSnapshot.set(previous ?: snapshots.read())
        try { return action() }
        finally { if (previous == null) decisionSnapshot.remove() else decisionSnapshot.set(previous) }
    }

    fun getPlot(plotId: Int): PlotData? = readSnapshot().plots[plotId]
    fun getPlotAt(world: String, x: Int, z: Int): PlotData? = readSnapshot().at(world, x, z)
    fun getFlags(plotId: Int): List<PlotFlagData>? = readSnapshot().flags[plotId]
    fun getMembers(plotId: Int): List<PlotMemberData>? = readSnapshot().members[plotId]
    fun getCachedPlots(): Collection<PlotData> = readSnapshot().plots.values
    internal fun readSnapshot(): PlotCacheSnapshot = decisionSnapshot.get() ?: snapshots.read()

    fun reloadPlotSync(plotId: Int) {
        snapshots.reload { current ->
            val loaded = databaseHandler.loadPlotCacheData(plotId)
            val plot = loaded.plots.singleOrNull()
            val plots = current.plots.toMutableMap()
            if (plot == null) plots.remove(plotId) else plots[plotId] = plot
            val flags = if (plot == null) current.flags - plotId
                else current.flags + (plotId to loaded.flags[plotId].orEmpty())
            val members = if (plot == null) current.members - plotId
                else current.members + (plotId to loaded.members[plotId].orEmpty())
            if (plot != null && current.plots[plotId] == plot) current.withMetadata(flags, members)
            else PlotCacheSnapshot.create(plots.values, flags, members)
        }
    }

    fun reloadFlagsSync(plotId: Int) {
        reloadPlotSync(plotId)
    }
    fun reloadMembersSync(plotId: Int) {
        reloadPlotSync(plotId)
    }
    private fun async(task: () -> Unit) = plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable(task))
    fun refreshAllCachesAsync() { async { reloadAllCachesSync() } }
    // A new plot must not become visible before its flags and membership are available.
    fun refreshPlotCacheAsync() { async { reloadAllCachesSync() } }
    fun refreshFlagCacheAsync() {
        async { reloadAllCachesSync() }
    }
    fun refreshMemberCacheAsync() {
        async { reloadAllCachesSync() }
    }
    fun updatePlotCacheAsync(plotId: Int) { async { reloadPlotSync(plotId) } }
    fun updateFlagCacheAsync(plotId: Int, onComplete: () -> Unit = {}) {
        async {
            reloadFlagsSync(plotId)
            plugin.server.scheduler.runTask(plugin, onComplete)
        }
    }
    fun updateMemberCacheAsync(plotId: Int) { async { reloadMembersSync(plotId) } }
    fun invalidatePlot(plotId: Int) {
        snapshots.reload { current ->
            PlotCacheSnapshot.create((current.plots - plotId).values, current.flags - plotId, current.members - plotId)
        }
    }
    fun clearAllCaches() { snapshots.clear(PlotCacheSnapshot.create()) }
    fun reloadAllCachesSync() {
        snapshots.reload {
            val loaded = databaseHandler.loadPlotCacheData()
            PlotCacheSnapshot.create(loaded.plots, loaded.flags, loaded.members)
        }
    }
}
