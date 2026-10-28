# API — integrating with PlotsX

[← Home](Home.md) · [Polski](../API.md)

The API lets other plugins read plots, check protection rules, and manage members and settings. Additional menus, gameplay features, and administration tools can use the same rules as PlotsX.

Administrators do not need to write code to use PlotsX itself. This page is for integration developers. Ready-to-use connections are described under [Integrations](Integrations.md).

## Requirements and dependency

One API supports both classic and chunk-based plots. It requires Java 21 and the Paper API; Folia integrations must follow the threading rules below. Match the API library version to the installed PlotsX version. The current development version is `1.1.0-R0.1-SNAPSHOT`; the upcoming release is **1.1.0**.

Gradle Kotlin DSL:

```kotlin
repositories {
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/")
}

dependencies {
    compileOnly("pl.syntaxdevteam:plotsx-api:1.1.0-R0.1-SNAPSHOT")
}
```

For stable releases, choose the matching version without the `-SNAPSHOT` suffix. With Maven, use the same coordinates and the `provided` scope. You can also use the `PlotsX-<version>-api.jar` matching a local plugin build. Do not bundle API classes in your own JAR: PlotsX supplies them at runtime.

For an optional integration, add this to your plugin's `paper-plugin.yml`:

```yaml
dependencies:
  server:
    PlotsX:
      load: BEFORE
      required: false
      join-classpath: true
```

Set `required: true` for a required integration. The equivalents in a classic `plugin.yml` are `softdepend: [PlotsX]` and `depend: [PlotsX]`. Put optional integration code in a separate class and load it only after checking that PlotsX is enabled.

## Obtaining the API

Public classes are in `pl.syntaxdevteam.plotsx.api`. Obtain the service through Bukkit's `ServicesManager`. Do not use PlotsX's internal classes or database.

Kotlin example, inside your integration class:

```kotlin
import pl.syntaxdevteam.plotsx.api.PlotsXApi

val api = server.servicesManager.load(PlotsXApi::class.java) ?: return
val location = player.location
val plot = api.getPlotAt(location.world.name, location.blockX, location.blockZ)
val owned = api.getOwnedPlots(player.uniqueId)
val accessible = api.getAccessiblePlots(player.uniqueId)
```

Java:

```java
import org.bukkit.Location;
import pl.syntaxdevteam.plotsx.api.PlotsXApi;
import pl.syntaxdevteam.plotsx.api.PlotSnapshot;

PlotsXApi api = getServer().getServicesManager().load(PlotsXApi.class);
if (api == null) return;
Location location = player.getLocation();
PlotSnapshot plot = api.getPlotAt(location.getWorld().getName(),
        location.getBlockX(), location.getBlockZ());
```

## Available operations

| Area | Methods and data |
| --- | --- |
| Plot lookup | `getPlot`, `getPlotAt`, `getPlots`, `getOwnedPlots`, `getAccessiblePlots` |
| Saved teleport points | `getOwnedPlotProfiles`, `PlotProfileSnapshot.teleport` |
| Members | `getMembers`, `isMember`, `addMember`, `addMemberAsync`, `removeMember`, `removeMemberAsync` |
| Roles and grants | `getRoles`, `getRolePermissions`, `setMemberRole`, `setMemberRoleAsync`, `setRolePermission`, `setRolePermissionAsync` |
| Ownership | `transferOwnershipAsync` |
| Protection and settings | `getFlags`, `getFlagValue`, `evaluateFlag`, `setFlag`, `setFlagAsync` |
| Management permissions | `canManage` |
| Custom flags | `registerFlag`, `unregisterFlag` |
| Change notifications | `PlotFlagChangedEvent` |

The existing synchronous write methods are also available. Prefer the `Async` variants for new integrations so database writes do not block gameplay.

Creating, deleting, and expanding plots are outside this API. Those operations involve additional economy, limit, and land-collision rules.

## Plots, geometry, and teleport points

A `PlotSnapshot` describes a plot at the time of the read. Snapshots and lists are immutable; use the appropriate API method to change settings. Several consecutive reads are not one transaction: a player may change the plot between calls.

| Property | Classic plot | Chunk plot |
| --- | --- | --- |
| `geometryType` | `classic` | `chunks` |
| `radius` | Radius of the original square | `null` |
| `extensions` | Purchased square segments | Empty list |
| `chunks` | Empty list | Coordinates of claimed chunks |
| `area` | Sum of segment areas | Chunk count × 256 |

`contains(world, x, z)` checks the actual plot shape. `containsArea(...)` checks an entire rectangle, and `containsCircle(...)` an entire circle; checking corners alone can miss unclaimed gaps in an irregular plot. `geometryRevision` identifies geometry changes.

`isMember` checks explicitly added members; the owner is the separate `owner` field. `getAccessiblePlots` returns owned plots and memberships without an administrative protection bypass.

`getOwnedPlotProfiles` combines a plot with its saved teleport point. `teleport == null` means no custom point is configured. Reading it neither loads chunks nor teleports a player. Saved coordinates do not guarantee a safe landing.

## Checking protection

`getFlagValue` returns an explicit flag value or its default; it returns `null` for an unknown plot or flag. Some older flags interpret `true` as a restriction. Use `evaluateFlag` to decide whether an action is allowed: it accounts for the meaning of the flag.

```kotlin
import pl.syntaxdevteam.plotsx.api.FlagDecision

val decision = api.evaluateFlag(worldName, blockX, blockZ, flagKey, playerUuid)
val allowed = decision == FlagDecision.ALLOW
```

| Result | Meaning |
| --- | --- |
| `ALLOW` | Action allowed; also returned outside plots for a known flag. |
| `DENY` | Action denied. |
| `UNKNOWN_FLAG` | No flag definition; the integration should deny the action or explicitly handle the unavailable feature. |

`subject` is a player's UUID or `null` for an environmental action. The method does not check server-level administrator permissions. Owners and members bypass the rule only when its definition has `memberBypass=true`. The `grave-create` flag defaults to enabled and has `memberBypass=false`.

## Writes and permissions

Pass the actual initiator as the `CommandSender` to write methods. PlotsX checks ownership, role, and the relevant permissions. Do not substitute the console for a player: the console has administrative access.

`addMemberAsync` adds a player with the `member` role; use `setMemberRoleAsync` to change it. Inspect every operation result: permission checks, a missing plot, changed membership, or a persistence error can cause a rejection.

Example called on the player's entity thread, inside your plugin class:

```kotlin
api.addMemberAsync(player, plotId, invitedUuid).thenAccept { result ->
    player.scheduler.execute(this, Runnable {
        player.sendMessage("PlotsX: $result")
    }, null, 1L)
}
```

The future completes after data is saved and the new protection state is available. Never call `join()` or `get()` on a server or entity thread. Schedule messages, GUI updates, and world access on the appropriate scheduler.

`DATABASE_ERROR` can also mean protection refresh failed after a durable write. Do not automatically repeat an operation without checking its current state. Disabling PlotsX completes pending writes with the same result.

## Paper and Folia

| Call | Required context |
| --- | --- |
| Snapshot, member, and flag reads; `evaluateFlag`; `getRolePermissions` | May run asynchronously; do not obtain an entity's location from the wrong thread while doing so. |
| `canManage` and starting writes with a player as initiator | That player's entity thread. |
| Operations initiated by the console | Server thread. |
| `registerFlag`, `unregisterFlag` | Server thread, not a Folia region thread. |

Ownership transfer requires the recipient to be an online member and checks their limits. `transferOwnershipAsync` captures limits on the recipient's entity thread and saves the change in the background. Synchronous `transferOwnership` on Folia requires the current thread to own both players; otherwise it returns `RECIPIENT_WRONG_THREAD`. The integration handles transfer confirmation.

## Custom flags and events

`registerFlag` accepts a `FlagDefinition`. Keys have the form `pluginname:flag`, with the namespace matching the plugin's lowercase name. Allowed characters are `a-z`, digits, `_`, `-`, and one `:`; the entire key is limited to 120 characters. An existing definition cannot be replaced.

A custom flag appears in the settings panel and role permissions as `flag.pluginname:flag`. Its name and description are plain text. Registration exposes the setting; the integration checks it with `evaluateFlag` for its own actions. Disabling the provider removes its definition while saved settings remain. Register it again when the provider next enables.

`PlotFlagChangedEvent` reports a successful flag change through the GUI or API. It includes the plot, flag key, previous and new values, and initiator UUID (`null` for console). It cannot be cancelled. Saving the same value emits no event; database imports and role-grant updates do not emit it either.

After `setFlagAsync`, the event is scheduled on the initiator's thread. The future does not wait for delivery; a player disconnect or plugin shutdown may prevent it.

Discard a retained service reference when PlotsX disables and obtain a new one after it enables again. `apiVersion` remains the historical contract revision `2`; it is neither the dependency version nor a selector for separate API variants.
