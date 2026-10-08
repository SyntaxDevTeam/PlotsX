# Folia compatibility review

Reviewed on 2026-10-08 against the [Paper/Folia scheduling documentation](https://docs.papermc.io/paper/dev/folia-support/).

## Changes

- Database workers and cache refreshes use the async scheduler adapter. Player GUI and command completions return to the player's entity scheduler; console completions use the global scheduler.
- Delayed block corrections and smart-door cleanup run on the owning region. Shared player tracking, door guards, open GUI registrations and API flag-provider tracking use concurrent collections.
- Shutdown avoids the unsupported Bukkit scheduler on Folia. GUI cleanup closes inventories only when the current thread owns the player.
- Border sessions use the player's entity scheduler. Terrain height is sampled only in loaded chunks owned by the calling region. Remote parts of a plot can therefore be omitted; request the border again after moving closer.
- Teleport destinations are checked on their chunk's region after asynchronous chunk loading. Fallback searches inspect one chunk at a time. Player teleport and result messages return to the entity scheduler and use `teleportAsync`.
- Classic and chunk purchase debit/validation callbacks run on the actor's entity scheduler. Refunds use captured offline account data on the global scheduler and do not require the actor to remain online. Both geometries use the same durable payment journal; shutdown or ambiguous provider responses leave recoverable evidence instead of a log-only refund obligation.
- Player-sensitive API operations check ownership of the actor entity thread.

## Validation and remaining limits

The unit/database suite passes with `./gradlew test --offline`. A live two-client acceptance run also passes on Folia 1.21.11 build 14: the players occupy distinct regions, a free classic GUI expansion commits once despite duplicate confirmation and leaves a committed journal record, the synchronous API safely rejects a recipient owned by another region, the asynchronous API transfers ownership, the command workflow transfers it back, reload preserves the owner, and shutdown completes without plugin exceptions. See [release evidence](release/1.0.0.md).

The `folia-supported` descriptor remains enabled, but this review does not certify every external provider or every runtime path. Economy providers, CoreProtect rollback/restore, WorldGuard and permission integrations need validation with the exact versions installed on the server. Ownership transfer captures the recipient's limits on their entity scheduler and writes on a worker. The synchronous API returns `RECIPIENT_WRONG_THREAD` for another region; integrations should use `transferOwnershipAsync()` without blocking entity threads.

For staging with additional integrations and versions, exercise two players in separate regions: claim/unclaim, flags, membership and ownership transfer, classic/chunk expansion (including disconnect and refund failures), rename/dialog expiry, remote/cross-world teleport, borders, private double chests, pistons and explosions across plot boundaries, configuration reload and shutdown. Check for region ownership exceptions and verify that failed purchases leave no missing refunds. Interrupted or ambiguous payment attempts remain in the durable journal for reconciliation; providers are never retried blindly.
