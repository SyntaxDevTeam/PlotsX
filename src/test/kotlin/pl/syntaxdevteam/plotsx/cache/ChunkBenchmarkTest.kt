package pl.syntaxdevteam.plotsx.cache

import org.junit.Test
import org.junit.Assert.*
import pl.syntaxdevteam.plotsx.databases.*
import pl.syntaxdevteam.plotsx.geometry.*
import java.io.File
import java.util.UUID

/** Opt-in measurement, not a flaky timing gate. Dataset generation is excluded from lookup timings. */
class ChunkBenchmarkTest {
    @Test fun `record mixed resolver percentiles and approximate retained heap`() {
        if (System.getenv("PLOTSX_BENCHMARK") != "1") return
        val report = StringBuilder("# Local mixed cache benchmark\n\n")
        report.append("JVM: ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}\n\n")
        report.append("OS: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}; CPUs: ${Runtime.getRuntime().availableProcessors()}\n\n")
        report.append("100000 warmups and 50000 deterministic hit/miss lookups per size. Heap delta is approximate after GC, includes frozen models and index, excludes input. No SQL/world/chunk access. Times in ns; publication in ms.\n\n")
        report.append("| Chunks + 101 classic plots | Build ms | Heap delta bytes | p50 ns | p95 ns | p99 ns |\n|---|---|---|---|---|---|\n")
        val owner = UUID(0,1)
        fun heap(): Long { System.gc(); return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() }
        for (count in listOf(1000, 10000, 100000)) {
            val input = (0 until count).map { i ->
                PlotData(i+1,owner,i*32,0,64,null,"world","C$i",0,chunks=setOf(ChunkPosition(i*2,0)))
            } + (0..100).map { i -> PlotData(count+i+1,owner,i*4096,1000000,64,if(i==100) 10000 else 16,"world","L$i",0) }
            val before = heap()
            val start = System.nanoTime()
            val snapshot = PlotCacheSnapshot.create(input)
            val build = (System.nanoTime()-start)/1e6
            val retained = heap()-before
            fun lookup(i: Int): PlotData? = snapshot.at("world", ((i.toLong()*7919)%count).toInt()*32 + if(i%2==0) 0 else 16, 0)
            repeat(100000) { lookup(it) }
            val durations = LongArray(50000) { i ->
                val t = System.nanoTime(); val found=lookup(i); val elapsed=System.nanoTime()-t
                assertEquals(i%2==0,found!=null);elapsed
            }.sorted()
            report.append("| $count | $build | $retained | ${durations[25000]} | ${durations[47500]} | ${durations[49500]} |\n")
        }
        report.append("\nSynthetic local fixture; not a production TPS or total server heap budget. E8 staging and workload-specific acceptance remain separate.\n")
        File("build/reports/chunk-benchmark.md").apply { parentFile.mkdirs(); writeText(report.toString()) }
    }
}
