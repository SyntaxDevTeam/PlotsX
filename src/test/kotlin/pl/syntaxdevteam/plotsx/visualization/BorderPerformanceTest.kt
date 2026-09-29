package pl.syntaxdevteam.plotsx.visualization

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.io.File
import java.util.UUID

/** Deterministic complexity checks plus an opt-in local timing report; timings are not CI gates. */
class BorderPerformanceTest {
    @Test fun `border work remains bounded by exposed perimeter for representative plot sizes`() {
        for (count in listOf(1, 8, 16, 32, 64)) {
            val plot = linePlot(count)
            val points = BorderOutline.create(plot, 2)
            assertTrue("$count chunks produced ${points.size} points", points.size <= count * 16 + 32)
        }
    }

    @Test fun `record border preparation cost by chunk count`() {
        if (System.getenv("PLOTSX_BENCHMARK") != "1") return
        val report = StringBuilder("# Plot expansion and border benchmark\n\n")
        report.append("| Chunks | Points | Average preparation µs |\n|---:|---:|---:|\n")
        for (count in listOf(1, 8, 16, 32, 64, 128)) {
            val plot = linePlot(count)
            repeat(2_000) { BorderOutline.create(plot, 2) }
            val started = System.nanoTime()
            repeat(10_000) { BorderOutline.create(plot, 2) }
            val averageMicros = (System.nanoTime() - started) / 10_000.0 / 1_000.0
            report.append("| $count | ${BorderOutline.create(plot, 2).size} | $averageMicros |\n")
        }
        File("build/reports/expansion-benchmark.md").apply { parentFile.mkdirs(); writeText(report.toString()) }
    }

    private fun linePlot(count: Int) = PlotData(
        1, UUID(0, 1), 0, 0, 64, null, "world", "benchmark", 0,
        chunks = (0 until count).mapTo(linkedSetOf()) { ChunkPosition(it, 0) }
    )
}
