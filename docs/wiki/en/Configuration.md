# Config — server settings

For rank-based limits and multi-world configuration, see:
[Rank limits and worlds](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/limits-and-worlds.md).

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Settings are stored in `plugins/PlotsX/config.yml`. A value of `true` means “yes” and `false` means “no”. Preserve indentation and use spaces instead of tabs.

## Full default configuration

```yaml
plots:
  maxPlots: 5
  radius: 16
  world: "world"
  expansion:
    price: 500.0
    priceMultiplier: 1.5
    defaultMaxRadius: 64
    defaultMaxTotalArea: 16641

privateChests:
  protectOnPlace: true

database:
  type: "sqlite"
  sql:
    host: "localhost"
    port: 3306
    dbname: "my_database"
    username: "root"
    password: "password"

language: "PL"

aliases:
  claim: "c"
  unclaim: "unc"

webhook:
  discord:
    enabled: false
    url: "YOUR_WEBHOOK_URL_HERE"

checkForUpdates: true
autoDownloadUpdates: false
debug: true

stats:
  enabled: false
```

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

The old `levels` and `step` settings are no longer used. When upgrading, remove `levels` and set `price: 500.0` or your own value. Existing plots keep their land, and their current size becomes the base size. Area limits count the sum of segments; the radius limit controls the farthest boundary from the original center. See [expansion and payments](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/commands.md#rozszerzanie-działki-i-opłaty).

## Private containers

`privateChests.protectOnPlace: true` automatically protects containers placed by the plot owner or a member.

`false` disables automatic protection for newly placed containers. It does not remove existing protection. The manual `/pchest lock` command remains available.

## Data storage

| Setting | Meaning |
| --- | --- |
| `database.type` | Database engine: `sqlite`, `h2`, `mysql`, `mariadb`, or `postgresql`. |
| `database.sql.host` | Address of the external database server. |
| `database.sql.port` | Database port. Defaults to 3306; for a typical PostgreSQL setup use 5432. |
| `database.sql.dbname` | Database name; for SQLite this is also the local filename without the `.db` suffix. |
| `database.sql.username` | Database username. |
| `database.sql.password` | Database password. |

For a simple setup, keep SQLite. With the default name, data is stored in `plugins/PlotsX/my_database.db`. SQLite does not require a host, port, username, or password. H2 also stores data locally but uses login settings.

For an external database, enter the credentials provided by your host. Changing the database name or engine **does not migrate plots**. Back up the data before making such a change, then restart the server.

## Language and aliases

| Setting | Meaning |
| --- | --- |
| `language` | Message language; `PL` is Polish and `EN` is English. |
| `aliases.claim` | Additional alias for creating a plot, without the slash. |
| `aliases.unclaim` | Additional alias for removing a plot, without the slash. |

PlotsX includes Polish (`PL`) and English (`EN`) messages. For additional languages, contact the community.

Changing an alias does not remove the base command. Restart the server after changing aliases or language.

## Other options

| Setting | Default | Purpose |
| --- | --- | --- |
| `webhook.discord.enabled` | `false` | Enables the Discord webhook connection. |
| `webhook.discord.url` | `"YOUR_WEBHOOK_URL_HERE"` | Webhook URL; replace the placeholder with the real address. |
| `checkForUpdates` | `true` | Enables update checks. |
| `autoDownloadUpdates` | `false` | Enables automatic update downloads. |
| `debug` | `true` | Enables detailed diagnostic messages in the console. |
| `stats.enabled` | `false` | Enables operational statistics collection. |

These settings control plugin maintenance and may depend on the release. The webhook entry itself does not define which plot events are sent; PlotsX does not expose a list of plot notifications here. Never publish your webhook URL or database password.

If you are not diagnosing an issue, you can set `debug: false` to reduce detailed logging.

## How do I apply changes?

After changing ordinary plot settings, use `/ptx reload`. Changing the database, language, aliases, or integrations requires a server restart. If you are unsure, save the file and perform a full restart.

`/ptx reload` rereads the config, but it does not restart the whole plugin or reload all plot data from scratch.

Next: [Administration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Administration.md).
