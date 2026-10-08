package pl.syntaxdevteam.plotsx.compatibility.platform

import org.bukkit.command.CommandSender
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import pl.syntaxdevteam.core.platform.ServerEnvironment

interface SchedulerAdapter {
    fun runAsync(task: Runnable)
    fun runSync(task: Runnable)
    fun runSyncLater(delayTicks: Long, task: Runnable)
    fun runRegionally(location: Location, task: Runnable)
    fun runForPlayer(player: Player, task: Runnable, retired: Runnable? = null)
    fun runRegionallyLater(location: Location, delayTicks: Long, task: Runnable)
    fun runForSender(sender: CommandSender, task: Runnable) {
        if (sender is Player) runForPlayer(sender, task) else runSync(task)
    }
    fun isFoliaBased(): Boolean
}

class BukkitSchedulerAdapter(
    private val plugin: Plugin,
    foliaBasedOverride: Boolean? = null
) : SchedulerAdapter {

    private val foliaBased: Boolean = foliaBasedOverride ?: ServerEnvironment.isFoliaBased()

    override fun runAsync(task: Runnable) {
        if (foliaBased) {
            plugin.server.asyncScheduler.runNow(plugin) { _ -> task.run() }
        } else {
            plugin.server.scheduler.runTaskAsynchronously(plugin, task)
        }
    }

    override fun runSync(task: Runnable) {
        if (foliaBased) {
            plugin.server.globalRegionScheduler.execute(plugin) { task.run() }
        } else {
            plugin.server.scheduler.runTask(plugin, task)
        }
    }

    override fun runSyncLater(delayTicks: Long, task: Runnable) {
        if (foliaBased) {
            plugin.server.globalRegionScheduler.runDelayed(plugin, { task.run() }, delayTicks.coerceAtLeast(1))
        } else {
            plugin.server.scheduler.runTaskLater(plugin, task, delayTicks)
        }
    }

    override fun runRegionally(location: Location, task: Runnable) {
        if (foliaBased) {
            plugin.server.regionScheduler.execute(plugin, location, task)
        } else {
            runSync(task)
        }
    }

    override fun runForPlayer(player: Player, task: Runnable, retired: Runnable?) {
        if (foliaBased) {
            if (!player.scheduler.execute(plugin, task, retired, 1L)) retired?.run()
        } else {
            runSync(task)
        }
    }

    override fun runRegionallyLater(location: Location, delayTicks: Long, task: Runnable) {
        plugin.server.regionScheduler.runDelayed(plugin, location, { task.run() }, delayTicks.coerceAtLeast(1))
    }

    override fun isFoliaBased(): Boolean = foliaBased
}
