package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Openable
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketEntityEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.inventory.EquipmentSlot
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
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
    private val damageableByFlow: Set<Material> by lazy { PlotCompat.loadDamageableByFlow() }
    private val utilityBlocks: Set<Material> by lazy { PlotCompat.loadUtilityBlocks() }
    private val redstoneNames: Set<Material> by lazy { PlotCompat.loadRedstoneBlocks() }
    private val containerEntities: Set<EntityType> by lazy { PlotCompat.loadContainerEntities() }

    /**
     * Sprawdza, czy dany blok znajduje się w obrębie działki.
     * @param world Świat, w którym znajduje się działka.
     * @param x X-koordynata bloku.
     * @param z Z-koordynata bloku.
     * @return Obiekt PlotData, jeśli blok znajduje się w obrębie działki, lub null, jeśli nie.
     */
    private fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        return plugin.cacheManager.getCachedPlots().firstOrNull { plot ->
            plot.world.equals(world, ignoreCase = true) &&
                    x in (plot.x - plot.radius..plot.x + plot.radius) &&
                    z in (plot.z - plot.radius..plot.z + plot.radius)
        }
    }

    /**
     * Przywraca blok do jego oryginalnego stanu.
     * Zapobiega powstawaniu bloków ducha
     * @param block Blok, który ma zostać przywrócony.
     */
    private fun cancelAndRestore(block: Block) {
        block.state.update(true, false)
        logger.debug("[cancelAndRestore] Restoring block at ${block.location.blockX},${block.location.blockZ} to its original state.")
    }

    /**
     * Sprawdza, czy gracz ma odpowiednie uprawnienia do działania na działce.
     * Sprawdza po uprawnieniach, czy jest członkiem lub właścicielem działki oraz flagi.
     *
     * @param player Gracz, którego uprawnienia mają zostać sprawdzone.
     * @param plot Działka, na której gracz chce działać.
     * @param flag Flaga, która ma zostać sprawdzona.
     * @return true, jeśli gracz ma odpowiednie uprawnienia, false w przeciwnym razie.
     */
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
            FlagType.WHITELIST -> value
            FlagType.BLACKLIST -> !value
        }
    }

    /**
     * Sprawdza, jak flaga na działce jest/powinna być ustawiona.
     * @param plotId ID działki.
     * @param flag Flaga, która ma zostać sprawdzona.
     * @return true, jeśli flaga jest dozwolona, false w przeciwnym razie.
     */
    private fun isFlagAllowed(plotId: Int, flag: String): Boolean {
        val def = PlotFlagRegistry.allFlags[flag] ?: return false
        val flags = plugin.cacheManager.getFlags(plotId) ?: emptyMap()
        val value = flags[flag] ?: def.defaultValue
        return when (def.type) {
            FlagType.WHITELIST -> value
            FlagType.BLACKLIST -> !value
        }
    }


    /**
     * Funkcja debugująca
     * Wypisuje wszystkie flagi i ich domyślne wartości.
     */
    @Suppress("unused")
    private fun printAllFlagBehaviors() {
        PlotFlagRegistry.allFlags.forEach { (key, def) ->
            println("Flaga $key: default=${def.defaultValue}, typ=${def.type}, efektDlaObcego=${when (def.type) {
                FlagType.WHITELIST -> def.defaultValue
                FlagType.BLACKLIST -> !def.defaultValue
            }}")
        }
    }

    private fun hasBypass(player: Player): Boolean {
        return PermissionChecker.canBypassPlots(player)
    }

    /**
     * Zdarzenie wywoływane, gdy gracz wchodzi na działkę.
     * Sprawdza, czy gracz zmienił działkę i wysyła odpowiednie wiadomości.
     *
     * @param event Zdarzenie ruchu gracza.
     */
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

    /**
     * Zdarzenie wywoływane, gdy gracz umieszcza blok.
     * Sprawdza, czy gracz ma odpowiednie uprawnienia do umieszczania bloków na działce.
     *
     * @param event Zdarzenie umieszczania bloku.
     */
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

    /**
     * Zdarzenie wywoływane, gdy gracz niszczy blok.
     * Sprawdza, czy gracz ma odpowiednie uprawnienia do niszczenia bloków na działce.
     *
     * @param event Zdarzenie niszczenia bloku.
     */
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

    /**
     * Zdarzenie wywoływane, gdy blok zmienia się w inny blok (np. przez grawitację). TODO: Przetestować ponownie!!!
     * Sprawdza, czy gracz ma odpowiednie uprawnienia do zmiany bloków na działce.
     *
     * @param event Zdarzenie zmiany bloku.
     */
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

    /**
     * Spawnowanie potworów i zwierząt na działkach
     *
     * @param event
     */
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
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val block = event.clickedBlock ?: return
        val plot = getPlotAtLocation(block.world.name, block.x, block.z) ?: return

        val mat = block.type

        when (mat) {
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
            }
            in utilityBlocks -> {
                if (block.type in utilityBlocks) {
                    if (!hasPlotPermission(player, plot, "utility")) {
                        event.isCancelled = true
                        player.sendMessage(message.getMessage("flags", "utility.not_allowed"))
                    }
                    return
                }
            }
            in redstoneNames -> {
                if (block.type in redstoneNames) {
                    if (!hasPlotPermission(player, plot, "redstone")) {
                        event.isCancelled = true
                        player.sendMessage(message.getMessage("flags", "redstone.not_allowed"))
                    }
                    return
                }
            }
            else -> {
                // nic nie rób xD
            }
        }

        if (mat in doorsAndGates) {
            val data = block.blockData
            if (data is Openable && hasPlotPermission(player, plot, "smart-door")) {
                if (!toggling.add(block)) return
                plugin.server.scheduler.runTaskLater(plugin, Runnable { toggling.remove(block) }, 20L)

                toggleOpenState(block)
                for (face in listOf(
                    BlockFace.NORTH, BlockFace.SOUTH,
                    BlockFace.EAST,  BlockFace.WEST
                )) {
                    val neighbour = block.getRelative(face)
                    if (neighbour.type == mat) {
                        toggleOpenState(neighbour)
                        break
                    }
                }

                event.isCancelled = true
                return
            }

            if (!hasPlotPermission(player, plot, "door")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags", "door.not_allowed"))
            }
            return
        }
    }

    private fun toggleOpenState(block: Block) {
        val openable = block.blockData as? Openable ?: return
        openable.isOpen = !openable.isOpen
        block.blockData = openable
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        val player = event.player
        val plot = getPlotAtLocation(
            event.block.world.name, event.block.x, event.block.z
        ) ?: return

        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        val player = event.player
        val plot = getPlotAtLocation(
            event.block.world.name, event.block.x, event.block.z
        ) ?: return

        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEntity(event: PlayerBucketEntityEvent) {
        val player = event.player
        val loc = event.entity.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onLiquidFlow(event: BlockFromToEvent) {
        val plot = getPlotAtLocation(
            event.block.world.name, event.block.x, event.block.z
        ) ?: getPlotAtLocation(
            event.toBlock.world.name, event.toBlock.x, event.toBlock.z
        ) ?: return

        if (!isFlagAllowed(plot.id, "flow")) {
            event.isCancelled = true
            return
        }

        if (!isFlagAllowed(plot.id, "flow-damage") && event.toBlock.type in damageableByFlow) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockPhysics(event: BlockPhysicsEvent) {
        if (event.changedType != Material.WATER && event.changedType != Material.LAVA) return
        val block = event.block
        if (block.type !in damageableByFlow) return

        val plot = getPlotAtLocation(block.world.name, block.x, block.z) ?: return
        if (!isFlagAllowed(plot.id, "flow-damage")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityDamageByPlayer(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity
        if (victim.type !in passiveMobs) return

        val plot = getPlotAtLocation(
            victim.world.name,
            victim.location.blockX,
            victim.location.blockZ
        ) ?: return
        if (plot.ownerUuid == attacker.uniqueId) return
        if (!hasPlotPermission(attacker, plot, "passives")) {
            event.isCancelled = true
            attacker.sendMessage(message.getMessage("flags", "passives.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerUseFlintAndSteel(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val item = event.player.inventory.itemInMainHand.type
        if (item != Material.FLINT_AND_STEEL) return

        val block = event.clickedBlock ?: return
        val plot = getPlotAtLocation(block.world.name, block.x, block.z) ?: return

        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
            event.player.sendMessage(message.getMessage("flags", "fire.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockIgnite(event: BlockIgniteEvent) {
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockBurn(event: BlockBurnEvent) {
        val plot = getPlotAtLocation(
            event.block.world.name, event.block.x, event.block.z
        ) ?: return

        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockFade(event: BlockFadeEvent) {
        val plot = getPlotAtLocation(
            event.block.world.name, event.block.x, event.block.z
        ) ?: return

        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityDamageByBlock(event: EntityDamageByBlockEvent) {
        val cause = event.cause
        if (cause != EntityDamageEvent.DamageCause.FIRE && cause != EntityDamageEvent.DamageCause.FIRE_TICK && cause != EntityDamageEvent.DamageCause.LAVA) return

        val plot = getPlotAtLocation(
            event.entity.world.name,
            event.entity.location.blockX,
            event.entity.location.blockZ
        ) ?: return

        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val ent    = event.rightClicked
        val plot = getPlotAtLocation(ent.world.name, ent.location.blockX, ent.location.blockZ)
            ?: return

        // 1) Pasywne moby (karmienie, name-tag, wsiadanie etc.)
        if (ent is LivingEntity && ent.type in passiveMobs) {
            if (!hasPlotPermission(player, plot, "passives")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags","passives.not_allowed"))
            }
            return
        }

        // 2) Kontenerowe encje (chest‐minecart, chest‐boat, hopper‐minecart, boarding)
        if (ent.type in containerEntities) {
            if (!hasPlotPermission(player, plot, "minecart")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags","minecart.not_allowed"))
            }
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val veh    = event.entity
        if (veh.type in containerEntities) {
            val plot = getPlotAtLocation(veh.world.name, veh.location.blockX, veh.location.blockZ)
                ?: return
            if (!hasPlotPermission(player, plot, "minecart")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags","minecart.not_allowed"))
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onVehicleEnter(event: VehicleEnterEvent) {
        val player  = event.entered as? Player ?: return
        val vehicle = event.vehicle
        if (vehicle.type in containerEntities) {
            val plot = getPlotAtLocation(vehicle.world.name, vehicle.location.blockX, vehicle.location.blockZ)
                ?: return
            if (!hasPlotPermission(player, plot, "minecart")) {
                event.isCancelled = true
                player.sendMessage(message.getMessage("flags","minecart.not_allowed"))
            }
        }
    }

    /**
     * Blokada komend /sethome i /home na cudzej działce, jeśli allow-home = false.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val msg = event.message.lowercase()
        if (!msg.startsWith("/home") && !msg.startsWith("/sethome")) return

        val player = event.player
        val loc = player.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        // jeśli nie ma prawa (bypass/owner/member) i flaga allow-home = false → cancel
        if (!hasPlotPermission(player, plot, "allow-home")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "allow-home.not_allowed"))
        }
    }

    /**
     * Blokada picia mikstur (regularnych, splash, lingering), jeśli use-potions = false.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        val item = event.item.type
        if (item != Material.POTION && item != Material.SPLASH_POTION && item != Material.LINGERING_POTION) return

        val loc = player.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(player, plot, "use-potions")) {
            event.isCancelled = true
            player.sendMessage(message.getMessage("flags", "use-potions.not_allowed"))
        }
    }

    /**
     * Blokada rzucania splash/lingering mikstur, jeśli use-potions = false.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPotionSplash(event: PotionSplashEvent) {
        val shooter = event.entity.shooter as? Player ?: return

        // lokalizacja GRACZA-rzucającego decyduje o pozwoleniu
        val loc = shooter.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(shooter, plot, "use-potions")) {
            event.isCancelled = true
            shooter.sendMessage(message.getMessage("flags", "use-potions.not_allowed"))
        }
    }

    // TODO: DODAĆ METODE OBSŁUGI MIKSTUR TRWAŁYCH!
}
