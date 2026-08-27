package pl.syntaxdevteam.plotsx.commands

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player
import org.jetbrains.annotations.NotNull
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.DatabaseHandler
import pl.syntaxdevteam.plotsx.databases.Helpers
import pl.syntaxdevteam.plotsx.gui.ClaimConfirmGUI
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker

class ClaimCMD(private var plugin: PlotsX) : BasicCommand {
    val dbh = plugin.databaseHandler
    val helpers = Helpers(plugin)

    override fun execute(@NotNull stack: CommandSourceStack, @NotNull args: Array<String>) {
        val player = stack.sender as? Player ?: run {
            stack.sender.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "console"))
            return
        }
        val location = player.location
        val world = location.world.name
        val x = location.blockX
        val z = location.blockZ
        val radius = plugin.config.getInt("plots.radius", 16)

        if (!isClaimWorldAllowed(world)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "claim_world_not_allowed"))
            return
        }

        if (!PermissionChecker.canCreatePlot(stack.sender)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        val maxPlots = plugin.config.getInt("plots.maxPlots", 5).coerceAtLeast(0)
        val ownerUuid = plugin.uuidManager.getUUID(player.name) // Celowe podczas testów jednoosobowych.
        if (dbh.getPlotsByOwner(ownerUuid).size >= maxPlots) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error",
                "max_plots_reached",
                mapOf("max" to maxPlots.toString())
            ))
            return
        }

        val currentPlot = dbh.getPlotAtLocation(world, x, z)
        if (currentPlot != null) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "is_plot"))
            return
        }

        if (dbh.doesPlotOverlap(x, z, radius, world)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "in_collision"))
            return
        }

        if (overlapsExternalRegion(player.world, x, z, radius)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "worldguard_collision"))
            return
        }

        openClaimGui(player)
    }

    fun openClaimGui(player: Player) {
        val gui = ClaimConfirmGUI(
            plugin = plugin,
            player = player,
            onConfirm = { p ->
                val loc      = p.location
                val uuid     = plugin.uuidManager.getUUID("yRoshee") // Celowe podczas testów jednoosobowych.
                val world    = loc.world!!.name
                val x        = loc.blockX
                val z        = loc.blockZ
                val y        = loc.blockY
                val radius   = plugin.config.getInt("plots.radius", 16)
                val maxPlots = plugin.config.getInt("plots.maxPlots", 5).coerceAtLeast(0)

                if (!isClaimWorldAllowed(world)) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "claim_world_not_allowed"))
                    return@ClaimConfirmGUI
                }

                if (overlapsExternalRegion(loc.world!!, x, z, radius)) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "worldguard_collision"))
                    return@ClaimConfirmGUI
                }

                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    when (dbh.claimPlotAtomically(
                        ownerUuid = uuid,
                        actorUuid = p.uniqueId,
                        world = world,
                        x = x,
                        z = z,
                        y = y,
                        radius = radius,
                        maxPlots = maxPlots,
                        namePrefix = "Działka yRoshee"
                    )) {
                        is DatabaseHandler.ClaimResult.Success -> {
                            plugin.cacheManager.refreshAllCachesAsync()
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "claim_success"))
                                helpers.visualizePlotBorder3D(
                                    player    = p,
                                centerX   = x,
                                centerZ   = z,
                                radius    = radius,
                                durationSec = 20,
                                stepXZ      = 2,
                                stepY    = 4
                                )
                            })
                        }
                        DatabaseHandler.ClaimResult.LimitReached -> plugin.server.scheduler.runTask(plugin, Runnable {
                            p.sendMessage(plugin.messageHandler.stringMessageToComponent(
                                "error",
                                "max_plots_reached",
                                mapOf("max" to maxPlots.toString())
                            ))
                        })
                        DatabaseHandler.ClaimResult.Overlap -> plugin.server.scheduler.runTask(plugin, Runnable {
                            p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "in_collision"))
                        })
                        DatabaseHandler.ClaimResult.DatabaseError -> plugin.server.scheduler.runTask(plugin, Runnable {
                            p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "create_error"))
                        })
                    }
                })
            },
            onCancel = { p ->
                p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "claim_cancelled"))
            }
        )

        plugin.guiHandler.registerGui(player, gui)
    }

    private fun isClaimWorldAllowed(world: String): Boolean {
        val configuredWorld = plugin.config.getString("plots.world")?.trim().orEmpty()
        return configuredWorld.isEmpty() || configuredWorld == "*" || configuredWorld.equals(world, ignoreCase = true)
    }

    private fun overlapsExternalRegion(world: org.bukkit.World, x: Int, z: Int, radius: Int): Boolean =
        plugin.regionProtectionHook?.overlapsProtectedRegion(world, x, z, radius) == true

}
