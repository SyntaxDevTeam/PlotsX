package pl.syntaxdevteam.plotsx.cache

import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.databases.PlotFlagData
import pl.syntaxdevteam.plotsx.databases.PlotMemberData
import java.util.concurrent.ConcurrentHashMap

class CacheManager(private val plugin: PlotsX) {

    private val databaseHandler = plugin.databaseHandler

    @Volatile private var plotCache = ConcurrentHashMap<Int, PlotData>()
    @Volatile private var flagCache = ConcurrentHashMap<Int, List<PlotFlagData>>()
    @Volatile private var memberCache = ConcurrentHashMap<Int, List<PlotMemberData>>()

    /** Public API - dostęp do cache */

    fun getPlot(plotId: Int): PlotData? = plotCache[plotId]
    fun reloadPlotSync(plotId: Int) {
        val plot = databaseHandler.getPlotById(plotId)
        if (plot == null) invalidatePlot(plotId) else plotCache[plotId] = plot
    }
    fun getFlags(plotId: Int): List<PlotFlagData>? = flagCache[plotId]
    fun reloadFlagsSync(plotId: Int) {
        flagCache[plotId] = databaseHandler.getPlotFlags(plotId)
    }
    fun getMembers(plotId: Int): List<PlotMemberData>? = memberCache[plotId]
    fun reloadMembersSync(plotId: Int) {
        memberCache[plotId] = databaseHandler.getPlotMembers(plotId)
    }

    fun getCachedPlots(): Collection<PlotData> = plotCache.values

    /** Public API - inicjowanie asynchronicznego odświeżania cache */

    fun refreshAllCachesAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            refreshPlotCacheSync()
            refreshFlagCacheSync()
            refreshMemberCacheSync()
            plugin.logger.debug("[Cache] Cache odświeżony (działki, flagi, członkowie).")
        })
    }

    fun refreshPlotCacheAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            refreshPlotCacheSync()
            plugin.logger.debug("[Cache] Plot cache odświeżony.")
        })
    }

    fun refreshFlagCacheAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            refreshFlagCacheSync()
            plugin.logger.debug("[Cache] Flag cache odświeżony.")
        })
    }

    fun refreshMemberCacheAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            refreshMemberCacheSync()
            plugin.logger.debug("[Cache] Member cache odświeżony.")
        })
    }

    /** Private sync logic wykonywana asynchronicznie z zewnątrz */

    private fun refreshPlotCacheSync() {
        val allPlots = databaseHandler.getPlotsFromAllUsers()
        // Publish a complete replacement so asynchronous API reads never see a half-filled map.
        plotCache = ConcurrentHashMap(allPlots.associateBy { it.id })
    }

    private fun refreshFlagCacheSync() {
        flagCache = ConcurrentHashMap(plotCache.keys.associateWith { databaseHandler.getPlotFlags(it) })
    }

    private fun refreshMemberCacheSync() {
        memberCache = ConcurrentHashMap(plotCache.keys.associateWith { databaseHandler.getPlotMembers(it) })
    }

    /** Możliwość dodania lub zaktualizowania pojedynczego wpisu */

    fun updatePlotCacheAsync(plotId: Int) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val plot = databaseHandler.getPlotById(plotId)
            if (plot != null) plotCache[plotId] = plot
        })
    }

    fun updateFlagCacheAsync(plotId: Int, onComplete: () -> Unit = {}) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val flags = databaseHandler.getPlotFlags(plotId)
            flagCache[plotId] = flags
            plugin.server.scheduler.runTask(plugin, onComplete)
        })
    }

    fun updateMemberCacheAsync(plotId: Int) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val members = databaseHandler.getPlotMembers(plotId)
            memberCache[plotId] = members
        })
    }

    fun invalidatePlot(plotId: Int) {
        plotCache.remove(plotId)
        flagCache.remove(plotId)
        memberCache.remove(plotId)
    }

    /** Synchronous clear **/
    fun clearAllCaches() {
        plotCache.clear()
        flagCache.clear()
        memberCache.clear()
        //plugin.logger.debug("Cache został wyczyszczony synchronicznie (działki, flagi, członkowie).")
    }

    /** Synchronous reload of all caches **/
    fun reloadAllCachesSync() {
        refreshPlotCacheSync()
        refreshFlagCacheSync()
        refreshMemberCacheSync()
        //plugin.logger.debug("Cache odświeżony synchronicznie (działki, flagi, członkowie).")
    }
}
