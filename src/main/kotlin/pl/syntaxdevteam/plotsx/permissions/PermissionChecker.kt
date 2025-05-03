package pl.syntaxdevteam.plotsx.permissions

import org.bukkit.entity.Player

/**
 * Centralny obiekt do sprawdzania uprawnień gracza w pluginie PlotsX.
 * Uwzględnia operatorów (OP) oraz standardowe node'y permissions.
 */
object PermissionChecker {

    /**
     * Lista wszystkich dostępnych uprawnień w pluginie PlotsX.
     * Rozszerz enum o nowe wartości według potrzeby.
     */
    enum class PermissionKey(val node: String) {
        // Zarządzanie działkami
        CLAIM_PLOT("plotsx.cmd.claim"),
        MANAGE_PLOT("plotsx.cmd.plot"),
        UNCLAIM_PLOT("plotsx.cmd.unclaim"),
        VISIT_PLOT("plotsx.plot.visit"),
        INFO_PLOT("plotsx.plot.info"),

        // Administracyjne
        ADMIN_BYPASS("plotsx.admin.bypass"),
        ADMIN_MANAGE("plotsx.admin.manage");

        override fun toString(): String = node
    }

    /**
     * Sprawdza, czy gracz ma dane uprawnienie zdefiniowane w [PermissionKey].
     * Operatorzy (OP) zawsze mają dostęp.
     *
     * @param player gracz, którego uprawnienia sprawdzamy
     * @param key wartość z enum [PermissionKey]
     * @return true jeśli gracz jest OP lub ma ustawione permission node, false w przeciwnym wypadku
     */
    fun has(player: Player, key: PermissionKey): Boolean {
        // OP zawsze ma pełne uprawnienia
        if (player.isOp) return true
        return player.hasPermission(key.node)
    }

    /**
     * Czytelne nazwy uprawnień do komunikatów lub logów.
     */
    fun displayName(key: PermissionKey): String = when (key) {
        PermissionKey.CLAIM_PLOT   -> "Tworzenie działki"
        PermissionKey.MANAGE_PLOT    -> "Zarządzanie działką"
        PermissionKey.UNCLAIM_PLOT  -> "Usuwanie działki"
        PermissionKey.VISIT_PLOT    -> "Teleport do działki"
        PermissionKey.INFO_PLOT     -> "Informacje o działce"
        PermissionKey.ADMIN_BYPASS  -> "Omijanie zabezpieczeń działek"
        PermissionKey.ADMIN_MANAGE  -> "Zarządzanie działkami globalnie"
    }

    /**
     * Skrócone metody dla łatwiejszego użycia:
     */
    fun canCreatePlot(player: Player) = has(player, PermissionKey.CLAIM_PLOT)
    fun canManagePlot(player: Player)  = has(player, PermissionKey.MANAGE_PLOT)
    fun canUnclaimPlot(player: Player) = has(player, PermissionKey.UNCLAIM_PLOT)
    fun canVisitPlot(player: Player)    = has(player, PermissionKey.VISIT_PLOT)
    fun canInfoPlot(player: Player)     = has(player, PermissionKey.INFO_PLOT)
    fun canBypassPlots(player: Player)  = has(player, PermissionKey.ADMIN_BYPASS)
    fun canManagePlots(player: Player)  = has(player, PermissionKey.ADMIN_MANAGE)
}


