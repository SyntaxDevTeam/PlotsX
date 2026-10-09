# FAQ and troubleshooting

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

## I cannot create a plot

Check that you are in an allowed world, have `plotsx.cmd.claim`, and have not reached your plot-count or area limit. The entire target area must be free from other plots and WorldGuard regions. A free block directly under your feet is not enough.

## I own fewer than five plots, but the limit still blocks me

Plot count and total owned area are separate limits. A few large plots can use the entire available area allowance.

## Does a plot protect the mine under my house?

Yes. Protection covers the full height of the world over the plot area. Another plot cannot be created directly above or below it.

## How do I see the borders?

Open the plot panel and click the plot-information item. The borders are displayed for roughly 20 seconds.

## How do I return to my base?

Open the panel of your own plot by name or from the plot list and choose teleportation. If no safe location can be found, teleportation may be rejected.

## My friend is a member but cannot open a chest

Private containers require separate sharing. The container owner should look at it and run `/pchest trust <player>`.

## I own the plot but cannot open a member's container

Plot ownership does not automatically grant access to another player's private containers. Ask the container owner to share it with you.

## I cannot revoke chest access from a player who was removed from the plot

Normal revocation currently looks up the current owner and members. It is best to revoke container sharing before removing the member. If a private chest remains after the player was removed, ask an administrator for help.

## Plants do not grow or water does not flow

Growth and liquid flow are blocked by default. Set `cant-grow = NO` and, if you need flowing liquids, `flow = NO`. Also review the other [protection flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md).

## A hopper does not transfer items

Check `item-transfer` and the private protection of both containers. Disabling the transfer block alone does not make private chests accessible to hoppers.

## I have Vault, but expansion purchases do not work

You also need an economy plugin that exposes balances through Vault or VaultUnlocked. Check your balance, plot limits, and whether another expansion is available.

## My radius limit is 64, but the plot stops at 48

The default offer has three upgrades and ends at radius 48. The limit is an upper bound, not an additional upgrade level to purchase.

## Expansion is blocked by spawn even though I am not touching it

The entire area after expansion is checked. A WorldGuard region can be larger than the visible build or can extend above or below your current position.

## A flag is set to YES, but the action is still blocked

Some flags enable a block rather than permission. Check the “What does YES mean?” column on the [Protection and Flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md) page. Additional restrictions may also come from other plugins.

## Why can an administrator build despite the protection?

Operators and players with protection bypass can ignore many restrictions. Test using a regular account that is not a plot member.

## I changed an alias or language and nothing happened

Perform a full server restart. `/ptx reload` rereads the config, but does not recreate every setting initialized during startup.

## Does removing a plot delete the house?

No. `/unclaim` releases the land and removes PlotsX protection, while the build remains in the world. Take valuable items before confirming.

## Can I sell a plot, merge it with another plot, or transfer it to a friend?

PlotsX currently does not provide those commands or panel options.

## Can I make a backup with the export command?

In this release, it should not be treated as a complete replacement for the documented backup procedure. Follow the [backup instructions](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Administration.md).

## Where can I download the plugin or ask for help?

[Downloads](https://github.com/SyntaxDevTeam/PlotsX/releases) · [Discord](https://discord.gg/Zk6mxv7eMh) · [Issue tracker](https://github.com/SyntaxDevTeam/PlotsX/issues)
