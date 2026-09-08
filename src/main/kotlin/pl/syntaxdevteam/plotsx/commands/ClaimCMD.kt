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
        val radius = plugin.hookHandler.getClaimRadius(player)
        val limits = plugin.hookHandler.getPlotLimits(player)

        if (!isClaimWorldAllowed(world)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "claim_world_not_allowed"))
            return
        }

        if (!PermissionChecker.canCreatePlot(stack.sender)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
            return
        }

        if (radius > limits.maxRadius) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error", "claim_radius_limit", mapOf("max" to limits.maxRadius.toString())
            ))
            return
        }

        val maxPlots = plugin.hookHandler.getMaxPlots(player)
        val ownerUuid = plugin.uuidManager.getUUID(player.name) // Celowe podczas testów jednoosobowych.
        val ownedPlots = dbh.getPlotsByOwner(ownerUuid)
        if (ownedPlots.size >= maxPlots) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error",
                "max_plots_reached",
                mapOf("max" to maxPlots.toString())
            ))
            return
        }
        val currentArea = ownedPlots.sumOf { plotArea(it.radius) }
        if (currentArea > limits.maxTotalArea - plotArea(radius)) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error", "claim_area_limit", mapOf("max" to formatLimit(limits.maxTotalArea))
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
                val uuid     = plugin.uuidManager.getUUID(player.name) // Celowe podczas testów jednoosobowych.
                val world    = loc.world!!.name
                val x        = loc.blockX
                val z        = loc.blockZ
                val y        = loc.blockY
                val radius   = plugin.hookHandler.getClaimRadius(p)
                val maxPlots = plugin.hookHandler.getMaxPlots(p)
                val limits   = plugin.hookHandler.getPlotLimits(p)

                if (radius > limits.maxRadius) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent(
                        "error", "claim_radius_limit", mapOf("max" to limits.maxRadius.toString())
                    ))
                    return@ClaimConfirmGUI
                }

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
                        maxTotalArea = limits.maxTotalArea,
                        namePrefix = "Działka ${player.name}"
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
                        DatabaseHandler.ClaimResult.AreaLimitReached -> plugin.server.scheduler.runTask(plugin, Runnable {
                            p.sendMessage(plugin.messageHandler.stringMessageToComponent(
                                "error", "claim_area_limit", mapOf("max" to formatLimit(limits.maxTotalArea))
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
        val configured = plugin.config.get("plots.world")
        val worlds = when (configured) {
            null -> return true
            is String -> if (configured.isBlank()) return true else listOf(configured)
            is List<*> -> configured.filterIsInstance<String>()
            else -> return false
        }
        return worlds.any { it.trim() == "*" || it.trim().equals(world, ignoreCase = true) }
    }

    private fun overlapsExternalRegion(world: org.bukkit.World, x: Int, z: Int, radius: Int): Boolean =
        plugin.regionProtectionHook?.overlapsProtectedRegion(world, x, z, radius) == true

    private fun plotArea(radius: Int): Long {
        val side = radius.toLong() * 2L + 1L
        return side * side
    }

    private fun formatLimit(value: Long): String = if (value == Long.MAX_VALUE) "∞" else value.toString()

}
