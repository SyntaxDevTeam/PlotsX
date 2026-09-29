package pl.syntaxdevteam.plotsx.databases

import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX

class Helpers(private var plugin: PlotsX) {
    fun borderDurationSeconds(): Int = plugin.config.getInt("plots.borderDisplaySeconds", 30).coerceAtLeast(1)

    fun visualizePlotBorder3D(player: Player, plot: PlotData, durationSec: Int = 10, stepXZ: Int = 1, stepY: Int = 4) {
        plugin.borderVisualizer.show(player, plot, durationSec, stepXZ)
    }

}
