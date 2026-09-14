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

        if (plugin.claimMode == pl.syntaxdevteam.plotsx.claiming.ClaimMode.CLASSIC && radius > limits.maxRadius) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error", "claim_radius_limit", mapOf("max" to limits.maxRadius.toString())
            ))
            return
        }

        val maxPlots = plugin.hookHandler.getMaxPlots(player)
        val ownerUuid = player.uniqueId
        val ownedPlots = dbh.getPlotsByOwner(ownerUuid)
        if (ownedPlots.size >= maxPlots) {
            player.sendMessage(plugin.messageHandler.stringMessageToComponent(
                "error",
                "max_plots_reached",
                mapOf("max" to maxPlots.toString())
            ))
            return
        }
        val currentArea = ownedPlots.sumOf { it.area }
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

        if (dbh.getPlotsFromAllUsers().any { it.world.equals(world, true) && it.geometry.intersects(claimGeometry(x, z, radius)) }) {
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
        val quotedLocation = player.location.clone()
        val quotedRadius = plugin.hookHandler.getClaimRadius(player)
        val gui = ClaimConfirmGUI(
            plugin = plugin,
            player = player,
            onConfirm = { p ->
                if (!PermissionChecker.canCreatePlot(p)) {
                    p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "no_permission"))
                    return@ClaimConfirmGUI
                }
                if (p.world != quotedLocation.world ||
                    claimGeometry(p.location.blockX, p.location.blockZ, plugin.hookHandler.getClaimRadius(p)).bounds !=
                    claimGeometry(quotedLocation.blockX, quotedLocation.blockZ, quotedRadius).bounds) {
                    p.sendMessage(plugin.interactions.text("claim_changed"))
                    return@ClaimConfirmGUI
                }
                val loc      = p.location
                val uuid     = player.uniqueId
                val world    = loc.world!!.name
                val x        = loc.blockX
                val z        = loc.blockZ
                val y        = loc.blockY
                val radius   = plugin.hookHandler.getClaimRadius(p)
                val maxPlots = plugin.hookHandler.getMaxPlots(p)
                val limits   = plugin.hookHandler.getPlotLimits(p)

                if (plugin.claimMode == pl.syntaxdevteam.plotsx.claiming.ClaimMode.CLASSIC && radius > limits.maxRadius) {
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

                val maxChunksPerPlot = plugin.hookHandler.getMaxChunksPerPlot(p)
                val maxChunksOwned = plugin.hookHandler.getMaxOwnedChunks(p)
                val actor = p.uniqueId
                val namePrefix = "Działka ${p.name}"
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                    val result = try { dbh.claimPlotAtomically(
                        ownerUuid = uuid,
                        actorUuid = actor,
                        world = world,
                        x = x,
                        z = z,
                        y = y,
                        radius = radius,
                        maxPlots = maxPlots,
                        maxTotalArea = limits.maxTotalArea,
                        namePrefix = namePrefix,
                        maxChunksPerPlot = maxChunksPerPlot, maxChunksOwned = maxChunksOwned
                    ) } catch (failure: Exception) {
                        plugin.logger.err("Claim failed: ${failure.message}")
                        DatabaseHandler.ClaimResult.DatabaseError
                    }
                    when (result) {
                        is DatabaseHandler.ClaimResult.Success -> {
                            plugin.server.scheduler.runTask(plugin, Runnable {
                                p.sendMessage(plugin.messageHandler.stringMessageToComponent("plots", "claim_success"))
                                plugin.cacheManager.getPlot(result.plotId)?.let { helpers.visualizePlotBorder3D(p, it, 20, 2, 4) }
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
                        DatabaseHandler.ClaimResult.ChunkLimitReached -> plugin.server.scheduler.runTask(plugin, Runnable {
                            p.sendMessage(plugin.messageHandler.stringMessageToComponent("error", "claim_chunk_limit"))
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
        plugin.regionProtectionHook?.overlapsBounds(world, claimGeometry(x, z, radius).bounds) == true

    private fun plotArea(radius: Int): Long = if (plugin.claimMode == pl.syntaxdevteam.plotsx.claiming.ClaimMode.CHUNKS) 256L else
        pl.syntaxdevteam.plotsx.geometry.ClassicGeometry(pl.syntaxdevteam.plotsx.databases.PlotSegment(0, 0, radius)).area

    private fun claimGeometry(x: Int, z: Int, radius: Int) =
        pl.syntaxdevteam.plotsx.claiming.ClaimGeometryFactory.create(plugin.claimMode, x, z, radius)

    private fun formatLimit(value: Long): String = if (value == Long.MAX_VALUE) "∞" else value.toString()

}
