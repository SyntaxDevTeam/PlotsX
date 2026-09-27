package pl.syntaxdevteam.plotsx.protection

internal object AffectedPlotsPolicy {
    /** An environmental action is allowed only when every affected claimed plot permits it. */
    fun isAllowed(plotIds: Iterable<Int>, flagAllowed: (Int) -> Boolean): Boolean =
        plotIds.distinct().all(flagAllowed)
}
