# Owner profile read API

Read-only persisted snapshots for MiniPortalPlayerProfile 0.3.0. Existing snapshot constructors and existing API methods remain unchanged. New optional owner-list method provides composed detailed snapshots. No entity/chunk loading, teleport, database writes or simulation updates.

getOwnedPlotProfiles(UUID) uses the existing plot cache and owner filter. PlotProfileSnapshot wraps the original plot and nullable configured teleport block. TP X/Z are integer block coordinates (in-game destination uses +0.5). It is not a safe destination guarantee: the normal teleport flow revalidates blocks and can select a fallback. A missing configured point must not be replaced with a fabricated TP.
