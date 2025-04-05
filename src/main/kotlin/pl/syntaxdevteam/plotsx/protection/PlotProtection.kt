package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Location
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.databases.Plot
import pl.syntaxdevteam.plotsx.databases.PlotFlag
import kotlin.math.pow
import kotlin.math.sqrt

object PlotProtection {
    private val plots = mutableListOf<Plot>()
    private val plotFlags = mutableListOf<PlotFlag>()

    fun isProtected(location: Location, player: Player): Boolean {
        return plots.any { plot ->
            val distance = sqrt((plot.x - location.x).pow(2.0) + (plot.z - location.z).pow(2.0))
            distance <= plot.radius && plot.ownerUuid != player.uniqueId.toString() && plot.world == location.world.name
        }
    }
    fun hasFlag(plotId: Int, flagKey: String): Boolean {
        return plotFlags.any { it.plotId == plotId && it.flagKey == flagKey }
    }
}
