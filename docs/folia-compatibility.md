# Folia compatibility review

Reviewed on 2026-10-08 against the [Paper/Folia scheduling documentation](https://docs.papermc.io/paper/dev/folia-support/).

## Changes

- Database workers and cache refreshes use the async scheduler adapter. Player GUI and command completions return to the player's entity scheduler; console completions use the global scheduler.
- Delayed block corrections and smart-door cleanup run on the owning region. Shared player tracking, door guards, open GUI registrations and API flag-provider tracking use concurrent collections.
- Shutdown avoids the unsupported Bukkit scheduler on Folia. GUI cleanup closes inventories only when the current thread owns the player.
- Border sessions use the player's entity scheduler. Terrain height is sampled only in loaded chunks owned by the calling region. Remote parts of a plot can therefore be omitted; request the border again after moving closer.
- Teleport destinations are checked on their chunk's region after asynchronous chunk loading. Fallback searches inspect one chunk at a time. Player teleport and result messages return to the entity scheduler and use `teleportAsync`.
- Chunk purchase economy/validation callbacks run on the actor's entity scheduler. A disconnected actor completes the pending callback exceptionally rather than leaving it to time out. Classic expansion reports `REFUND REQUIRED` if the player retires before a failed purchase can be refunded.
- Player-sensitive API operations check ownership of the actor entity thread.

## Validation and remaining limits

Compilation and the existing unit/database tests pass with `./gradlew compileKotlin test --offline`. These checks do not execute Folia's runtime thread assertions.

The `folia-supported` descriptor remains enabled, but this review does not certify every external provider or every runtime path. Economy providers, CoreProtect rollback/restore, WorldGuard and permission integrations need validation with the exact versions installed on the server. In particular, ownership transfer currently reads the recipient's permission limits from the initiating command/API context; transferring to a recipient in another region requires runtime verification and potentially a separate recipient callback. The synchronous API cannot transparently wait for another entity's region.

Before claiming full Folia support, exercise two players in separate regions: claim/unclaim, flags, membership and ownership transfer, classic/chunk expansion (including disconnect and refund failures), rename/dialog expiry, remote/cross-world teleport, borders, private double chests, pistons and explosions across plot boundaries, configuration reload and shutdown. Check for region ownership exceptions and verify that failed purchases leave no missing refunds. Classic expansion disconnect refunds remain explicitly reported for manual reconciliation.
