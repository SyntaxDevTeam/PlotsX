package pl.syntaxdevteam.plotsx.databases

data class PlotLog(
    val id: Int,
    val plotId: Int,
    val action: String,
    val actorUuid: String,
    val timestamp: Long
)