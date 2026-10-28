# Config — server settings

For rank-based limits and multi-world configuration, see:
[Rank limits and worlds](Limits-and-Worlds.md).

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Settings are stored in `plugins/PlotsX/config.yml`. A value of `true` means “yes” and `false` means “no”. Preserve indentation and use spaces instead of tabs.

## Configuration updates and reload

The current configuration version is **103**. When updating an older file, PlotsX preserves administrator settings, adds missing options, and creates a backup such as `config.yml.102.bak`. Another attempt does not overwrite an earlier backup. Do not manually change `config-version` to skip an update.

Invalid YAML is reported before the affected file is overwritten. If `/ptx reload` rejects the configuration, existing protection remains available. Correct the reported error and retry. Files with the current or a newer version are not automatically rewritten or downgraded.

Changing the database, claiming mode, language, or aliases requires a restart. Ordinary plot settings can be applied with `/ptx reload`; it also refreshes protection data but does not reconnect to a different database.

## Example of the main settings

```yaml
config-version: 103
language: "EN"

plots:
  claiming:
    mode: classic
  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
    expansion:
      price: 500.0
      priceMultiplier: 1.5
      maxPrice: -1
      standingCommand: true
  maxPlots: 5
  radius: 16
  world: ["world"]
  borderDisplaySeconds: 30
  notifications:
    boundary: actionbar
  expansion:
    price: 500.0
    priceMultiplier: 1.5
    maxPrice: -1
    defaultMaxRadius: 64
    defaultMaxTotalArea: 16641

privateChests:
  protectOnPlace: false

database:
  type: "sqlite"
  sql:
    host: "localhost"
    port: 3306
    dbname: "my_database"
    username: "root"
    password: "password"

aliases:
  claim: "c"
  unclaim: "unc"

update:
  check-for-updates: true
  auto-download: false
  hangar: true
  github: true
  modrinth: true

debug: off
stats:
  enabled: true
```

This is an abbreviated example. See the complete current [config.yml](../../../src/main/resources/config.yml).

## Plots

| Setting | Meaning |
| --- | --- |
| `plots.maxPlots` | Maximum number of plots a player may own. A value of 0 blocks new plot creation. |
| `plots.radius` | Initial plot radius. A radius of 16 creates a 33 × 33 block area. Use a positive integer. |
| `plots.world` | World where plots may be created. Enter its name, for example `world`. `"*"` or an empty string allows plot creation in every world. |
| `plots.expansion.defaultMaxRadius` | Default maximum plot radius; use a positive value. |
| `plots.expansion.defaultMaxTotalArea` | Default limit for the player's combined plot area; use a positive value. |

The initial size must fit within both limits. Radius and area limits can be overridden for ranks through [Permissions](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Permissions.md).

Changing the initial radius does not resize existing plots. Changing the allowed world does not remove existing land.

## Segment-based expansion

`plots.expansion.price` is the price of the first segment (`0` = free). A segment has the size of the original plot, for example 33 × 33 blocks for radius 16. The GUI lets the player select north, east, south, or west. The segment the player is standing in is the starting point. Moving into another segment lets the player turn and build any connected shape. An occupied neighbor cannot be skipped. North and east expansion can form an L shape without claiming the empty corner.

The “Show borders” button in the expansion GUI closes the menu and displays the outline of the existing plot. The duration is controlled by `plots.borderDisplaySeconds: 30` (seconds, minimum 1); the same value is used in the main plot panel. Border preview does not purchase a segment.

## Plot-boundary notifications

Approaching, entering, and leaving a plot are shown on the action bar by default so they do not fill chat history:

```yaml
plots:
  notifications:
    boundary: actionbar
```

Set `boundary: chat` if these three notification types should be sent to chat instead.
An invalid value safely falls back to `actionbar`.

The price of later expansions follows `price × priceMultiplier ^ purchased_segment_count`. The default `plots.expansion.priceMultiplier: 1.5` results in prices of 500, 750, 1125, and 1687.5. The multiplier must be at least 1; a value of 1 disables price growth. The counter is tracked separately for each plot, includes existing segments, and persists across restarts and ownership transfers. The original land and failed purchases do not increase the counter. Changing the base price or multiplier recalculates the next purchase price. Invalid values or a result outside the range supported by the economy provider block the purchase.

The old `levels` and `step` settings are no longer used. When upgrading, remove `levels` and set `price: 500.0` or your own value. Existing plots keep their land, and their current size becomes the base size. Area limits count the sum of segments; the radius limit controls the farthest boundary from the original center. See [expansion and payments](Expansion.md).

## Private containers

`privateChests.protectOnPlace: true` automatically protects containers placed by the plot owner or a member.

`false` disables automatic protection for newly placed containers. It does not remove existing protection. The manual `/pchest lock` command remains available.

## Data storage

Connection pooling, lifecycle, and JDBC driver management are handled by **SyntaxDB**. PlotsX no longer creates its own `HikariDataSource` or separate `DriverManager` connections at runtime.

| Setting | Meaning |
| --- | --- |
| `database.type` | Database engine: `sqlite`, `mysql`, `mariadb`, or `postgresql`. `mariadb` is handled as a MySQL-compatible dialect alias. |
| `database.sql.host` | Address of the external database server. |
| `database.sql.port` | Database port. Defaults to 3306; for a typical PostgreSQL setup use 5432. |
| `database.sql.dbname` | Database name; for SQLite this is also the local filename without the `.db` suffix. |
| `database.sql.username` | Database username. |
| `database.sql.password` | Database password. |

For a simple setup, keep SQLite. With the default name, data is stored in `plugins/PlotsX/my_database.db`. SQLite does not require a host, port, username, or password. SyntaxDB keeps WAL mode, foreign keys, and a lock wait timeout enabled for SQLite.

PlotsX accepts only the database types listed above. Changing the database name or engine **does not migrate plots**. Back up the data before making such a change, then restart the server.

## Language and aliases

| Setting | Meaning |
| --- | --- |
| `language` | Message language: `EN`, `PL`, `DE`, `ES`, `FR`, or `PT_BR`. |
| `aliases.claim` | Additional alias for creating a plot, without the slash. |
| `aliases.unclaim` | Additional alias for removing a plot, without the slash. |

Bundled languages:

| Value | Language |
| --- | --- |
| `EN` | English |
| `PL` | Polish |
| `DE` | German |
| `ES` | Spanish |
| `FR` | French |
| `PT_BR` | Brazilian Portuguese |

Message files are stored in `plugins/PlotsX/lang/`. Select a language in `config.yml` and restart the server.

Changing an alias does not remove the base command. Restart the server after changing aliases or language.

## Other options

| Setting | Default | Purpose |
| --- | --- | --- |
| `webhook.discord.enabled` | `false` | Enables the Discord webhook connection. |
| `webhook.discord.url` | `"YOUR_WEBHOOK_URL_HERE"` | Webhook URL; replace the placeholder with the real address. |
| `update.check-for-updates` | `true` | Enables update checks. |
| `update.auto-download` | `false` | Enables automatic update downloads. |
| `debug` | `off` | Diagnostic level: `off`, `info`, `diag`, or `dev`. |
| `stats.enabled` | `true` | Enables operational statistics collection. |

These settings control plugin maintenance and may depend on the release. The webhook entry itself does not define which plot events are sent; PlotsX does not expose a list of plot notifications here. Never publish your webhook URL or database password.

After troubleshooting, set `debug: off` to reduce detailed logging.

## How do I apply changes?

After changing ordinary plot settings, use `/ptx reload`. Changing the database, language, aliases, or integrations requires a server restart. If you are unsure, save the file and perform a full restart.

`/ptx reload` reads the configuration and refreshes protection data. It does not restart the whole plugin or switch database connections.

Next: [Administration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Administration.md).
