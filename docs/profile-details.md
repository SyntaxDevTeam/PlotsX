# Owner profile read API

Read-only persisted snapshots for MiniPortalPlayerProfile 0.3.0. Existing snapshot constructors and existing API methods remain unchanged. New optional owner-list method provides composed detailed snapshots. No entity/chunk loading, teleport, database writes or simulation updates.

getOwnedPlotProfiles(UUID) uses the existing plot cache and owner filter. PlotProfileSnapshot wraps the original plot and nullable configured teleport block. TP X/Z are integer block coordinates (in-game destination uses +0.5). It is not a safe destination guarantee: the normal teleport flow revalidates blocks and can select a fallback. A missing configured point must not be replaced with a fabricated TP.

## Kotlin runtime packaging (1.0.0-profile.2)

The bootstrap loader is Java and uses only Paper-provided Java dependencies. `paper-libraries.yml` explicitly resolves `kotlin-stdlib` matching the Kotlin compiler. Kotlin runtime classes are excluded from the shaded plugin JAR, so PlotsX and SyntaxDB share the Kotlin classes in Paper's library classloader. Bundling Kotlin in the plugin while resolving SyntaxDB externally creates incompatible `kotlin.jvm.functions.Function1` identities and fails at `Database.connect` during enable.

`build` runs `verifyRuntimePackaging`: it rejects bundled Kotlin classes, a missing or unexpanded Kotlin runtime coordinate, and Kotlin references in the bootstrap loader. Unit tests cover repository routing and runtime manifest parsing. The disposable Paper acceptance plugin checks the actual `Function1` identity used by SyntaxDB and PlotsX before exercising SQLite and plot protection. Its seed data uses scoped SyntaxDB connections.

Update only the PlotsX JAR while the server is stopped, remove the previous duplicate, and restart fully. Keep the existing PlotsX configuration and database. No schema or player-data changes are part of this packaging fix.
