# Build with friends

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

A shared base means more ideas and less solo digging. Add the people you want to share the land with.

## Manage your team

Stand on your plot and use:

| Command | Effect |
| --- | --- |
| `/plot add Alex` | Adds Alex to the plot. |
| `/plot remove Alex` | Removes Alex from the plot. |
| `/plot members` | Shows the owner and members. |

The player must be known to the server, but does not need to be online. You can also provide a UUID, their unique identifier.

## Who can do what?

**Owner** manages the plot, its settings, and its members.

**Member** can build and use the plot according to member access rules. Membership does not automatically grant the full management panel or permission to delete the plot. Add only people you trust — membership provides broad access to the land.

**Guest** uses the plot according to its configured [flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md). Simply visiting the plot does not grant permission to build.

## Chests remain separate

Adding someone to the plot does not open another player's private chests. Container owners decide who can access them.

After a member is removed, they lose access granted through another player's shared private chests on that plot, including access from an already open inventory. The stored trust entry remains: adding the player to the plot again restores access. If you want to revoke it permanently, remove the trust **before** removing the player from the plot.

Removing a member does not transfer ownership of their private chests. If protected containers are left behind, ask an administrator for help.

Next: [Private Chests](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Private-Chests.md).
