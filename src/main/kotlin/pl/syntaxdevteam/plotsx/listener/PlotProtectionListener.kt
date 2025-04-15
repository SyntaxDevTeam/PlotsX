package pl.syntaxdevteam.plotsx.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData

class PlotProtectionListener(private val plugin: PlotsX) : Listener {

    /**
     * Pomocnicza metoda, która przeszukuje cache działek,
     * żeby znaleźć działkę, która obejmuje podane współrzędne.
     */
    private fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        return plugin.cacheManager.getCachedPlots().firstOrNull { plot ->
            plot.world.equals(world, ignoreCase = true) &&
                    x in (plot.x - plot.radius .. plot.x + plot.radius) &&
                    z in (plot.z - plot.radius .. plot.z + plot.radius)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)
        if (plot != null) {
            // Sprawdzamy, czy gracz ma pozwolenie (właściciel, członek lub flaga "build" ustawiona na true)
            if (!playerHasBuildPermission(player, plot)) {
                event.isCancelled = true
                player.sendMessage("Nie możesz stawiać bloków w tej działce!")
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)
        if (plot != null) {
            if (!playerHasBuildPermission(player, plot)) {
                event.isCancelled = true
                player.sendMessage("Nie możesz niszczyć bloków w tej działce!")
            }
        }
    }

    /**
     * Sprawdza, czy gracz ma pozwolenie na modyfikację działki.
     * Uznajemy, że właściciel lub członek działki (zdefiniowani w cache)
     * mają pełne uprawnienia, w przeciwnym razie sprawdzamy flagę "build".
     */
    private fun playerHasBuildPermission(player: Player, plot: PlotData): Boolean {
        // Jeśli gracz jest właścicielem, wszystko jest dozwolone
        if (plot.ownerUuid == player.uniqueId) return true

        // Sprawdź, czy gracz jest członkiem działki (używamy cache członków)
        val members = plugin.cacheManager.getMembers(plot.id) ?: emptyList()
        if (members.any { it.memberUuid == player.uniqueId.toString() }) return true

        // W przeciwnym razie sprawdzamy flagę "build"
        val flags = plugin.cacheManager.getFlags(plot.id)
        // Przyjmujemy, że flaga "build" ustawiona na true oznacza zezwolenie na modyfikację.
        // Jeśli nie ma flagi, przyjmujemy wartość false (brak pozwolenia).
        return flags?.get("build") ?: false
    }
}
