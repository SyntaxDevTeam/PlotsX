package pl.syntaxdevteam.plotsx.gui

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID

class PlotListGUITest {
    @Test
    fun `owned plots are listed before shared plots`() {
        val viewer = UUID.randomUUID()
        val other = UUID.randomUUID()
        val plots = listOf(
            plot(4, other),
            plot(3, viewer),
            plot(2, other),
            plot(1, viewer)
        )

        assertEquals(listOf(1, 3, 2, 4), PlotListGUI.orderPlots(plots, viewer).map { it.id })
    }

    private fun plot(id: Int, owner: UUID) = PlotData(
        id = id,
        ownerUuid = owner,
        x = id * 100,
        z = 0,
        y = 64,
        radius = 16,
        world = "world",
        name = "Plot$id",
        creationTime = 0
    )
}
