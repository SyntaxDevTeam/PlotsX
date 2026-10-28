package pl.syntaxdevteam.plotsx.cache

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.*
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.io.File
import java.util.UUID

/** Opt-in comparison of complete rebuilding and targeted publication; no timing assertions in CI. */
class CachePublicationBenchmarkTest {
    @Test fun `record targeted metadata publication against full rebuild`() {
        if (System.getenv("PLOTSX_BENCHMARK") != "1") return
        val report = StringBuilder("# Cache publication benchmark\n\n")
        report.append("Synthetic in-memory publication only; excludes JDBC, server events and database latency.\n")
        report.append("Median of 20 measurements after 10 warmups per path. JVM: ${System.getProperty("java.version")}.\n\n")
        report.append("| Plots (one chunk each) | Full rebuild ms | Targeted metadata ms |\n|---:|---:|---:|\n")
        for (count in listOf(1_000, 10_000)) {
            val owner = UUID(0, 1)
            val plots = (1..count).map { id -> PlotData(id, owner, id * 32, 0, 64, null, "world", "Plot$id", 0,
                chunks = setOf(ChunkPosition(id * 2, 0))) }
            val flags = plots.associate { it.id to listOf(PlotFlagData(it.id, "build", "false")) }
            val snapshot = PlotCacheSnapshot.create(plots, flags)
            val changed = plots.first().copy(name = "Renamed")
            val changedPlots = listOf(changed) + plots.drop(1)
            fun full() = PlotCacheSnapshot.create(changedPlots, flags)
            fun targeted() = snapshot.withPlot(changed.id, changed, flags.getValue(changed.id), emptyList())
            repeat(10) { full(); targeted() }
            fun median(action: () -> PlotCacheSnapshot): Double {
                val times = LongArray(20) {
                    val start = System.nanoTime()
                    val result = action()
                    val duration = System.nanoTime() - start
                    assertEquals("Renamed", result.at("world", changed.x, 0)?.name)
                    duration
                }.sorted()
                return (times[9] + times[10]) / 2_000_000.0
            }
            report.append("| $count | ${median(::full)} | ${median(::targeted)} |\n")
        }
        File("build/reports/cache-publication-benchmark.md").apply { parentFile.mkdirs(); writeText(report.toString()) }
    }
}
