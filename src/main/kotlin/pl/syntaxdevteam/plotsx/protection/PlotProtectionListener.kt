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
import pl.syntaxdevteam.plotsx.databases.PlotData
import java.util.UUID

class PlotProtectionListener(private val plugin: PlotsX) : Listener {

    private val logger = plugin.logger
    private val message = plugin.messageHandler
    private val playerLastPlot = mutableMapOf<UUID, Int?>()
    private val toggling: MutableSet<Block> = mutableSetOf()
    private val aggressiveMobs = setOf(
        EntityType.BLAZE,
        EntityType.CAVE_SPIDER,
        EntityType.CREAKING,
        EntityType.CREEPER,
        EntityType.DROWNED,
        EntityType.ELDER_GUARDIAN,
        EntityType.ENDERMAN,
        EntityType.ENDERMITE,
        EntityType.EVOKER,
        EntityType.GHAST,
        EntityType.GIANT,
        EntityType.GUARDIAN,
        EntityType.HUSK,
        EntityType.ILLUSIONER,
        EntityType.MAGMA_CUBE,
        EntityType.PHANTOM,
        EntityType.PIGLIN,
        EntityType.PIGLIN_BRUTE,
        EntityType.PILLAGER,
        EntityType.RAVAGER,
        EntityType.SHULKER,
        EntityType.SILVERFISH,
        EntityType.SKELETON,
        EntityType.SLIME,
        EntityType.SPIDER,
        EntityType.STRAY,
        EntityType.VEX,
        EntityType.VINDICATOR,
        EntityType.WITCH,
        EntityType.WITHER,
        EntityType.WITHER_SKELETON,
        EntityType.WARDEN,
        EntityType.ZOGLIN,
        EntityType.ZOMBIFIED_PIGLIN,
        EntityType.ZOMBIE,
        EntityType.ZOMBIE_VILLAGER
    )

    private val passiveMobs = setOf(
        EntityType.ALLAY,
        EntityType.ARMADILLO,
        EntityType.AXOLOTL,
        EntityType.BAT,
        EntityType.BEE,
        EntityType.CAT,
        EntityType.CHICKEN,
        EntityType.COD,
        EntityType.COW,
        EntityType.DOLPHIN,
        EntityType.DONKEY,
        EntityType.FOX,
        EntityType.FROG,
        EntityType.GOAT,
        EntityType.HORSE,
        EntityType.MOOSHROOM,
        EntityType.MULE,
        EntityType.OCELOT,
        EntityType.PANDA,
        EntityType.PARROT,
        EntityType.PIG,
        EntityType.PUFFERFISH,
        EntityType.RABBIT,
        EntityType.SALMON,
        EntityType.SHEEP,
        EntityType.SNIFFER,
        EntityType.SNOW_GOLEM,
        EntityType.SQUID,
        EntityType.STRIDER,
        EntityType.TADPOLE,
        EntityType.TROPICAL_FISH,
        EntityType.TURTLE,
        EntityType.VILLAGER,
        EntityType.WANDERING_TRADER
    )


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
        if (plugin.uuidManager.getUUID(player.name) == plot.ownerUuid) return true

        val isMember = plugin.cacheManager.getMembers(plot.id)
            ?.any { it.memberUuid == player.uniqueId.toString() } ?: false
        if (!isMember) return false

        val flags = plugin.cacheManager.getFlags(plot.id) ?: return false
        return flags[flag] == true
    }

    private fun hasBypass(player: Player): Boolean {
        return player.isOp || player.hasPermission("plotsx.plot.bypass")
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
        val isBucket = mat in listOf(
            Material.WATER_BUCKET,
            Material.LAVA_BUCKET,
            Material.POWDER_SNOW_BUCKET,
            Material.BUCKET,
            Material.EGG,
            Material.BLUE_EGG,
            Material.BROWN_EGG,
            Material.SNIFFER_EGG,
            Material.TURTLE_EGG
        )
        if (!isBucket) return
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

        // --- Definicje grup bloków ---
        val containers = listOf(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SHULKER_BOX
        ) + Material.entries.filter { it.name.endsWith("_SHULKER_BOX") }

        val doorsAndGates = listOf(
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
            Material.BAMBOO_DOOR, Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.PALE_OAK_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR, Material.PALE_OAK_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE, Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE, Material.PALE_OAK_FENCE_GATE
        )

        val buttonsAndLevers = listOf(
            Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON, Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON, Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON, Material.PALE_OAK_BUTTON
        )

        val enderChest = listOf(
            Material.ENDER_CHEST
        )

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
