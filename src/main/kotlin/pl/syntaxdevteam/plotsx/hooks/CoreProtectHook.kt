package pl.syntaxdevteam.plotsx.hooks

import net.coreprotect.CoreProtect
import net.coreprotect.CoreProtectAPI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData

class CoreProtectHook(private val plugin: PlotsX) {
    private var coreProtectAPI: CoreProtectAPI? = null

    init {
        checkCoreProtect()
    }

    private fun checkCoreProtect() {
        val pm = Bukkit.getPluginManager()
        if (!pm.isPluginEnabled("CoreProtect")) {
            plugin.logger.warning("CoreProtect plugin not found on server!")
            return
        }

        val provider = pm.getPlugin("CoreProtect")
        if (provider is CoreProtect && provider.isEnabled) {
            val api = provider.api
            if (api.isEnabled) {
                coreProtectAPI = api
                plugin.logger.debug("Hooked into CoreProtect!")
                api.testAPI()
            } else {
                plugin.logger.warning("CoreProtect API is not enabled!")
            }
        } else {
            plugin.logger.warning("CoreProtect provider is null or disabled!")
        }
    }

    fun isEnabled(): Boolean = coreProtectAPI != null

    /** Logowanie stawiania bloku */
    fun logBlockPlace(player: Player, block: Block) {
        coreProtectAPI?.logPlacement(
            player.name,
            block.location,
            block.type,
            block.blockData
        )
    }

    /** Logowanie łamania bloku */
    fun logBlockBreak(player: Player, block: Block) {
        coreProtectAPI?.logRemoval(
            player.name,
            block.location,
            block.type,
            block.blockData
        )
    }

    /** Logowanie transakcji w kontenerze */
    fun logContainerTransaction(player: Player, block: Block) {

        coreProtectAPI?.logContainerTransaction(
            player.name,
            block.location
        )
    }

    /**
     * Przywraca działkę do stanu sprzed given czasu (sekundy wstecz)
     */
    fun restorePlot(plot: PlotData, timeSeconds: Long) {
        val api = coreProtectAPI ?: return
        val seconds = timeSeconds.toInt()
        val world = Bukkit.getWorld(plot.world) ?: return
        val centerY = world.spawnLocation.y
        val center = Location(world, plot.x.toDouble(), centerY, plot.z.toDouble())

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            api.performRestore(
                seconds,
                null,
                null,
                null,
                null,
                null,
                plot.radius,
                center
            )
        })
    }

    /**
     * Zwraca historię zmian na działce (sekundy wstecz)
     */
    fun getPlotHistory(plot: PlotData, timeSeconds: Long): List<Array<String>> {
        val api = coreProtectAPI ?: return emptyList()
        val seconds = timeSeconds.toInt()
        val world = Bukkit.getWorld(plot.world) ?: return emptyList()
        val centerY = world.spawnLocation.y
        val center = Location(world, plot.x.toDouble(), centerY, plot.z.toDouble())

        return api.performLookup(
            seconds,
            null,
            null,
            null,
            null,
            null,
            plot.radius,
            center
        ) ?: emptyList()
    }

    /**
     * Rollback działki do stanu sprzed given czasu (sekundy wstecz)
     * Jeśli podano `player`, cofa tylko akcje tej osoby.
     */
    fun rollbackPlot(plot: PlotData, timeSeconds: Long, player: Player? = null) {
        val api = coreProtectAPI ?: return
        val seconds = timeSeconds.toInt()
        val world = Bukkit.getWorld(plot.world) ?: return
        val centerY = world.spawnLocation.y
        val center = Location(world, plot.x.toDouble(), centerY, plot.z.toDouble())

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            api.performRollback(
                seconds,
                player?.let { listOf(it.name) },
                null,
                null,
                null,
                null,
                plot.radius,
                center
            )
        })
    }
}
