package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Dispenser
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Openable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketEntityEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.EquipmentSlot
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID

class PlotProtectionListener(private val plugin: PlotsX) : Listener {

    private val logger = plugin.logger
    private val message = plugin.messageHandler
    private val playerLastPlot = mutableMapOf<UUID, Int?>()
    private val toggling: MutableSet<Block> = mutableSetOf()

    private val aggressiveMobs: Set<EntityType> by lazy { PlotCompat.loadAggressiveMobs() }
    private val passiveMobs: Set<EntityType>   by lazy { PlotCompat.loadPassiveMobs() }
    private val doorsAndGates: Set<Material>   by lazy { PlotCompat.loadDoorsAndGates() }
    private val buttonsAndLevers: Set<Material> by lazy { PlotCompat.loadButtonsAndLevers() }
    private val containers: Set<Material>      by lazy { PlotCompat.loadContainers() }
    private val enderChest: Set<Material>      by lazy { PlotCompat.loadEnderChest() }
    private val dispenserBuckets: Set<Material> by lazy { PlotCompat.loadDispenserBucketMaterials() }


    private fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        return plugin.cacheManager.getCachedPlots().firstOrNull { plot ->
            plot.world.equals(world, ignoreCase = true) &&
                    x in (plot.x - plot.radius..plot.x + plot.radius) &&
                    z in (plot.z - plot.radius..plot.z + plot.radius)
        }
    }

    private fun cancelAndRestore(block: Block) {
        block.state.update(true, false)
        logger.debug("[cancelAndRestore] Restoring block at ${block.location.blockX},${block.location.blockZ} to its original state.")
    }

    private fun hasPlotPermission(player: Player, plot: PlotData, flag: String): Boolean {
        if (hasBypass(player)) return true
        if (player.uniqueId == plot.ownerUuid) return true

        val isMember = plugin.cacheManager.getMembers(plot.id)
            ?.any { it.memberUuid == player.uniqueId.toString() } ?: false
        if (isMember) return true

        val flagDefinition = PlotFlagRegistry.allFlags[flag]
            ?: run {
                plugin.logger.debug("[hasPlotPermission] Nieznana flaga '$flag' na działce ${plot.id} (${plot.name})")
                return false
            }

        val flags = plugin.cacheManager.getFlags(plot.id) ?: emptyMap()
        val value = flags[flag] ?: flagDefinition.defaultValue

        plugin.logger.debug(
            "[hasPlotPermission] Flaga '$flag' = $value (domyślnie=${flagDefinition.defaultValue}, typ=${flagDefinition.type}) " +
                    "dla gracza ${player.name} na działce ${plot.id} (${plot.name})"
        )

        return when (flagDefinition.type) {
            FlagType.WHITELIST -> value     // true = pozwól obcemu
            FlagType.BLACKLIST -> !value    // true = blokuj → !true = false
        }
    }


    private fun printAllFlagBehaviors() {
        PlotFlagRegistry.allFlags.forEach { (key, def) ->
            println("Flaga $key: default=${def.defaultValue}, typ=${def.type}, efektDlaObcego=${when (def.type) {
                FlagType.WHITELIST -> def.defaultValue
                FlagType.BLACKLIST -> !def.defaultValue
            }}")
        }
    }

    private fun hasBypass(player: Player): Boolean {
        return player.isOp //|| player.hasPermission("plotsx.plot.bypass")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (event.from.blockX == event.to.blockX &&
            event.from.blockZ == event.to.blockZ &&
            event.from.world == event.to.world
        ) return

        val uuid = player.uniqueId
        val to = event.to
        val newPlot = getPlotAtLocation(to.world.name, to.blockX, to.blockZ)
        val oldPlotId = playerLastPlot[uuid]
        val newPlotId = newPlot?.id

        if (oldPlotId != newPlotId) {
            playerLastPlot[uuid] = newPlotId

            if (oldPlotId != null) {
                val oldPlot = plugin.cacheManager.getCachedPlots().firstOrNull { it.id == oldPlotId }
                if (oldPlot != null) {
                    player.sendMessage(message.getMessage("plots", "leave_plot", mapOf("plot" to oldPlot.name)))
                    }
            }

            if (newPlot != null) {
                player.sendMessage(message.getMessage("plots", "enter_plot", mapOf("plot" to newPlot.name)))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)

        logger.debug("BlockPlaceEvent at ${loc.blockX},${loc.blockZ} => plot=${plot?.id}")

        if (plot != null && !hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage(message.getMessage("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)

        logger.debug("BlockBreakEvent at ${loc.blockX},${loc.blockZ} => plot=${plot?.id}")

        if (plot != null && !hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage(message.getMessage("flags", "build.break_not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onFallingBlock(event: EntityChangeBlockEvent) {
        if (event.entityType != EntityType.FALLING_BLOCK) return

        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        logger.debug("EntityChangeBlockEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
        val flags = plugin.cacheManager.getFlags(plot.id) ?: return
        if (flags["fall"] == false) {
            if (event.entityType == EntityType.FALLING_BLOCK) {
                event.isCancelled = true
                event.block.blockData = event.block.blockData
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onLiquidFlow(event: BlockFromToEvent) {
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)
            ?: getPlotAtLocation(event.toBlock.location.world.name, event.toBlock.location.blockX, event.toBlock.location.blockZ)

        logger.debug("BlockFromToEvent at ${loc.blockX},${loc.blockZ} => plot=${plot?.id}")
        if (plot != null && plugin.cacheManager.getFlags(plot.id)?.get("flow") == false) {
            event.isCancelled = true
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        logger.debug("PlayerBucketEmptyEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "flow.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        logger.debug("PlayerBucketFillEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "flow.bucket_not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEntity(event: PlayerBucketEntityEvent) {
        val player = event.player
        val entity = event.entity
        val loc = entity.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        logger.debug("PlayerBucketEntityEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "flow.bucket_not_allowed"))
        }
    }
    /**
    * Zablokuj wpychanie/przesuwanie bloków na lub z działek.
    */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPistonExtend(event: BlockPistonExtendEvent) {

        val dx = event.direction.modX
        val dy = event.direction.modY
        val dz = event.direction.modZ

        for (block in event.blocks) {
            val from = block.location
            val to   = from.clone().add(dx.toDouble(), dy.toDouble(), dz.toDouble())

            if (getPlotAtLocation(from.world.name, from.blockX, from.blockZ) != null ||
                getPlotAtLocation(to.world.name,   to.blockX,   to.blockZ)   != null
            ) {
                event.isCancelled = true
                return
            }
        }
    }

    /**
     * Zablokuj przyciąganie bloków przez lepki tłok
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (!event.isSticky) return

        // wektor „przyciągania” – odwrotność extend, ale dla simplicity patrzymy tylko na miejsce tłoka
        for (block in event.blocks) {
            val from = block.location
            val to   = event.block.location

            if (getPlotAtLocation(from.world.name, from.blockX, from.blockZ) != null ||
                getPlotAtLocation(to.world.name,   to.blockX,   to.blockZ)   != null
            ) {
                event.isCancelled = true
                return
            }
        }
    }

    /**
     * Zablokuj wylewanie płynów i powder_snow przez dyspensery na lub z działek.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDispenserDispense(event: BlockDispenseEvent) {
        val state = event.block.state
        if (state !is Dispenser) return

        val mat = event.item.type
        if (mat !in dispenserBuckets) return

        val face = (state.blockData as Directional).facing

        val fromLoc = event.block.location
        val toBlock = event.block.getRelative(face)
        val toLoc   = toBlock.location

        val plotFrom = getPlotAtLocation(fromLoc.world.name, fromLoc.blockX, fromLoc.blockZ)
        val plotTo   = getPlotAtLocation(toLoc.world.name, toLoc.blockX, toLoc.blockZ)

        if (plotFrom != null || plotTo != null) {
            if (mat == Material.BUCKET) {
                val type = toBlock.type
                if (type == Material.WATER || type == Material.LAVA || type == Material.POWDER_SNOW) {
                    event.isCancelled = true
                    logger.debug("Dispenser próbuje zabrać $type pustym wiadrem z lub na działkę – anulowane")
                    return
                }
            } else {
                event.isCancelled = true
                logger.debug("Dispenser próbuje wylać $mat na lub z działki – anulowane")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val loc = event.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        val flags = plugin.cacheManager.getFlags(plot.id) ?: return

        val entityType = event.entityType

        if (entityType in aggressiveMobs) {
            if (flags["spawn-monsters"] == false) {
                event.isCancelled = true
                plugin.logger.debug("Spawn potwora $entityType zablokowany na działce ${plot.name} (${plot.id})")
            }
        } else if (entityType in passiveMobs) {
            if (flags["spawn-animals"] == false) {
                event.isCancelled = true
                plugin.logger.debug("Spawn zwierzęcia $entityType zablokowany na działce ${plot.name} (${plot.id})")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayaerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val player = event.player
        val block = event.clickedBlock ?: return
        val loc = block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        // --- 1) Kontenery ---
        when (block.type) {
            in containers -> {
                if (!hasPlotPermission(player, plot, "chest")) {
                    event.isCancelled = true
                    player.sendMessage(message.getMessage("flags", "chest.not_allowed"))
                }
                return
            }
            in buttonsAndLevers -> {
                if (!hasPlotPermission(player, plot, "button")) {
                    event.isCancelled = true
                    player.sendMessage(message.getMessage("flags", "button.not_allowed"))
                }
                return
            }
            in enderChest -> {
                if (!hasPlotPermission(player, plot, "ender-chest")) {
                    event.isCancelled = true
                    player.sendMessage(message.getMessage("flags", "ender-chest.not_allowed"))
                }
                return
            }else -> {
                // Do nothing
            }
        }

        // --- 2) Drzwi / bramy / trapdoory ---
        if (block.type in doorsAndGates) {
            // A) SMART-DOOR dla żelaznych drzwi + automatyczne przełączenie pary
            if (block.type == Material.IRON_DOOR && hasPlotPermission(player, plot, "smart-door")) {
                if (!toggling.add(block)) return
                plugin.server.scheduler.runTaskLater(plugin, Runnable { toggling.remove(block) }, 20L)

                toggleOpenState(block)
                for (face in arrayOf(
                    org.bukkit.block.BlockFace.NORTH,
                    org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST
                )) {
                    val neighbor = block.getRelative(face)
                    if (neighbor.type == block.type) {
                        toggleOpenState(neighbor)
                        break
                    }
                }

                event.isCancelled = true
                return
            }

            // B) Zwykłe drzwi/trapdoory/fence_gate
            if (!hasPlotPermission(player, plot, "door")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags", "door.not_allowed"))
            }
            // jeżeli ma flagę "door", to pozwalamy na domyślną obsługę
            return
        }
    }

    private fun toggleOpenState(block: Block) {
        val openable = block.blockData as? Openable ?: return
        openable.isOpen = !openable.isOpen
        block.blockData = openable
    }

}
