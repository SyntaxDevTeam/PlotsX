package pl.syntaxdevteam.plotsx.permissions

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Centralny obiekt do sprawdzania uprawnień gracza w pluginie PlotsX.
 * Uwzględnia operatorów (OP) oraz standardowe node'y permissions.
 */
object PermissionChecker {
    private val AUTHOR_UUID: UUID = UUID.fromString("248e508c-28de-4a8f-a284-2c73cf917d15")

    /**
     * Lista wszystkich dostępnych uprawnień w pluginie PlotsX.
     * Rozszerz enum o nowe wartości według potrzeby.
     */
    enum class PermissionKey(val node: String) {
        // Zarządzanie działkami
        OWNER("plotsx.owner"),
        CLAIM_PLOT("plotsx.cmd.claim"),
        MANAGE_PLOT("plotsx.cmd.plot"),
        UNCLAIM_PLOT("plotsx.cmd.unclaim"),
        VISIT_PLOT("plotsx.plot.visit"),
        INFO_PLOT("plotsx.plot.info"),
        EXPAND_PLOT("plotsx.plot.expand"),

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

    fun has(player: CommandSender, key: PermissionKey): Boolean {
        if (player !is Player) return true
        if (player.isOp) return true
        if (player.hasPermission("*")) return true
        if (player.hasPermission("plotsx.*")) return true
        if (player.hasPermission(PermissionKey.OWNER.node)) return true

        return player.hasPermission(key.node)
    }

    fun isAuthor(uuid: UUID): Boolean {
        return uuid == AUTHOR_UUID
    }

    fun hasWithBypass(player: CommandSender, key: PermissionKey): Boolean {
        if (player !is Player) return true
        if (player.uniqueId == AUTHOR_UUID) return true
        if (player.isOp) return true
        return canBypassPlots(player) || has(player, key)
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
        PermissionKey.EXPAND_PLOT   -> "Rozszerzanie działki"
        PermissionKey.ADMIN_BYPASS  -> "Omijanie zabezpieczeń działek"
        PermissionKey.ADMIN_MANAGE  -> "Zarządzanie działkami globalnie"
        PermissionKey.OWNER        -> "Allows using the All PlotsX commands"
    }

    /**
     * Skrócone metody dla łatwiejszego użycia:
     */
    fun canCreatePlot(player: CommandSender) = has(player, PermissionKey.CLAIM_PLOT)
    fun canManagePlot(player: CommandSender)  = has(player, PermissionKey.MANAGE_PLOT)
    fun canUnclaimPlot(player: CommandSender) = has(player, PermissionKey.UNCLAIM_PLOT)
    fun canVisitPlot(player: CommandSender)    = has(player, PermissionKey.VISIT_PLOT)
    fun canInfoPlot(player: CommandSender)     = has(player, PermissionKey.INFO_PLOT)
    fun canExpandPlot(player: CommandSender)   = has(player, PermissionKey.EXPAND_PLOT)
    fun canBypassPlots(player: CommandSender)  = has(player, PermissionKey.ADMIN_BYPASS)
    fun canManagePlots(player: CommandSender)  = has(player, PermissionKey.ADMIN_MANAGE)
}

