<div align="center">
  <h1>PlotsX</h1>
  <p><strong>Claim land, grow it naturally, and manage everything from one clean interface.</strong></p>
  <p>
    <a href="#-compatibility"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.2.0/assets/cozy/supported/paper_vector.svg" alt="Supports Paper" height="56"></a>
    <a href="#-compatibility"><img src="https://github.com/SyntaxDevTeam/PunisherX/raw/main/assets/badges/folia.svg" alt="Supports Folia" height="28"></a>
  </p>

[![Build](https://github.com/SyntaxDevTeam/PlotsX/actions/workflows/buildexplorer.yml/badge.svg?branch=main)](https://github.com/SyntaxDevTeam/PlotsX/actions/workflows/buildexplorer.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://github.com/SyntaxDevTeam/PlotsX/blob/main/LICENSE)

**[Features](#-features)** · **[Quick start](#-quick-start)** · **[Documentation](#-documentation)** · **[API](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/api.md)** · **[Discord](https://discord.gg/Zk6mxv7eMh)**

</div>

PlotsX is a lightweight land-protection plugin built for survival servers and shared player worlds. Players can claim an area, invite friends, configure protection rules, protect private containers, expand their land and teleport back to it without learning a large command set.

The plugin supports both traditional square plots and free-form chunk claims. Most player-facing management is available through `/plot`, while administrators keep control over worlds, limits, prices, permissions and integrations.

This README describes the current `main` branch and is intended to serve as the project overview for GitHub and plugin marketplaces.

## ✨ Features

| Feature | What PlotsX provides |
| --- | --- |
| **Two claiming systems** | Use classic square plots or chunk-based plots. Existing plots keep their original geometry when the server changes the claiming mode. |
| **GUI-first management** | `/plot` gives players access to plot information, members, flags, teleportation, renaming, borders and expansion. |
| **Flexible expansion** | Classic plots grow through adjacent segments. Chunk plots can buy neighboring chunks and remove individual chunks while preserving a connected shape. |
| **Members and roles** | Invite players, assign `member`, `builder` or `manager`, configure per-role grants and transfer ownership. |
| **Protection flags** | Control building, containers, PvP, redstone, pistons, mobs, animals, interactions and other plot behavior. |
| **Private containers** | Lock supported containers and selectively trust other players without exposing the whole plot. |
| **Custom teleport point** | Plot owners can choose a safe location inside their plot to use as its teleport destination. |
| **Native Paper dialogs** | Plot renaming uses Paper's native Dialog API on Paper 1.21.7+ with a compatible chat fallback on older servers. |
| **Safe paid expansion** | Vault/VaultUnlocked economies can charge for expansion. Purchase state is journaled so land and money changes can be reconciled safely. |
| **Multiple databases** | SQLite, H2, MySQL, MariaDB and PostgreSQL are supported. |
| **Public API v2** | Java and Kotlin integrations can query and work with PlotsX through the dedicated API artifact. |

### Designed for player-owned worlds

PlotsX does not require a separate generated plot world. Administrators decide which normal worlds allow claims, and each plot covers the full vertical height of the world.

The plugin is suitable for survival bases, towns, farms, shared projects and other servers where players should claim land where they already play.

## 🧭 Classic plots or chunk plots

The claiming mode is selected by the administrator:

| Classic | Chunks |
| --- | --- |
| Starts as a square around the player's location. | Starts as the 16 × 16 chunk the player is standing in. |
| Grows through adjacent square segments. | Grows by purchasing directly adjacent chunks. |
| Works well when every plot should have a predictable shape. | Allows irregular shapes that follow the player's base naturally. |
| Radius, size and total-area limits can be configured per rank. | Per-plot and total chunk limits can be configured per rank. |

Changing the default mode does not delete existing plots. Classic and chunk plots remain protected and continue using their own expansion rules.

## 🛡️ Protection and ownership

PlotsX protects more than block breaking. Built-in flags cover common survival interactions including containers, redstone, pistons, PvP, animals, hostile mobs and utility blocks.

Members can be assigned roles with separate grants. Owners and administrators can control who may invite or remove players, rename the plot, change individual flags or transfer ownership.

Private containers add a second layer of access control. A chest, barrel or other supported container can be locked for one player and shared only with selected users even when other members have access to the plot.

## 💰 Expansion and economy

Plot expansion can be free or paid. When an economy is configured, PlotsX uses VaultUnlocked first and falls back to Vault.

Prices, multipliers and upper limits are configurable by the server. Expansion operations use a persistent operation journal so confirmed land changes, debits and refunds can be reconciled instead of relying on blind retries after a failure or disconnect.

Chunk plots are managed interactively: players can purchase neighboring chunks without leaving the expansion workflow and can remove eligible individual chunks from a dedicated GUI. The original anchor chunk cannot be removed, and removals that would split a plot into disconnected pieces are rejected.

## 🔌 Integrations

| Integration | Purpose |
| --- | --- |
| **Vault / VaultUnlocked** | Paid plot expansion through the server economy. Free prices work without an economy plugin. |
| **WorldGuard** | Prevent claims and expansion on protected server regions, or explicitly allow them with the `plotsx-claim` flag. |
| **LuckPerms** | Convenient permission and per-rank limit management. Any compatible permission system can still grant PlotsX nodes. |
| **CoreProtect** | Pass block and container activity to the server's existing history tooling. |
| **CleanerX** | Validate new plot names against the configured word filter. |
| **PlaceholderAPI / MiniPlaceholders** | Detected as optional integrations; PlotsX currently does not expose a public placeholder set. |

PlotsX works without these integrations for normal free claiming and protection.

## 📦 Compatibility

| Requirement | Supported range |
| --- | --- |
| **Platform** | Paper and compatible forks, including Purpur; Folia scheduling is supported. |
| **Minecraft** | Recognized versions: **1.20.6**, **1.21–1.21.11**, **26.1–26.3**. |
| **Java** | Java **21+**, or the newer Java version required by your server build. |
| **Artifact** | `PlotsX-Paper-<version>.jar` |
| **Native dialogs** | Paper **1.21.7+** when the Dialog API is available. |

The current release line is **1.0.0**. Compatibility recognition does not mean every possible server/plugin combination has been certified, so test upgrades on a staging server before deploying them to production.

Public marketplace links will be added here when their PlotsX project pages are published. Current development artifacts are available from successful [Build Explorer workflow runs](https://github.com/SyntaxDevTeam/PlotsX/actions/workflows/buildexplorer.yml).

## 🚀 Quick start

1. Build or obtain `PlotsX-Paper-<version>.jar` and place it in the server's `plugins/` directory.
2. Start the server once to generate the configuration and language files.
3. Configure allowed worlds and choose `classic` or `chunks` as the claiming mode.
4. Grant players the required `plotsx.*` permissions.
5. Restart the server after changing the claiming mode or installing integrations that participate during plugin startup.
6. Join with a normal player account and verify claiming and guest protection before opening the feature to everyone.

For players, the basic flow is intentionally short:

1. Stand in a free area and run `/claim`.
2. Confirm the claim in the GUI.
3. Run `/plot` to manage the new plot.
4. Invite a friend with `/plot add <player>` or use the member interface.
5. Expand the plot later when more space is needed.

## 💬 Commands and permissions

| Task | Command | Permission |
| --- | --- | --- |
| Claim land | `/claim` | `plotsx.cmd.claim` |
| Remove your plot | `/unclaim` | `plotsx.cmd.unclaim` |
| Open plot management | `/plot` or `/plot <name>` | `plotsx.cmd.plot` |
| Add/remove members | `/plot add <player>`, `/plot remove <player>` | `plotsx.cmd.plot` plus the required role grant |
| Manage roles | `/plot role <player> <role>` | Owner or administrator access |
| Configure role grants | `/plot permission <role> <grant> <true\|false>` | Owner or administrator access |
| Transfer ownership | `/plot transfer <player> confirm` | Owner or administrator access |
| Expand a plot | Expansion GUI | `plotsx.plot.expand` |
| Manage private containers | `/privatechest ...` / `/pchest ...` | `plotsx.cmd.privatechest` |
| Administration / reload / backup | `/ptx ...` / `/plotsx ...` | `plotsx.cmd.ptx` |

The administrator interface also supports listing and managing plots belonging to offline players. See the full **[command reference](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Commands.md)** and **[permission reference](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Permissions.md)** for exact syntax and limit nodes.

## 🧩 Player interface

`/plot` is the central entry point. Depending on context, it opens the plot you are standing on or a list of your own plots.

From the panel, players can:

- inspect plot information and display its border;
- manage flags and members;
- rename the plot;
- teleport to it;
- set its teleport point from a safe position inside the plot;
- expand classic or chunk plots;
- remove eligible chunks from chunk plots.

On Paper 1.21.7+, renaming uses a native Minecraft dialog. Older versions retain the chat-input fallback, so one PlotsX codebase can support both modern and legacy interaction paths.

## 🗄️ Storage and reliability

PlotsX keeps protection data cache-backed for normal gameplay and supports SQLite, H2, MySQL, MariaDB and PostgreSQL for persistent storage.

Expansion purchases use a durable operation journal and transactional land commits. This makes failures easier to recover from and avoids treating an uncertain economy response as permission to repeat a debit blindly.

Administrators can export and import PlotsX data with `/ptx export` and `/ptx import`. Keep a database and plugin-data backup before upgrades or migrations.

## 📚 Documentation

The PlotsX wiki is maintained in both English and Polish. Language versions are kept side by side and cover the same user and administrator topics.

| Reference | 🇬🇧 English | 🇵🇱 Polski |
| --- | --- | --- |
| Wiki home | [Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md) | [Strona główna](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Home.md) |
| Getting started | [Getting Started](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Getting-Started.md) | [Pierwsze kroki](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Pierwsze-kroki.md) |
| Installation | [Installation](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Installation.md) | [Instalacja](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Instalacja.md) |
| Configuration | [Configuration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Configuration.md) | [Konfiguracja](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Konfiguracja.md) |
| Plot panel | [Plot Panel](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Plot-Panel.md) | [Panel działki](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Panel-dzialki.md) |
| Expansion | [Expansion and Limits](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Expansion.md) | [Rozszerzanie](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Rozszerzanie.md) |
| Protection and flags | [Protection and Flags](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Protection-and-Flags.md) | [Ochrona i flagi](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Ochrona-i-flagi.md) |
| Members and shared play | [Playing Together](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Playing-Together.md) | [Wspólna gra](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Wspolna-gra.md) |
| Private containers | [Private Chests](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Private-Chests.md) | [Prywatne skrzynie](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Prywatne-skrzynie.md) |
| Integrations | [Integrations](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Integrations.md) | [Integracje](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Integracje.md) |
| Commands | [Commands](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Commands.md) | [Komendy](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Komendy.md) |
| Permissions | [Permissions](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Permissions.md) | [Uprawnienia](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Uprawnienia.md) |
| Administration | [Administration](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Administration.md) | [Administracja](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/Administracja.md) |
| FAQ | [FAQ and Troubleshooting](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/FAQ.md) | [Pytania i problemy](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/FAQ.md) |

Developer documentation: **[API v2](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/api.md)**.

## 🛠️ Build from source

PlotsX uses Gradle and a Java 21 toolchain.

```bash
./gradlew clean buildAll
```

The installable server artifact is written to:

```text
build/libs/PlotsX-Paper-1.0.0.jar
```

The compile-only API artifact is written alongside it as:

```text
build/libs/PlotsX-1.0.0-api.jar
```

The API JAR is for integration developers and is **not** a server plugin.

## 🤝 Support and contributions

For support, join the **[SyntaxDevTeam Discord](https://discord.gg/Zk6mxv7eMh)**. Reproducible bugs can be reported through **[GitHub Issues](https://github.com/SyntaxDevTeam/PlotsX/issues)** with the PlotsX version, server platform/version, relevant logs and steps to reproduce.

Contributions and feedback are welcome. PlotsX is released under the **[MIT License](https://github.com/SyntaxDevTeam/PlotsX/blob/main/LICENSE)**.
