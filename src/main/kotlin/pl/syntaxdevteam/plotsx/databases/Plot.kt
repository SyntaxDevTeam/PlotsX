package pl.syntaxdevteam.plotsx.databases

data class Plot(
    val id: Int,
    val ownerUuid: String,
    val x: Int,
    val z: Int,
    val radius: Int,
    val world: String,
    val name: String,
    val creationTime: Long,
    val expirationTime: Long?,
    val members: List<PlotMember>,
    val flags: List<PlotFlag>,
    val logs: List<PlotLog>
)