# A larger base — expansion and limits

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Need more room for another farm? Expand the plot from its panel. Expansion is available to the owner with the `plotsx.plot.expand` permission.

## Classic plot

1. Stand in the part of your plot where you want to add the next segment.
2. Open `/plot` and choose expansion.
3. Select north, east, south, or west.
4. Review the land and price, then confirm the purchase.

Each purchase adds an adjacent square with the size of the original plot. With the default radius of 16, that is **33 × 33 blocks**. You can move into the newly added section and expand from it again, including L-shaped layouts. Empty corner areas outside the claimed squares remain unclaimed.

The plot does not grow in all four directions at once. You cannot skip over an occupied neighboring segment.

## Chunk plot

The initial plot occupies one chunk, or **16 × 16 blocks**. In the expansion menu, select an adjacent chunk on the map and confirm the purchase. The new chunk must connect to the existing plot by an edge; touching only at a corner is not enough.

If the administrator keeps the option enabled, you can also stand in a free chunk next to your plot and use `/plot expand` to go directly to its confirmation step.

Both plot types cover the full height of the world.

## How much does expansion cost?

Creating a plot is free. By default, consecutive expansions of one plot cost **500, 750, 1125, 1687.5…** in the server currency. The administrator can configure separate prices for classic and chunk plots or set them to `0`.

Paid purchases require a working economy through Vault or VaultUnlocked. Operators also pay for paid expansion. A failed purchase does not increase the price of the next expansion.

## Limits

| Limit | Default |
| --- | --- |
| Number of plots owned across all worlds | 5 |
| Farthest classic-plot boundary from its original center | 64 blocks |
| Combined area of classic and mixed plots | 16641 blocks²; the effective limit can be higher for chunk-plot owners |
| Chunks in one chunk plot | 32 |
| Total chunks owned by one player | 64 |

Rank permissions can change these limits. For chunk plots, the area limit is automatically increased enough to make the configured chunk count reachable.

## Why can a purchase be rejected?

The entire new area must fit within the limits and must not overlap another plot or a blocked WorldGuard region. PlotsX does not trim an expansion to fit the remaining free space.

If the price, land, owner, or limits change while the menu is open, open the offer again. Stay near the correct section of the plot until confirming.

If persistence fails after money has already been withdrawn, PlotsX attempts a refund, even if the player disconnects. If the server is interrupted or the economy response is ambiguous, the operation is recorded for administrator review. Give the administrator the operation identifier shown in the payment message.

Next: [Configuration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Configuration.md).
