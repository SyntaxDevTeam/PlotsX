package pl.syntaxdevteam.plotsx.hooks

import net.coreprotect.CoreProtect
import net.coreprotect.CoreProtectAPI
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData

class CoreProtectHook(private val plugin: PlotsX) : Listener {
    private var coreProtectAPI: CoreProtectAPI? = null

    init {
        connect()
    }

    private fun connect(): Boolean {
        val pm = Bukkit.getPluginManager()
        val provider = pm.getPlugin("CoreProtect")
        if (provider == null) {
            plugin.logger.warning("CoreProtect plugin not found on server!")
            return false
        }

        if (!provider.isEnabled) {
            plugin.logger.warning(
                "CoreProtect ${provider.pluginMeta.version} is installed, but it is not enabled. " +
                    "Check the earlier CoreProtect startup error."
            )
            return false
        }

        return try {
            if (provider !is CoreProtect) {
                plugin.logger.warning(
                    "Plugin named CoreProtect has an unexpected main class: ${provider.javaClass.name}"
                )
                return false
            }

            val api = provider.api
            if (api.isEnabled) {
                coreProtectAPI = api
                plugin.logger.success("Hooked into CoreProtect ${provider.pluginMeta.version}!")
                api.testAPI()
                true
            } else {
                plugin.logger.warning("CoreProtect API is not enabled!")
                false
            }
        } catch (exception: IllegalStateException) {
            plugin.logger.warning("CoreProtect classloader is unavailable: ${exception.message}")
            false
        } catch (error: LinkageError) {
            plugin.logger.warning("CoreProtect API could not be linked: ${error.message}")
            false
        }
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("CoreProtect", ignoreCase = true) && coreProtectAPI == null) {
            connect()
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
        if (plot.extensions.isNotEmpty()) {
            plot.segments.forEach { restorePlot(plot.copy(x = it.x, z = it.z, radius = it.radius, extensions = emptyList()), timeSeconds) }
            return
        }
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
        if (plot.extensions.isNotEmpty()) return plot.segments.flatMap {
            getPlotHistory(plot.copy(x = it.x, z = it.z, radius = it.radius, extensions = emptyList()), timeSeconds)
        }

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
        if (plot.extensions.isNotEmpty()) {
            plot.segments.forEach { rollbackPlot(plot.copy(x = it.x, z = it.z, radius = it.radius, extensions = emptyList()), timeSeconds, player) }
            return
        }
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
