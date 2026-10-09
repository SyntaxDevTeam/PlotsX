# Integrations with other plugins

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

PlotsX can use integrations with plugins already installed on your server.

## WorldGuard — space for spawn and server builds

When WorldGuard is active, PlotsX checks the entire area of a new or expanded plot against WorldGuard regions. By default, a normal WorldGuard region still blocks claiming, keeping spawn, arenas, and administrative builds protected.

PlotsX registers its own WorldGuard state flag:

```text
plotsx-claim
```

To allow plot creation and expansion in a region, set:

```text
/rg flag <region> plotsx-claim allow
```

To explicitly block claims:

```text
/rg flag <region> plotsx-claim deny
```

A missing flag on a protected region does not mean permission — the area remains blocked. When regions overlap, PlotsX uses WorldGuard's `StateFlag` resolution, so priorities, inheritance, and the normal `allow`/`deny` conflict rules apply.

The entire plot area and full world height are checked. This applies to `/claim`, classic expansion, and chunk expansion. It is not enough for only the block below the player to be outside the region.

The WorldGuard global region, `__global__`, does not block plot creation by itself and is not used to resolve `plotsx-claim`. Other WorldGuard rules can still restrict actions in the world.

If PlotsX cannot check regions or the flag was not registered correctly, it blocks claiming or expanding land that intersects a region. This fail-safe behavior prevents protected areas from being opened accidentally.

The flag is registered during the plugin-loading phase, before WorldGuard locks its flag registry. After installing or updating PlotsX, perform a full server restart instead of reloading the plugin.

Without WorldGuard, plots still work and cannot overlap each other.

## Vault and VaultUnlocked — paid expansion

Paid upgrades require:

- VaultUnlocked or Vault;
- an economy plugin exposing accounts and balances through it.

Vault itself does not store money. PlotsX first tries a VaultUnlocked economy and then classic Vault. Prices use the default currency.

Free upgrades with a price of `0` do not require an economy.

## LuckPerms — rank access

LuckPerms is convenient for assigning commands and limits to specific ranks. See the [Permissions](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Permissions.md) page for available nodes. Basic plot usage does not specifically require LuckPerms — any compatible permission system can grant the required access.

## CoreProtect — action history

When connected to CoreProtect, PlotsX forwards information about block placement and breaking as well as container transactions. This helps administrators investigate activity.

PlotsX does not provide its own player-facing rollback command or history menu. History lookup and rollback are handled through CoreProtect's administrative tools.

## CleanerX — plot names

PlotsX checks names through `containsBannedWord` from the CleanerX API. If a banned word is detected, the rename is rejected, including for OP players. If an installed CleanerX instance is disabled or its API fails, saving the name is blocked and the player receives a message. Without CleanerX installed, renaming works without the word filter. Existing names are not modified automatically.

After startup, check the log: `CleanerX API detected - integration enabled.` confirms that the integration is active. `CleanerX API not detected - skipping integration.` means the connection was not established; verify that CleanerX is installed and enabled and review any preceding API warnings. Word detection depends on the CleanerX dictionary and whitelist.

## PlaceholderAPI and MiniPlaceholders

PlotsX detects these plugins, but currently does not expose a public set of plot placeholders for chat or scoreboards. They are not required for normal gameplay.

Restart the server after adding integrations.

Next: [Configuration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Configuration.md).
