# Commands

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

`<player>` or `<name>` means a value you provide. Do not type the angle brackets. An argument in `[brackets]` is optional.

## Plots

| Command | What it does | Permission |
| --- | --- | --- |
| `/claim` | Opens the confirmation for creating a plot at your current location. | `plotsx.cmd.claim` |
| `/unclaim` | Opens the confirmation for removing the plot you own at your current location. | `plotsx.cmd.unclaim` |
| `/plot` | Opens the panel of the plot you are standing on if you have access. Otherwise, it lists plots you own or belong to. | `plotsx.cmd.plot` |
| `/plot <name>` | Opens an accessible plot by name; your own plot takes priority. | `plotsx.cmd.plot` |
| `/plot add <player>` | Adds a member to your plot. | `plotsx.cmd.plot` |
| `/plot remove <player>` | Removes a member from your plot. | `plotsx.cmd.plot` |
| `/plot members` | Opens the plot member panel. | `plotsx.cmd.plot` |
| `/plot expand` | Opens expansion confirmation from a free chunk next to a chunk plot you own when `plots.chunks.expansion.standingCommand` is enabled. | `plotsx.cmd.plot` and `plotsx.plot.expand` |

The administrator chooses how new plots are created through `plots.claiming.mode: classic | chunks`. `classic` uses a radius, while `chunks` claims an entire 16 × 16 chunk. Changing the mode requires a restart; saved plots of both types remain protected and continue to expand according to their own geometry.

### Roles, grants, and administration

Player forms require `plotsx.cmd.plot` and standing on a plot the player can access. Members use grants assigned to their role. The `/plot admin` forms require `plotsx.admin.manage`.

| Command | Action and required access |
| --- | --- |
| `/plot add <player>` | Adds a player known to the server by name or UUID, including offline players. Requires the `invite` grant or owner/administrator access. |
| `/plot remove <player>` | Removes a member. The `kick` grant can remove only lower roles: `member < builder < manager`. Owners and administrators are not limited by this hierarchy. |
| `/plot members` | Opens the member panel, also available to plot members. |
| `/plot role <player> <member\|builder\|manager>` | Changes a member role; owner/administrator only. |
| `/plot permission <role> <grant> <true\|false>` | Changes a role grant for this plot; owner/administrator only. |
| `/plot transfer <player> confirm` | Transfers ownership to a current member who is online; owner/administrator only. Checks the recipient's plot-count, radius, area limits, and plot-name conflicts. |
| `/plot admin list <player/UUID>` | Lists plots owned by the target, including offline owners, with ID, name, world, position, and either the radius or chunk count. Works from console and in game. |
| `/plot admin <id> move confirm` | Moves the plot's protection and settings to the administrator's position; requires `plotsx.admin.manage` and an in-game sender. |
| `/plot admin <id>` | Opens the member panel of the specified plot in game. |
| `/plot admin <id> <subcommand> [arguments]` | Executes the same management operations on the specified plot, including from console. Requires `plotsx.admin.manage` without separately requiring `plotsx.cmd.plot`. `members` prints the member list. |

Examples: `/plot permission builder flag.build true`, `/plot role Alex manager`, `/plot admin 12 members`.
Grants are `invite`, `kick`, `rename`, and `flag.<identifier>`; `flag.*` is not supported.

`/unclaim` does not select a plot by name.

Default aliases: `/c` = `/claim`, `/unc` = `/unclaim`. Administrators can change them.

Renaming, border display, teleportation, flags, and expansion are available through the [Plot Panel](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Plot-Panel.md). Expansion additionally requires `plotsx.plot.expand`.

## Moving plot protection

Find the ID with `/plot admin list <player/UUID>`, stand at the destination, and use `/plot admin <id> move confirm`. The plot keeps its ID, owner, name, members, flags, and shape. Chunk plots move by whole chunks. The teleport point is reset.

Buildings and containers remain at the old location, which loses this plot's protection. The destination is checked against allowed worlds, WorldGuard, and other plots. Relocation is rejected if the plot changed during the operation.

If `/claim` is blocked by an ongoing expansion purchase, you receive a waiting message and a notification when you can try again. Retry the command manually.

## Creating plots: limits and worlds

`/claim` uses `plots.world`, which accepts a list such as `["world", "survival"]`.
A legacy single value still works; `["*"]` allows all worlds, while `[]` blocks plot creation.
Worlds must be loaded, for example by Multiverse-Core; no dedicated integration is required.

`plotsx.plot.max-plots.<number>` overrides `plots.maxPlots`, and `plotsx.plot.radius.<radius>` overrides `plots.radius`. The highest granted value for a given limit wins.
Plot count and total area are calculated across all worlds.
The initial radius is also constrained by `plotsx.plot.size.<radius>` and the total-area limit.
Limits and the target world are checked again when plot creation is confirmed.

For chunk plots, `plotsx.plot.max-chunks-per-plot.<number>` limits a single plot, while `plotsx.plot.max-chunks.<number>` limits the owner's total chunks. Expansion is performed through the plot panel and uses `plots.chunks.expansion.price` and `priceMultiplier`.

## Private containers

All options below require `plotsx.cmd.privatechest` and looking at a container on a plot from no more than 6 blocks away.

| Command | What it does |
| --- | --- |
| `/privatechest` | Shows usage information. |
| `/privatechest lock` | Protects an unassigned container. |
| `/privatechest unlock` | Removes its private protection. |
| `/privatechest trust <player>` | Shares the container. |
| `/privatechest share <player>` | Same as `trust`. |
| `/privatechest untrust <player>` | Revokes sharing. |
| `/privatechest unshare <player>` | Same as `untrust`. |
| `/privatechest info` | Shows the owner and sharing list. |

The `/pchest` alias works with every option. Access details are described in [Private Chests](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Private-Chests.md).

## Plugin administration

`/ptx`, `/plotsx`, and `/plx` run the same commands. They work in game and in the console. Omit the leading slash in the console.

**Every option requires the single `plotsx.cmd.ptx` permission, including import and reload. Grant it only to administrators.**

| Command | What it does |
| --- | --- |
| `/ptx` | Shows how to open help. |
| `/ptx help [page]` | Shows concise help. |
| `/ptx version` | Shows the plugin version and information. |
| `/ptx reload` | Reloads the config and refreshes protection data. Changing the claiming mode, database, language, or aliases requires a restart. |
| `/ptx export [mysql\|mariadb\|sqlite\|postgresql]` | Writes the backup to `plugins/PlotsX/dump/backup.sql`. Omitting the engine uses the current database's SQL format. |
| `/ptx import` | Validates and restores the backup, then publishes a rebuilt cache. |

For backup and restore procedures, see [Administration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Administration.md).

## Plot names and CleanerX

Renaming checks `containsBannedWord` through the CleanerX API. A detected banned word or an empty name causes the change to be rejected, including for OP players. If an installed CleanerX instance is disabled, exposes an incompatible API, or reports an error, saving the name is blocked. If CleanerX is not installed, the word filter is not applied. The CleanerX dictionary and whitelist determine the result; existing plot names are not changed automatically.
