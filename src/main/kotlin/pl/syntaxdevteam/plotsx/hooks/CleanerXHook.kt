package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.Bukkit
import pl.syntaxdevteam.cleanerx.api.CleanerXAPI

/**
 * Obiekt hooka dla CleanerX, umożliwiający opcjonalną integrację.
 *
 * CleanerX nie jest wymagany do działania LegacyTeamX, dlatego ta klasa
 * zapewnia bezpieczny sposób na sprawdzenie dostępności API CleanerX.
 */
object CleanerXHook {
    /**
     * Pobiera instancję CleanerXAPI, jeśli jest dostępna.
     *
     * @return Instancja CleanerXAPI, jeśli CleanerX jest zainstalowany i aktywny;
     *         w przeciwnym razie `null`.
     */
    fun getApi(): CleanerXAPI? {
        return Bukkit.getPluginManager().getPlugin("CleanerX")
            ?.takeIf { it.isEnabled }
            ?.let { Bukkit.getServicesManager().load(CleanerXAPI::class.java) }
    }
}
