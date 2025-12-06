package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX

@Suppress("UnstableApiUsage")
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

                    plugin.onReload()
                    stack.sender.sendMessage(mH.miniMessageFormat("<green>The configuration file has been reloaded.</green>"))

                }

                args[0].equals("export", ignoreCase = true) -> {

                    plugin.databaseHandler.exportDatabase()

                }

                args[0].equals("import", ignoreCase = true) -> {

                    plugin.databaseHandler.importDatabase()

                }
            }
        } else {
            stack.sender.sendMessage(mH.miniMessageFormat("<green>Type </green><gold>/ptx help</gold> <green>to see available commands</green>"))
        }
    }

    private fun sendHelp(stack: CommandSourceStack, page: Int) {
        val commands = listOf(
            "  <gold>/plotsx|ptx help <gray>- <white>Displays this prompt.",
            "  <gold>/plotsx|ptx version <gray>- <white>Shows plugin info.",
            "  <gold>/plotsx|ptx reload <gray>- <white>Reloads the configuration file.",
            "  <gold>/claim <gray>- <white>Pozwala zając dany teren podswoją diałkę",
            "  <gold>/unclaim <gray>- <white>usuwa działkę na której się znajdujesz.",
            " ",
            " ",
            " ",
            " "
        )

        val itemsPerPage = 12
        val totalPages = (commands.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(1, totalPages)

        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|    <gold>Available commands for $plName:"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (currentPage * itemsPerPage).coerceAtMost(commands.size)
        for (i in startIndex until endIndex) {
            stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|  ${commands[i]}"))
        }

        val prevPage = if (currentPage > 1) currentPage - 1 else totalPages
        val nextPage = if (currentPage < totalPages) currentPage + 1 else 1
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(
            " <gray>| (Page $currentPage/$totalPages) <click:run_command:'/ptx help $prevPage'><white>[Previous]</white></click>   " +
                    "<click:run_command:'/ptx help $nextPage'><white>[Next]</white></click>"
        ))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>|"))
        stack.sender.sendMessage(mH.miniMessageFormat(" <gray>+-------------------------------------------------"))
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        if (!stack.sender.hasPermission("plotsx.cmd.ptx")) {
            return emptyList()
        }
        return when (args.size) {
            1 -> listOf("help", "version", "reload", "export", "import")
            else -> emptyList()
        }
    }
}
