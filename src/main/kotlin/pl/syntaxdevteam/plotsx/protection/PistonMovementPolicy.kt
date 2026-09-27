package pl.syntaxdevteam.plotsx.protection

internal object PistonMovementPolicy {
    /** null is wilderness; mixing domains always means crossing a protected boundary. */
    fun isAllowed(domains: Set<Int?>, flagAllowed: (Int) -> Boolean): Boolean {
        if (domains.size != 1) return false
        val plotId = domains.singleOrNull() ?: return true
        return flagAllowed(plotId)
    }
}
