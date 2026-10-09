package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.SqlBackup

class PlotsXCMD(private val plugin: PlotsX) : BasicCommand {
    private val mH = plugin.messageHandler
    private val website = plugin.pluginMeta.website
    private val author = plugin.pluginMeta.authors
    private val plName = plugin.pluginMeta.name
    private val version = plugin.pluginMeta.version

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        if (!stack.sender.hasPermission("plotsx.cmd.ptx")) {
            stack.sender.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        if (args.isNotEmpty()) {
            when {
                args[0].equals("help", ignoreCase = true) -> {
                    val page = args.getOrNull(1)?.toIntOrNull() ?: 1
                    sendHelp(stack, page)
                }

                args[0].equals("version", ignoreCase = true) -> {
                    stack.sender.sendMessage(
                        mH.miniMessageFormat(
                            "\n<gray>-------------------------------------------------\n" +
                                    " <gray>|\n" +
                                    " <gray>|   <gold>→ <bold>" + plName + "</bold> ←\n" +
                                    " <gray>|   <white>Author: <bold><gold>" + author + "</gold></bold>\n" +
                                    " <gray>|   <white>Website: <bold><gold><click:open_url:'" + website + "'>" + website + "</click></gold></bold>\n" +
                                    " <gray>|   <white>Version: <bold><gold>" + version + "</gold></bold>\n" +
                                    " <gray>|" +
                                    "\n-------------------------------------------------"
                        )
                    )
                }

                args[0].equals("reload", ignoreCase = true) -> {
                    try {
                        plugin.onReload { failure ->
                            plugin.schedulerAdapter.runForSender(stack.sender, Runnable {
                            if (failure == null) {
                                stack.sender.sendMessage(mH.miniMessageFormat("<green>The configuration file has been reloaded.</green>"))
                            } else {
                                stack.sender.sendMessage("Reload unavailable: ${failure.message}")
                            }
                            })
                        }
                    } catch (failure: IllegalArgumentException) {
                        stack.sender.sendMessage("Reload rejected: ${failure.message}")
                    } catch (failure: IllegalStateException) {
                        stack.sender.sendMessage("Reload unavailable: ${failure.message}")
                    }
                }

                args[0].equals("export", ignoreCase = true) -> {
                    if (args.size > 2) {
                        stack.sender.sendMessage("Usage: /ptx export [mysql|mariadb|sqlite|postgresql|h2]")
                        return
                    }
                    val dialect = args.getOrNull(1)
                    databaseTask(stack.sender, "Database export") {
                        val file = if (dialect != null) plugin.databaseHandler.exportDatabase(dialect)
                        else plugin.databaseHandler.exportDatabase()
                        "Backup saved: ${file.absolutePath}"
                    }
                }

                args[0].equals("import", ignoreCase = true) -> {
                    if (args.size != 1) {
                        stack.sender.sendMessage("Usage: /ptx import")
                        return
                    }
                    databaseTask(stack.sender, "Database import") {
                        plugin.databaseHandler.importDatabase()
                        "Database restored from dump/backup.sql."
                    }
                }
            }
        } else {
            stack.sender.sendMessage(mH.stringMessageToComponentNoPrefix("help", "hint"))
        }
    }

    /** Heavy backup/restore work is never allowed to occupy the Paper server thread. */
    private fun databaseTask(sender: CommandSender, operationName: String, operation: () -> String) {
        plugin.schedulerAdapter.runAsync(Runnable {
            val result = try {
                Result.success(operation())
            } catch (exception: Exception) {
                plugin.logger.err("$operationName failed: ${exception.message}")
                Result.failure(exception)
            }
            if (!plugin.isEnabled) return@Runnable
            plugin.schedulerAdapter.runForSender(sender, Runnable {
                result.fold(
                    onSuccess = { message -> sender.sendMessage(message) },
                    onFailure = { failure -> sender.sendMessage("$operationName failed: ${failure.message}") }
                )
            })
        })
    }

    private val helpEntries = listOf(
        "/plx help [page]" to "help",
        "/plx version" to "version",
        "/plx reload" to "reload",
        "/plx export [mysql|mariadb|sqlite|postgresql|h2]" to "export",
        "/plx import" to "import",
        "/claim" to "claim",
        "/unclaim" to "unclaim",
        "/plot" to "plot",
        "/plot <name>" to "plot_name",
        "/plot members" to "members",
        "/plot add <player>" to "add",
        "/plot remove <player>" to "remove",
        "/plot role <player> <member|builder|manager>" to "role",
        "/plot permission <rank> <action> <true|false>" to "permission",
        "/plot transfer <player> confirm" to "transfer",
        "/plot admin list <player|UUID>" to "admin_list",
        "/plot admin <id> [action ...]" to "admin",
        "/plot admin <id> move confirm" to "admin_move",
        "/privatechest lock" to "lock",
        "/privatechest unlock" to "unlock",
        "/privatechest trust|share <player>" to "trust",
        "/privatechest untrust|unshare <player>" to "untrust",
        "/privatechest info" to "info"
    )
    private val helpPageSize = 12
    private val helpPages get() = (helpEntries.size + helpPageSize - 1) / helpPageSize

    private fun sendHelp(stack: CommandSourceStack, page: Int) {
        val currentPage = page.coerceIn(1, helpPages)
        fun text(key: String, values: Map<String, String> = emptyMap()) =
            mH.stringMessageToComponentNoPrefix("help", key, values)
        fun line(content: net.kyori.adventure.text.Component) =
            stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|  ").append(content))
        val border = mH.miniMessageFormat(" <gray>+-------------------------------------------------")
        stack.sender.sendMessage(border)
        line(text("header", mapOf("plugin" to plName)))
        line(net.kyori.adventure.text.Component.empty())
        helpEntries.drop((currentPage - 1) * helpPageSize).take(helpPageSize).forEach { (syntax, key) ->
            // Command arguments are literal text, not MiniMessage tags.
            line(net.kyori.adventure.text.Component.text(syntax, net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text(" — ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(text("commands.$key").colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.WHITE)))
        }
        line(net.kyori.adventure.text.Component.empty())
        line(text("aliases"))
        val configured = plugin.config.getConfigurationSection("aliases")
        listOf("claim", "unclaim").forEach { command ->
            val alias = configured?.getString(command)
            if (!alias.isNullOrBlank() && alias != command) {
                line(net.kyori.adventure.text.Component.text("/$alias → /$command",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY))
            }
        }
        var footer = text("page", mapOf("page" to currentPage.toString(), "pages" to helpPages.toString()))
        if (currentPage > 1) footer = footer.append(net.kyori.adventure.text.Component.space())
            .append(text("previous").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plx help ${currentPage - 1}")))
        if (currentPage < helpPages) footer = footer.append(net.kyori.adventure.text.Component.space())
            .append(text("next").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/plx help ${currentPage + 1}")))
        line(footer)
        stack.sender.sendMessage(border)
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("plotsx.cmd.ptx")) {
            return emptyList()
        }
        return when (args.size) {
            1 -> listOf("help", "version", "reload", "export", "import")
            2 -> if (args[0].equals("help", ignoreCase = true)) (1..helpPages).map(Int::toString)
                .filter { it.startsWith(args[1]) }
            else if (args[0].equals("export", ignoreCase = true)) SqlBackup.dialects.filter {
                it.startsWith(args[1], ignoreCase = true)
            } else emptyList()
            else -> emptyList()
        }
    }
}
