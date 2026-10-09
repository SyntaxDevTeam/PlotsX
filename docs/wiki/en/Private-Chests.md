# Private chests

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Shared land does not have to mean shared storage. Protect your items and share them only when you want to.

Protection covers chests, trapped chests, barrels, and shulker boxes located on plots.

## Protect a container

By default, a container placed by the plot owner or a member automatically becomes private. The administrator can disable this behavior.

You can claim an older, unprotected container manually:

1. Stand no more than 6 blocks away.
2. Look at a container located on a plot where you are the owner or a member.
3. Run `/pchest lock`.

## Share your items

While looking at your container, run `/pchest trust Alex`. Alex must be the owner or a member of the same plot.

Sharing allows access to the contents. It does not grant permission to break the container or modify its protection. The plot owner also needs explicit access to a private container owned by another player.

## All options

Each command below requires you to look at a container located on a plot.

| Command | Action |
| --- | --- |
| `/pchest` | Shows usage information. |
| `/pchest lock` | Protects an unassigned container. |
| `/pchest unlock` | Removes private protection. |
| `/pchest trust <player>` | Shares the container. |
| `/pchest share <player>` | Same as `trust`. |
| `/pchest untrust <player>` | Revokes sharing. |
| `/pchest unshare <player>` | Same as `untrust`. |
| `/pchest info` | Shows the owner and sharing list. |

You can always use `/privatechest` instead of `/pchest`. Removing protection and changing the sharing list is limited to the container owner or an administrator with the appropriate access.

Currently, revoking access also requires the target player to still belong to the plot. Do this before `/plot remove`.

## Two protection layers

The chest flag controls general container access on the plot, while private protection secures a specific container. Allowing guests to access plot containers does not automatically unlock private chests. After `unlock`, the normal plot rules still apply.

Automatic item transfers to or from private containers are blocked. If you are building hopper-based storage, account for the item-transfer flag as well.

Next: [Playing Together](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Playing-Together.md).
