package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import java.util.UUID

@Suppress("UnstableApiUsage")
class PrivateChestCMD(private val plugin: PlotsX) : BasicCommand {
    private val messages get() = plugin.messageHandler

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as? Player ?: run {
            stack.sender.sendMessage(messages.stringMessageToComponent("error", "console"))
            return
        }
        if (!PermissionChecker.canManagePrivateChests(player)) {
            player.sendMessage(messages.stringMessageToComponent("error", "no_permission"))
            return
        }

        val block = player.getTargetBlockExact(6)
        if (block == null || !plugin.privateChestManager.isSupported(block)) {
            send(player, "look_at_chest")
            return
        }
        val plot = plugin.cacheManager.getCachedPlots().firstOrNull {
            it.world.equals(block.world.name, true) &&
                it.contains(block.x, block.z)
        } ?: run {
            send(player, "plot_required")
            return
        }
        val protection = plugin.privateChestManager.getProtection(block, plot.id)

        when (args.firstOrNull()?.lowercase()) {
            "lock" -> {
                if (!isPlotParticipant(player, plot)) {
                    player.sendMessage(messages.stringMessageToComponent("error", "not_owner"))
                    return
                }
                if (protection != null) {
                    send(player, if (canManage(player, protection.owner)) "already_locked" else "not_chest_owner")
                    return
                }
                plugin.privateChestManager.lock(block, plot.id, player.uniqueId)
                send(player, "locked")
            }
            "unlock" -> {
                if (protection == null) {
                    send(player, "not_locked")
                    return
                }
                if (!canManage(player, protection.owner)) {
                    send(player, "not_chest_owner")
                    return
                }
                plugin.privateChestManager.unlock(block)
                send(player, "unlocked")
            }
            "trust", "share" -> changeTrust(player, block, plot, protection, args.getOrNull(1), true)
            "untrust", "unshare" -> changeTrust(player, block, plot, protection, args.getOrNull(1), false)
            "info" -> showInfo(player, protection)
            else -> send(player, "usage")
        }
    }

    private fun changeTrust(
        player: Player,
        block: Block,
        plot: PlotData,
        protection: pl.syntaxdevteam.plotsx.protection.PrivateChestManager.Protection?,
        input: String?,
        add: Boolean
    ) {
        if (protection == null) {
            send(player, "not_locked")
            return
        }
        if (!canManage(player, protection.owner)) {
            send(player, "not_chest_owner")
            return
        }
        if (input == null) {
            send(player, "usage")
            return
        }
        val target = findPlotParticipant(plot, input)
        if (target == null) {
            send(player, "player_not_on_plot", mapOf("player" to input))
            return
        }
        if (target == protection.owner) {
            send(player, "owner_cannot_be_shared")
            return
        }

        if (add) {
            if (target in protection.trusted) {
                send(player, "already_shared", mapOf("player" to displayName(target)))
                return
            }
            plugin.privateChestManager.trust(block, protection, target)
            send(player, "shared", mapOf("player" to displayName(target)))
        } else {
            if (target !in protection.trusted) {
                send(player, "not_shared", mapOf("player" to displayName(target)))
                return
            }
            plugin.privateChestManager.untrust(block, protection, target)
            send(player, "unshared", mapOf("player" to displayName(target)))
        }
    }

    private fun showInfo(player: Player, protection: pl.syntaxdevteam.plotsx.protection.PrivateChestManager.Protection?) {
        if (protection == null) {
            send(player, "not_locked")
            return
        }
        val trusted = protection.trusted.map(::displayName).sorted().joinToString(", ").ifEmpty { "-" }
        send(player, "info", mapOf("owner" to displayName(protection.owner), "trusted" to trusted))
    }

    private fun isPlotParticipant(player: Player, plot: PlotData): Boolean =
        PermissionChecker.canBypassPlots(player) || player.uniqueId == plot.ownerUuid ||
            plugin.cacheManager.getMembers(plot.id).orEmpty().any { it.memberUuid == player.uniqueId.toString() }

    private fun findPlotParticipant(plot: PlotData, input: String): UUID? {
        val participants = buildSet {
            add(plot.ownerUuid)
            plugin.cacheManager.getMembers(plot.id).orEmpty().mapTo(this) { UUID.fromString(it.memberUuid) }
        }
        val parsed = runCatching { UUID.fromString(input) }.getOrNull()
        if (parsed != null) return parsed.takeIf { it in participants }
        return participants.firstOrNull { displayName(it).equals(input, true) }
    }

    private fun canManage(player: Player, owner: UUID): Boolean =
        player.uniqueId == owner || PermissionChecker.canBypassPlots(player)

    private fun displayName(uuid: UUID): String = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()

    private fun send(player: Player, key: String, placeholders: Map<String, String> = emptyMap()) {
        player.sendMessage(messages.stringMessageToComponent("private_chest", key, placeholders))
    }

    override fun suggest(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>): List<String> {
        val player = stack.sender as? Player ?: return emptyList()
        if (!PermissionChecker.canManagePrivateChests(player)) return emptyList()
        return when (args.size) {
            1 -> listOf("lock", "unlock", "trust", "untrust", "info")
                .filter { it.startsWith(args[0], true) }
            2 -> if (args[0].equals("trust", true) || args[0].equals("untrust", true)) {
                plugin.cacheManager.getCachedPlots().firstOrNull {
                    it.world.equals(player.world.name, true) &&
                        it.contains(player.location.blockX, player.location.blockZ)
                }?.let { plot ->
                    buildList {
                        add(plot.ownerUuid)
                        plugin.cacheManager.getMembers(plot.id).orEmpty().mapTo(this) { UUID.fromString(it.memberUuid) }
                    }.map(::displayName).filter { it.startsWith(args[1], true) }
                }.orEmpty()
            } else emptyList()
            else -> emptyList()
        }
    }
}
