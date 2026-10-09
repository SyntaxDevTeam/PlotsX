# Plot panel

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

The most important options are available directly through `/plot`.

On your own plot, the command opens that plot's panel. Outside plots, it shows a list of your plots. You can also select one of your plots by name, for example `/plot Base`. While standing on another player's land, a regular player cannot open the owner's management panel.

## What is available in the menu?

| Option | Purpose |
| --- | --- |
| Plot information | Shows the owner, name, plot ID, and creation date. |
| Clicking the information item | Displays the borders, by default for 30 seconds. |
| Flags | Opens the protection settings. |
| Teleport | Teleports you to the plot if a safe location can be found. |
| Rename | Opens a name form; older versions collect the name through chat. |
| Plot list | Lets you switch between your plots. |
| Expansion | Lets you select an adjacent segment or chunk and review its price. |

## A name that is easy to remember

On servers where the Paper Dialog API is available (1.21.7+), the option opens a form containing the current name and Save/Cancel buttons. Validation errors preserve the entered value. The form expires after 60 seconds.

On older versions, or when the legacy interface is forced, you have 60 seconds to type the name in chat. That message is used as the plot name instead of being sent to public chat. If the time expires, open the option again. The maximum plot-name length is 255 characters.

Other operations, including creating, deleting, and expanding plots as well as managing members, use inventory menus. Native dialogs are reserved for interactions that require text input.

You cannot give two of your plots the same name. Prefer simple names such as `Base`, `Farm`, or `Port`. Avoid names such as `add`, `remove`, and `members`, because they are used by commands.

## Returning home

Open the panel of the selected plot and choose teleportation. If teleportation fails, prepare a free and safe location inside the plot and try again. This option does not add a `/home` command.

Next: [Protection and Flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md).
