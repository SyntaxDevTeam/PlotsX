# Local mixed cache benchmark

JVM: OpenJDK 64-Bit Server VM 21.0.12.1

OS: Linux amd64; CPUs: 16

100000 warmups and 50000 deterministic hit/miss lookups per size. Heap delta is approximate after GC, includes frozen models and index, excludes input. No SQL/world/chunk access. Times in ns; publication in ms.

| Chunks + 101 classic plots | Build ms | Heap delta bytes | p50 ns | p95 ns | p99 ns |
|---|---|---|---|---|---|
| 1000 | 12.238207 | 769696 | 149 | 237 | 348 |
| 10000 | 22.36564 | 4477176 | 136 | 407 | 1153 |
| 100000 | 126.86559 | 46388496 | 291 | 543 | 679 |

Synthetic local fixture; not a production TPS or total server heap budget. E8 staging and workload-specific acceptance remain separate.
