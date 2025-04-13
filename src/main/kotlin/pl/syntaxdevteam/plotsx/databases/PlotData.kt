package pl.syntaxdevteam.plotsx.databases

import java.util.*

data class PlotData(
    val id: Int,
    val ownerUuid: UUID,
    val x: Int,
    val z: Int,
    val radius: Int,
    val world: String,
    val name: String,
    val creationTime: Long
)



