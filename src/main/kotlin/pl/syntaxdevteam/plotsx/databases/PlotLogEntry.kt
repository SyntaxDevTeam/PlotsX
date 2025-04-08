package pl.syntaxdevteam.plotsx.databases

import java.util.*

data class PlotLogEntry(
    val plotId: Int,
    val action: String,
    val actorUUID: UUID,
    val timestamp: Long
)
