# Rank limits and worlds

[← Home](Home.md) · [Polski](../Limity-i-swiaty.md)

```yaml
plots:
  claiming:
    mode: classic # classic | chunks; changing this requires a restart
  chunks:
    maxPerPlot: 32
    maxTotalOwned: 64
    expansion:
      price: 500.0
      priceMultiplier: 1.5
  maxPlots: 5
  radius: 16
  world: ["world", "survival", "world_nether"]
```

`maxPlots` counts plots across all worlds. `claiming.mode` selects the geometry of new claims only: `classic` uses a radius, while `chunks` claims a complete 16 × 16 chunk. Existing plots retain their geometry and protection when the mode changes. `radius` applies only to new classic plots.

Example permissions for a VIP group:

| Permission | Effect |
| --- | --- |
| `plotsx.plot.max-plots.10` | Up to 10 plots, including when receiving ownership. |
| `plotsx.plot.radius.24` | Initial radius of 24. |
| `plotsx.plot.size.96` | Maximum radius of 96. |
| `plotsx.plot.max-area.100000` | Up to 100000 block columns of combined area. |
| `plotsx.plot.max-chunks-per-plot.64` | Up to 64 chunks in one chunk plot. |
| `plotsx.plot.max-chunks.128` | Up to 128 owned chunks in total. |

The highest granted value for each permission wins; otherwise the configuration applies. Negative and nonnumeric values are ignored. `max-plots.0` blocks new plots; the initial radius is clamped to at least 1 and remains subject to size and area limits. Changing permissions does not remove or shrink existing plots.

Area and plot-count limits apply across both geometries. Chunk limits are additional and do not affect classic plots. Setting `maxPerPlot: 0` or `maxTotalOwned: 0` prevents further chunk additions.

`plots.world` accepts a list of world names. The legacy `world: "world"` form still works. `["*"]` allows all worlds; `[]` blocks claiming. For compatibility, an empty string or an absent key allows all worlds.

Worlds must already be loaded on the server, for example through Multiverse-Core. PlotsX uses the world name of the current location without requiring a dedicated Multiverse integration. It does not create or load worlds itself.

Next: [Permissions](Permissions.md) · [Configuration](Configuration.md).
