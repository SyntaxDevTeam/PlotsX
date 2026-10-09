# Permissions

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Permissions tell the server who may use a specific feature. Grant them to players or ranks, for example through LuckPerms.

## Player permission set

| Permission | Access |
| --- | --- |
| `plotsx.cmd.claim` | Create plots. |
| `plotsx.cmd.unclaim` | Remove plots you own. |
| `plotsx.cmd.plot` | Open the plot panel and manage members. |
| `plotsx.cmd.privatechest` | Use private-container commands. |
| `plotsx.plot.expand` | Expand your own plot through the panel. |

A command permission does not replace ownership of the plot or container.

Automatic protection of newly placed containers does not require the private-chest command permission. It depends on server configuration and on who places the container on the plot.

## Rank-based limits

| Permission | Meaning |
| --- | --- |
| `plotsx.plot.max-plots.10` | At most 10 plots across all worlds, including when receiving ownership. |
| `plotsx.plot.radius.24` | Initial radius of a new plot: 24 blocks. |
| `plotsx.plot.size.64` | Maximum plot radius: 64 blocks. |
| `plotsx.plot.max-area.16641` | Maximum combined player-owned area: 16641 blocks². |
| `plotsx.plot.max-chunks-per-plot.32` | Maximum chunks in one chunk plot. |
| `plotsx.plot.max-chunks.64` | Maximum total chunks owned across all worlds. |

You can replace the final number with another positive value. If a player has several values for the same limit, the highest value wins. Without these permissions, config values are used.

Default values come from `plots.maxPlots`, `plots.radius`, `plots.chunks`, and limits under `plots.expansion`. `max-plots.0` blocks creation of new plots; the initial radius is clamped to at least 1. Chunk limits may be 0, which blocks further chunk expansion. Changing permissions does not resize existing plots.

VIP example: grant `plotsx.plot.max-plots.10`, `plotsx.plot.radius.24`, `plotsx.plot.size.96`, and `plotsx.plot.max-area.100000` in addition to the required command permissions. See [rank limits and worlds](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/limits-and-worlds.md).

Plot roles `member`, `builder`, and `manager` are independent of server ranks. Their `invite`, `kick`, `rename`, and `flag.<identifier>` grants are documented under [Protection and Flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md).

## Administrator access

`/plot admin list <player/UUID>` requires `plotsx.admin.manage` and lists plots owned by the target, including offline owners. The normal "my plots" list shows only the player's own plots, even for operators.

| Permission | Meaning |
| --- | --- |
| `plotsx.cmd.ptx` | Full access to `/ptx` and `/plotsx`, including reload, import, and export. |
| `plotsx.admin.manage` | Manage any plot through `/plot admin <id> ...` and access administrative panels, members, roles, and grants. |
| `plotsx.admin.bypass` | Bypass plot and private-container protection, open another player's panel, and bypass radius and area limits. |
| `plotsx.owner` | Broad feature access and protection bypass. This is an administrative permission, not a marker that a player owns a plot. |
| `plotsx.*` | Broad access to PlotsX features, including protection bypass. |
| `*` | Global access, also recognized by PlotsX protection. |

Bypass does not replace command permissions. It does not allow removing another player's plot through `/unclaim` or buying expansions as a non-owner. It does not bypass the plot-count limit, land collisions, or economy charges.

Operators bypass protection as well as radius and area limits. For `/ptx`, grant `plotsx.cmd.ptx` explicitly: broad permissions do not replace it regardless of the permission-system configuration. Likewise, use `plotsx.admin.bypass` explicitly or operator status when you need the limit bypass.

## Permissions you do not need to grant

`plotsx.plot.visit` and `plotsx.plot.info` currently do not control teleportation or GUI information.

Static permissions declared in `paper-plugin.yml` use `default: op`. Grant the required permissions to non-OP players through your permission system.

Next: [Commands](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Commands.md).
