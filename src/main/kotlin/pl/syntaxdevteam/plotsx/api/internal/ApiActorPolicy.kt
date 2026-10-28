package pl.syntaxdevteam.plotsx.api.internal

import pl.syntaxdevteam.plotsx.cache.PlotCacheSnapshot
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID

/** Bukkit-free authorization evidence captured on the actor thread; mutable plot facts come from the current snapshot. */
internal data class ApiActorPolicy(val id: UUID?, val admin: Boolean, val roles: Set<String>,
                                   val actions: Set<String>, val defaults: Map<String, Set<String>>) {
    fun owner(plot: PlotData): Boolean = admin || (id != null && id == plot.ownerUuid)
    fun allowed(snapshot: PlotCacheSnapshot, plot: PlotData, action: String): Boolean {
        if (owner(plot)) return true
        if (action !in actions) return false
        val role = id?.let { snapshot.member(plot.id, it)?.memberRole } ?: return false
        if (role !in roles) return false
        return snapshot.flagValue(plot.id, "role_permission.$role.$action") ?: (action in defaults[role].orEmpty())
    }
}
