# Server administration and maintenance

[← Home](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/Home.md)

After worlds, limits, and permissions are configured, players can create and manage plots on their own.

## Check before opening the server

Using a regular player account, create a plot, add a friend, protect a chest, and test expansion. With a second account that is not a plot member, test protection. Operators bypass many restrictions, so an OP account does not represent the normal player experience.

## Day-to-day administration

| Need | Method |
| --- | --- |
| Check the version | `/ptx version` |
| Reload config changes | `/ptx reload` |
| Change database, aliases, or integrations | Perform a full server restart. |
| Help with another player's private container | Use `plotsx.admin.bypass` together with the private-chest command permission. |
| Investigate activity | Use the server console and CoreProtect when available. |

`plotsx.cmd.ptx` also grants import and export access. Do not give it to every player just so they can open help.

## Backups

`/ptx export [mysql|mariadb|sqlite|postgresql|h2]` writes a portable backup to `plugins/PlotsX/dump/backup.sql`. It includes classic and chunk plots, members, flags, history, and the operation journal. Without an argument, it uses the syntax of the current database. Keep a separate world backup as well — the PlotsX backup does not contain world blocks.

For the default SQLite setup:

1. Stop the server.
2. Copy the entire `plugins/PlotsX` directory.
3. Copy the worlds as well so builds, items, and container protection stay in sync.
4. Store the backup elsewhere, preferably with a date in its name.
5. Start the server again.

For H2, also make the backup while the server is stopped. With an external database, create an additional database-level backup using your hosting or database tools. Keep the plugin folder and worlds from the same point in time.

To restore data, stop the server, preserve the current state as another backup, then restore matching plugin, database, and world data. Start the server and verify the plots.

`/ptx import` validates geometry, collisions, and the journal, restores data transactionally where supported by the database engine, and then rebuilds the protection cache. Import is rejected if it would overwrite a currently unresolved payment operation. It does not replace a world backup or a test restore procedure before production deployment.

## Updating PlotsX

1. Create a full backup.
2. Read the notes for the target release.
3. Stop the server and replace the existing plugin file with the new one.
4. Start the server and review startup messages.
5. Test a plot, a private container, and expansion using a regular player account.

During startup, the plugin may add missing default config entries. After an update, check `plots.claiming.mode`, the separate `plots.expansion` and `plots.chunks.expansion` price settings, and chunk limits.

## Payment problem

If a player reports that money was taken without an expansion being applied, check their balance, plot geometry, and messages from that moment. For every paid expansion, record the operation UUID, provider, currency, amount, and state from the `PAYMENT REVIEW` or `PAYMENT RECONCILIATION REQUIRED` message. Do not retry a charge or refund until the provider history has been checked — ambiguous results are intentionally preserved in the operation journal.

## Reporting a problem

Prepare the PlotsX version, server version, a description of the situation, and the error message. Include what the player was doing before the problem occurred. Remove passwords and webhook URLs from any shared files.

### Expansion diagnostics

After temporarily setting `debug: diag`, the log includes `PlotsX expansion timings` entries covering offer preparation, map rendering, plot lookup counts, WorldGuard checks, the transaction executed on a worker, cache publication, and the callback on the main thread. `PlotsX border metrics` reports the number of points and particles per second, the total particles sent to the player, and the number of active sessions. Disable debug after collecting the data — these entries are intended for short diagnostic sessions, not permanent production logging.

A local, non-authoritative border benchmark can be generated with:
`PLOTSX_BENCHMARK=1 ./gradlew test --tests '*BorderPerformanceTest' --console=plain`.
The report is written to `build/reports/expansion-benchmark.md`. This is not a replacement for MSPT testing on the target Paper server with the actual economy and WorldGuard plugins installed.

[Report an issue](https://github.com/SyntaxDevTeam/PlotsX/issues) · [Join the Discord](https://discord.gg/Zk6mxv7eMh)

Next: [FAQ and Troubleshooting](https://github.com/SyntaxDevTeam/PlotsX/blob/main/docs/wiki/en/FAQ.md).
