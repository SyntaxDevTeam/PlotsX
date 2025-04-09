package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.gui.ClaimConfirmGUI

@Suppress("UnstableApiUsage")
class ClaimCMD(private var plugin: PlotsX) : BasicCommand {

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        if (stack.sender !is Player) {
            stack.sender.sendRichMessage("Only players can use this command!")
            return
        }

        if (!stack.sender.hasPermission("plotsx.cmd.claim")) {
            plugin.messageHandler.getMessage("error", "no_permission")
            return
        }

        val player = stack.sender as Player

        ClaimConfirmGUI(plugin).open(player)
    }
}