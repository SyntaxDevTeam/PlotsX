# Protection and flags

## Supported grants — plot role grants

Grants are configured under `plot-roles.<role>.permissions` for the `member`, `builder`, and `manager` plot roles. These roles are independent of server ranks such as VIP.
The owner can override their grants for a specific plot through `/plot members`.

| Grant | Meaning |
| --- | --- |
| `invite` | Add members. |
| `kick` | Remove members while respecting the role hierarchy. |
| `rename` | Rename the plot. |
| `flag.<identifier>` | Change a specific flag, for example `flag.build`. |

Every flag listed below has a matching `flag.<identifier>` grant. This also applies to additional flags registered by API integrations. There is no `flag.*` grant.
A grant allows changing a flag, but does not change the flag value by itself.
Assigning roles and transferring ownership remains limited to the owner or an administrator.
Example: `permissions: [invite, rename, flag.build, flag.pvp]`.
By default, only `manager` receives the `invite`, `kick`, and `rename` grants.

The integration flag `grave-create` (default YES) allows grave creation by integrations that use this flag; plot membership does not bypass it.

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

Flags are rule switches for your plot. Open `/plot`, choose flags, and click the setting you want to change.

## How should the switches be read?

A value of **YES** does not always mean permission. Some switches enable an action, while others enable a block. For example, `build = YES` allows guests to build, while `chest = YES` blocks normal guest access to containers.

The tables below state exactly what **YES** means. **NO** produces the opposite behavior. The identifier is the internal flag name used in messages and grants.

Player-access rules mainly affect guests. Owners and members have broader access. Environment rules such as fire, growth, and explosions apply to the plot area itself. Private containers have an additional protection layer independent of ordinary plot access.

## Building and using the base

| Setting | What does YES mean? | Default |
| --- | --- | --- |
| `build` | Guests may place and break blocks. | NO |
| `chest` | Blocks normal guest access to containers. | YES |
| `ender-chest` | Blocks guests from using ender chests. | YES |
| `lever` | Blocks lever use by guests. | YES |
| `button` | Blocks button use by guests. | YES |
| `door` | Blocks normal opening of doors and gates by guests. | YES |
| `smart-door` | Guests may use coordinated opening of adjacent doors of the same type. | NO |
| `utility` | Guests may use utility blocks such as furnaces. | NO |
| `redstone` | Guests may modify supported redstone components such as repeaters. | NO |
| `pistons` | Pistons may move and destroy blocks inside the plot. Movement across the border is always blocked. | NO |
| `decorations` | Allows supported decoration interactions such as armor stands and item frames. | NO |
| `bed-use` | Guests may use beds. | NO |
| `crafting` | Guests may use crafting tables and crafters. | NO |
| `enchanting` | Guests may use enchanting tables. | NO |
| `respawn-anchor` | Guests may use respawn anchors. | NO |
| `item-transfer` | Blocks automatic item transfer between containers. | YES |

Smart doors have their own access rule. If you want the entrance closed to guests, keep `door = YES` and `smart-door = NO`.

## Combat, items, and animals

| Setting | What does YES mean? | Default |
| --- | --- | --- |
| `pvp` | Allows player combat where plot access is checked. | NO |
| `passives` | Guests may damage animals. | NO |
| `use-potions` | Guests may use potions. | NO |
| `special-weapons` | Guests may use supported special weapons such as tridents. | NO |
| `projectiles` | Guests may launch projectiles. | NO |
| `animal-leash` | Guests may attach and remove leads from animals. | NO |
| `animal-ride` | Guests may ride supported animals. | NO |
| `animal-breed` | Guests may breed animals; this also covers egg fertilization in delayed-offspring mechanics. | NO |
| `fishing` | Guests may fish. | NO |
| `item-pickup` | Guests may pick up items. | NO |
| `item-drop` | Guests may drop items. | NO |
| `crop-trample` | Guests may trample farmland. | NO |

Since Alpha-02, the previous combined `animal-interact` flag is retained only as a backward-compatibility key and is not shown in the GUI. During migration, its stored value is copied to `animal-leash`, `animal-ride`, and `animal-breed` unless a specific new flag already has its own value. Existing plots therefore keep their previous behavior, while each action can be configured independently after migration.

Adding a player as a plot member grants broader access for many of these checks. The PvP flag is not a guarantee that combat between all members of the same team is disabled.

## Movement and commands

| Setting | What does YES mean? | Default |
| --- | --- | --- |
| `minecart` | Blocks supported guest interactions with vehicles. | YES |
| `teleport` | Blocks guests from teleporting onto the plot using ender pearls or chorus fruit. | YES |
| `portal-create` | Allows portal creation. | NO |
| `portal-use` | Guests may use portals. | NO |
| `allow-home` | Guests may use `/home` and `/sethome`. | NO |
| `command-use` | Guests may use other commands. | YES |
| `elytra` | Guests may start elytra flight. | NO |
| `flight` | Guests may enable flight if they already have that capability. | NO |

PlotsX does not provide `/home`, `/sethome`, or a flight ability. These flags only regulate capabilities already provided elsewhere.

The base commands `/plotsx`, `/ptx`, `/plot`, `/claim`, and `/unclaim` are exempt from the general command block. Custom aliases and private-chest commands are not part of this exemption. `/home` and `/sethome` use their own dedicated control.

## Environment and natural changes

| Setting | What does YES mean? | Default |
| --- | --- | --- |
| `spawn-monsters` | Allows monster spawning. | NO |
| `spawn-animals` | Allows animal spawning. | YES |
| `allow-spawners` | Allows placing and breaking spawners where player access is checked. | NO |
| `flow` | Blocks liquid flow and dispenser-based pickup/placement of liquids. | YES |
| `flow-damage` | Allows liquids to destroy vulnerable blocks. | NO |
| `fire` | Blocks supported ignition, fire spread, and burn events. | YES |
| `iceform-player` | Guests may freeze water using Frost Walker. | NO |
| `iceform-world` | Allows natural creation and melting of ice or snow. | NO |
| `cant-grow` | Blocks plant growth. | YES |
| `leaves-decay` | Blocks natural leaf decay. | YES |
| `block-transform` | Blocks supported block transformations such as some spreading blocks. | YES |
| `fall` | Blocks supported changes caused by falling blocks. | YES |
| `explosions` | Blocks terrain destruction by explosions and supported explosion events on the plot. | YES |
| `effects` | Blocks supported effects applied to players. | YES |
| `conduit-effects` | Blocks conduit effects. | YES |
| `weather` | Blocks lightning strikes and lightning ignition on the plot. | YES |

The weather flag does not create separate weather over the plot. Private containers remain protected from explosion destruction even when explosions are allowed.

Some actions are controlled by multiple rules at once. Natural ice formation may require both `iceform-world = YES` and `block-transform = NO`. Allowing potions does not disable the separate effects block.

## Prepare the plot for your use case

**Farm:** start with `cant-grow = NO`. If you need flowing water, set `flow = NO`. Hopper transport also requires `item-transfer = NO` and no private-container protection blocking the path.

**Guest-friendly house:** unlock doors and selected utility stations while keeping building disabled.

**Shared base:** add trusted players as members instead of allowing every guest to build.

After changing settings, test them with a player who is neither a member nor an administrator. Other server plugins may add additional restrictions.

Next: [Private Chests](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Private-Chests.md).
