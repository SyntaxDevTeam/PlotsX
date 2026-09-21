package pl.syntaxdevteam.plotsx.protection

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Dispenser
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Openable
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.ThrownPotion
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.block.BlockFadeEvent
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.block.EntityBlockFormEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.PlayerLeashEntityEvent
import org.bukkit.event.entity.LingeringPotionSplashEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketEntityEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.event.player.PlayerUnleashEntityEvent
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.world.PortalCreateEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.compat.PlotCompat
import pl.syntaxdevteam.plotsx.databases.PlotData
import pl.syntaxdevteam.plotsx.permissions.PermissionChecker
import java.util.UUID

class PlotProtectionListener(private val plugin: PlotsX) : Listener {

    private val logger = plugin.logger
    private val message = plugin.messageHandler
    private val playerLastPlot = mutableMapOf<UUID, Int?>()
    private val approachingPlotWarnings = mutableMapOf<UUID, Int?>()
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
    private val spawnerBlocks: Set<Material> by lazy { PlotCompat.loadContainerSpawner() }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (PermissionChecker.isAuthor(event.player.uniqueId)) {
            event.player.sendMessage(
                plugin.messageHandler
                    .formatMixedTextToMiniMessage(plugin.messageHandler.getPrefix() + " <green>Witaj, <b>WieszczY!</b> Ten serwer używa Twojego pluginu! :)",
                        TagResolver.empty())
            )
        }
    }

    /**
     * Sprawdza, czy dany blok znajduje się w obrębie działki.
     * @param world Świat, w którym znajduje się działka.
     * @param x X-koordynata bloku.
     * @param z Z-koordynata bloku.
     * @return Obiekt PlotData, jeśli blok znajduje się w obrębie działki, lub null, jeśli nie.
     */
    private fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        return plugin.cacheManager.getPlotAt(world, x, z)
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

        val flagMeta = PlotFlagRegistry.allFlags[flag]
            ?: run {
                plugin.logger.debug("[hasPlotPermission] Nieznana flaga '$flag' na działce ${plot.id} (${plot.name})")
                return false
            }

        val flags = plugin.cacheManager.getFlags(plot.id) ?: emptyList()
        val value = flags.firstOrNull { it.name == flag }?.value?.toBooleanStrictOrNull() ?: flagMeta.defaultValue

        plugin.logger.debug(
            "[hasPlotPermission] Flaga '$flag' = $value (domyślnie=${flagMeta.defaultValue}, typ=${flagMeta.type}) " +
                    "dla gracza ${player.name} na działce ${plot.id} (${plot.name})"
        )

        return when (flagMeta.type) {
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
        val flagMeta = PlotFlagRegistry.allFlags[flag] ?: return false
        val flags = plugin.cacheManager.getFlags(plotId) ?: emptyList()
        val value = flags.firstOrNull { it.name == flag }?.value?.toBooleanStrictOrNull() ?: flagMeta.defaultValue
        return when (flagMeta.type) {
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

    private fun canUsePrivateChest(player: Player, plot: PlotData, chest: PrivateChestManager.Protection): Boolean =
        hasBypass(player) || player.uniqueId == chest.owner ||
            (chest.canAccess(player.uniqueId) && isPlotParticipant(player, plot))

    private fun deniedPrivateInventory(player: Player, inventory: org.bukkit.inventory.Inventory): Boolean {
        val block = inventory.location?.block ?: return false
        val plot = plotAt(block) ?: return false
        val chest = plugin.privateChestManager.getProtection(block, plot.id) ?: return false
        return !canUsePrivateChest(player, plot, chest)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrivateInventoryOpen(event: org.bukkit.event.inventory.InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (deniedPrivateInventory(player, event.inventory)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrivateInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (deniedPrivateInventory(player, event.view.topInventory)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrivateInventoryDrag(event: org.bukkit.event.inventory.InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (deniedPrivateInventory(player, event.view.topInventory)) event.isCancelled = true
    }

    private fun isPlotParticipant(player: Player, plot: PlotData): Boolean =
        hasBypass(player) || player.uniqueId == plot.ownerUuid ||
            plugin.cacheManager.getMembers(plot.id).orEmpty().any {
                it.memberUuid == player.uniqueId.toString()
            }

    private fun plotAt(block: Block): PlotData? =
        getPlotAtLocation(block.world.name, block.x, block.z)

    private fun plotAt(entity: org.bukkit.entity.Entity): PlotData? {
        val location = entity.location
        return getPlotAtLocation(location.world.name, location.blockX, location.blockZ)
    }

    private fun projectilePlayer(projectile: Projectile): Player? = projectile.shooter as? Player

    private fun notifyPlotBoundary(player: Player, component: Component) {
        when (PlotNotificationMode.fromConfig(plugin.config.getString("plots.notifications.boundary"))) {
            PlotNotificationMode.ACTIONBAR -> player.sendActionBar(component)
            PlotNotificationMode.CHAT -> player.sendMessage(component)
        }
    }

    /**
     * Zdarzenie wywoływane, gdy gracz wchodzi na działkę.
     * Sprawdza, czy gracz zmienił działkę i wysyła odpowiednie wiadomości.
     *
     * @param event Zdarzenie ruchu gracza.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) { // TODO: dodać pole widzenia działki na 2 pola przed wejściem na działkę pod warunkiem że działka nie należy do gracza
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
                    notifyPlotBoundary(player,
                        message.stringMessageToComponent("plots", "leave_plot", mapOf("plot" to oldPlot.name)))
                    }
            }

            if (newPlot != null) {
                notifyPlotBoundary(player,
                    message.stringMessageToComponent("plots", "enter_plot", mapOf("plot" to newPlot.name)))
            }
        }

        val stepX = (event.to.blockX - event.from.blockX).coerceIn(-1, 1)
        val stepZ = (event.to.blockZ - event.from.blockZ).coerceIn(-1, 1)
        if (stepX != 0 || stepZ != 0) {
            val approachingPlot = findApproachingPlot(event, stepX, stepZ)

            if (approachingPlot != null && approachingPlot.ownerUuid != uuid && approachingPlot.id != newPlotId) {
                if (approachingPlotWarnings[uuid] != approachingPlot.id) {
                    approachingPlotWarnings[uuid] = approachingPlot.id
                    notifyPlotBoundary(player,
                        message.stringMessageToComponent("plots", "approaching_plot", mapOf("plot" to approachingPlot.name))
                    )
                }
            } else {
                approachingPlotWarnings.remove(uuid)
            }
        }
        if (newPlot != null && !isFlagAllowed(newPlot.id, "effects")) {

            player.activePotionEffects
                .map { it.type }
                .forEach { player.removePotionEffect(it) }
        }
    }

    private fun findApproachingPlot(event: PlayerMoveEvent, stepX: Int, stepZ: Int): PlotData? {
        val to = event.to
        val worldName = to.world.name
        for (distance in 1..2) {
            val plot = getPlotAtLocation(
                worldName,
                to.blockX + stepX * distance,
                to.blockZ + stepZ * distance
            )
            if (plot != null) return plot
        }
        return null
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
        val block = event.blockPlaced
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)
        val mat = event.block.type

        if (mat in spawnerBlocks) {
            logger.debug("Block należy do spawnerBlocks")
            if (plot != null && !hasPlotPermission(player, plot, "allow-spawners")) {
                event.isCancelled = true
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    cancelAndRestore(event.block)
                }, 1L)
                player.sendMessage(message.stringMessageToComponent("flags", "allow-spawners.not_allowed"))
            }else{
                plugin.coreProtectHook.logBlockPlace(player, block)
                return
            }
        }

        if (plot != null && !hasPlotPermission(player, plot, "build")) {
            logger.debug("BlockPlaceEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage(message.stringMessageToComponent("flags", "build.not_allowed"))
        }

        if (!event.isCancelled && plot != null && isPlotParticipant(player, plot) &&
            plugin.config.getBoolean("privateChests.protectOnPlace", true) &&
            plugin.privateChestManager.isSupported(block)
        ) {
            val existingProtection = plugin.privateChestManager.getProtection(block, plot.id)
            if (existingProtection != null && existingProtection.owner != player.uniqueId && !hasBypass(player)) {
                event.isCancelled = true
                plugin.server.scheduler.runTaskLater(plugin, Runnable { cancelAndRestore(block) }, 1L)
                player.sendMessage(message.stringMessageToComponent("private_chest", "join_denied"))
                return
            }
            if (existingProtection == null) {
                plugin.privateChestManager.lock(block, plot.id, player.uniqueId)
                player.sendMessage(message.stringMessageToComponent("private_chest", "locked_on_place"))
            } else {
                // Copy metadata to the newly placed half of a double chest as well.
                plugin.privateChestManager.synchronize(block, existingProtection)
            }
        }/*else{ TODO: Po testach całkiem usunąć ten else
            plugin.coreProtectHook.logBlockPlace(player, block)
            logger.debug("Flaga nie zablokowana. Brak działki lub odpowiednie uprawnienia.")
        }*/
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val block = event.block
        val loc    = event.block.location
        val plot   = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)
        val mat    = event.block.type

        if (plot != null) {
            val privateChest = plugin.privateChestManager.getProtection(block, plot.id)
            if (privateChest != null && !privateChest.canAccess(player.uniqueId) && !hasBypass(player)) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("private_chest", "access_denied"))
                return
            }
            if (privateChest != null && player.uniqueId != privateChest.owner && !hasBypass(player)) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("private_chest", "break_denied"))
                return
            }
        }

        if (mat in spawnerBlocks) {
            logger.debug("Block należy do spawnerBlocks")
            if (plot != null && !hasPlotPermission(player, plot, "allow-spawners")) {
                event.isCancelled = true
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    cancelAndRestore(event.block)
                }, 1L)
                player.sendMessage(message.stringMessageToComponent("flags", "allow-spawners.not_allowed"))
            }else{
                plugin.coreProtectHook.logBlockBreak(player, block)
                return
            }
        }

        if (plot != null && !hasPlotPermission(player, plot, "build")) {
            logger.debug("BlockBreakEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage(message.stringMessageToComponent("flags", "build.break_not_allowed"))
        }else{
            plugin.coreProtectHook.logBlockBreak(player, block)
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onInventoryClick(e: InventoryClickEvent) {
        val who = e.whoClicked
        if (who !is Player) return
        val loc = (e.clickedInventory?.location ?: return)
        @Suppress("unused", "UnusedVariable") val plot   = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        plugin.coreProtectHook.logContainerTransaction(who, loc.block)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        val sourceLocation = event.source.location ?: return
        val destinationLocation = event.destination.location ?: return
        val sourcePlot = getPlotAtLocation(sourceLocation.world.name, sourceLocation.blockX, sourceLocation.blockZ)
        val destinationPlot = getPlotAtLocation(destinationLocation.world.name, destinationLocation.blockX, destinationLocation.blockZ)

        val privateSource = sourcePlot?.let {
            plugin.privateChestManager.getProtection(sourceLocation.block, it.id)
        }
        val privateDestination = destinationPlot?.let {
            plugin.privateChestManager.getProtection(destinationLocation.block, it.id)
        }
        if (privateSource != null || privateDestination != null) {
            event.isCancelled = true
            return
        }

        if (sourcePlot?.id == destinationPlot?.id) return
        if ((sourcePlot != null && !isFlagAllowed(sourcePlot.id, "item-transfer")) ||
            (destinationPlot != null && !isFlagAllowed(destinationPlot.id, "item-transfer"))
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf { block ->
            plotAt(block)?.let {
                plugin.privateChestManager.getProtection(block, it.id) != null || !isFlagAllowed(it.id, "explosions")
            } ?: false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf { block ->
            plotAt(block)?.let {
                plugin.privateChestManager.getProtection(block, it.id) != null || !isFlagAllowed(it.id, "explosions")
            } ?: false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onExplosionDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION &&
            event.cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
        ) return
        val plot = plotAt(event.entity) ?: return
        if (!isFlagAllowed(plot.id, "explosions")) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val player = event.player ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "decorations")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "decorations.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingBreakByEntity(event: HangingBreakByEntityEvent) {
        val player = when (val remover = event.remover) {
            is Player -> remover
            is Projectile -> projectilePlayer(remover)
            else -> null
        } ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "decorations")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "decorations.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        if (event is HangingBreakByEntityEvent) return
        val plot = plotAt(event.entity) ?: return
        if (!isFlagAllowed(plot.id, "decorations")) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        val plot = plotAt(event.rightClicked) ?: return
        if (!hasPlotPermission(event.player, plot, "decorations")) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "decorations.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArmorStandDamage(event: EntityDamageByEntityEvent) {
        if (event.entity !is ArmorStand) return
        val player = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> projectilePlayer(damager)
            else -> null
        } ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "decorations")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "decorations.not_allowed"))
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
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        logger.debug("EntityChangeBlockEvent at ${loc.blockX},${loc.blockZ} => plot=${plot.id}")

        if (event.entityType == EntityType.FALLING_BLOCK && !isFlagAllowed(plot.id, "fall")) {
            event.isCancelled = true
            event.block.state.update(true, false)
            return
        }

        if (!isFlagAllowed(plot.id, "block-transform")) {
            event.isCancelled = true
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

        val entityType = event.entityType

        if (entityType in aggressiveMobs) {
            if (!isFlagAllowed(plot.id, "spawn-monsters")) {
                event.isCancelled = true
                plugin.logger.debug("Spawn potwora $entityType zablokowany na działce ${plot.name} (${plot.id})")
            }
        } else if (entityType in passiveMobs) {
            if (!isFlagAllowed(plot.id, "spawn-animals")) {
                event.isCancelled = true
                plugin.logger.debug("Spawn zwierzęcia $entityType zablokowany na działce ${plot.name} (${plot.id})")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.PHYSICAL && event.clickedBlock?.type == Material.FARMLAND) {
            val player = event.player
            val plot = event.clickedBlock?.let(::plotAt) ?: return
            if (!hasPlotPermission(player, plot, "crop-trample")) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags", "crop-trample.not_allowed"))
            }
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return

        val player = event.player
        val block = event.clickedBlock ?: return
        val plot = getPlotAtLocation(block.world.name, block.x, block.z) ?: return

        val mat = block.type

        val interactionFlag = when {
            mat.name.endsWith("_BED") -> "bed-use"
            mat == Material.CRAFTING_TABLE || mat.name == "CRAFTER" -> "crafting"
            mat == Material.ENCHANTING_TABLE -> "enchanting"
            mat == Material.RESPAWN_ANCHOR -> "respawn-anchor"
            else -> null
        }
        if (interactionFlag != null) {
            if (!hasPlotPermission(player, plot, interactionFlag)) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags", "$interactionFlag.not_allowed"))
            }
            return
        }

        if (mat == Material.CHISELED_BOOKSHELF) {
            if (!hasPlotPermission(player, plot, "utility")) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags", "utility.not_allowed"))
            }
            return
        }

        when (mat) {
            in containers -> {
                val privateChest = plugin.privateChestManager.getProtection(block, plot.id)
                if (privateChest != null && !canUsePrivateChest(player, plot, privateChest)) {
                    event.isCancelled = true
                    player.sendMessage(message.stringMessageToComponent("private_chest", "access_denied"))
                    return
                }
                if (!hasPlotPermission(player, plot, "chest")) {
                    event.isCancelled = true
                    player.sendMessage(message.stringMessageToComponent("flags", "chest.not_allowed"))
                }
                return
            }
            in buttonsAndLevers -> {
                val flag = if (mat == Material.LEVER) "lever" else "button"
                if (!hasPlotPermission(player, plot, flag)) {
                    event.isCancelled = true
                    player.sendMessage(message.stringMessageToComponent("flags", "$flag.not_allowed"))
                }
                return
            }
            in enderChest -> {
                if (!hasPlotPermission(player, plot, "ender-chest")) {
                    event.isCancelled = true
                    player.sendMessage(message.stringMessageToComponent("flags", "ender-chest.not_allowed"))
                }
                return
            }
            in utilityBlocks -> {
                if (block.type in utilityBlocks) {
                    if (!hasPlotPermission(player, plot, "utility")) {
                        event.isCancelled = true
                        player.sendMessage(message.stringMessageToComponent("flags", "utility.not_allowed"))
                    }
                    return
                }
            }
            in redstoneNames -> {
                if (block.type in redstoneNames) {
                    if (!hasPlotPermission(player, plot, "redstone")) {
                        event.isCancelled = true
                        player.sendMessage(message.stringMessageToComponent("flags", "redstone.not_allowed"))
                    }
                    return
                }
            }
            in damageableByFlow -> {
                if (!isFlagAllowed(plot.id, "cant-grow")) {
                    event.isCancelled = true
                    event.player.sendMessage(message.stringMessageToComponent("flags", "cant-grow.not_allowed"))
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
                player.sendMessage(message.stringMessageToComponent("flags", "door.not_allowed"))
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
            player.sendMessage(message.stringMessageToComponent("flags", "build.not_allowed"))
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
            player.sendMessage(message.stringMessageToComponent("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBucketEntity(event: PlayerBucketEntityEvent) {
        val player = event.player
        val loc = event.entity.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(player, plot, "build")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "build.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onLiquidFlow(event: BlockFromToEvent) {
        val plots = listOfNotNull(
            getPlotAtLocation(event.block.world.name, event.block.x, event.block.z),
            getPlotAtLocation(event.toBlock.world.name, event.toBlock.x, event.toBlock.z)
        ).distinctBy { it.id }
        if (plots.any { !isFlagAllowed(it.id, "flow") ||
                (!isFlagAllowed(it.id, "flow-damage") && event.toBlock.type in damageableByFlow) }) {
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
        val attacker = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return
        val victim = event.entity

        if (victim is Player) {
            val plot = getPlotAtLocation(
                victim.world.name,
                victim.location.blockX,
                victim.location.blockZ
            ) ?: return

            if (!hasPlotPermission(attacker, plot, "pvp")) {
                event.isCancelled = true
                attacker.sendMessage(message.stringMessageToComponent("flags", "pvp.not_allowed"))
            }
            return
        }

        if (victim.type !in passiveMobs) return

        val plot = getPlotAtLocation(
            victim.world.name,
            victim.location.blockX,
            victim.location.blockZ
        ) ?: return
        if (!hasPlotPermission(attacker, plot, "passives")) {
            event.isCancelled = true
            attacker.sendMessage(message.stringMessageToComponent("flags", "passives.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpecialWeaponDamage(event: EntityDamageByEntityEvent) {
        val attacker = when (val damager = event.damager) {
            is Player -> damager.takeIf { it.inventory.itemInMainHand.type.name == "MACE" }
            is Projectile -> (damager.shooter as? Player).takeIf { damager.type.name == "TRIDENT" }
            else -> null
        } ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(attacker, plot, "special-weapons")) {
            event.isCancelled = true
            attacker.sendMessage(message.stringMessageToComponent("flags", "special-weapons.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpecialProjectileLaunch(event: ProjectileLaunchEvent) {
        if (event.entity.type.name != "TRIDENT") return
        val player = projectilePlayer(event.entity) ?: return
        val plot = plotAt(player) ?: return
        if (!hasPlotPermission(player, plot, "special-weapons")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "special-weapons.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleGlide(event: EntityToggleGlideEvent) {
        if (!event.isGliding) return
        val player = event.entity as? Player ?: return
        val plot = plotAt(player) ?: return
        if (!hasPlotPermission(player, plot, "elytra")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "elytra.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onToggleFlight(event: PlayerToggleFlightEvent) {
        if (!event.isFlying) return
        val plot = plotAt(event.player) ?: return
        if (!hasPlotPermission(event.player, plot, "flight")) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "flight.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerFish(event: PlayerFishEvent) {
        val locations = listOfNotNull(event.caught?.location, event.hook.location, event.player.location)
        val deniedPlot = locations.asSequence()
            .mapNotNull { getPlotAtLocation(it.world.name, it.blockX, it.blockZ) }
            .distinctBy(PlotData::id)
            .firstOrNull { !hasPlotPermission(event.player, it, "fishing") }
        if (deniedPlot != null) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "fishing.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLightningStrike(event: LightningStrikeEvent) {
        val plot = plotAt(event.lightning) ?: return
        if (!isFlagAllowed(plot.id, "weather")) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onWeatherDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.LIGHTNING) return
        val plot = plotAt(event.entity) ?: return
        if (!isFlagAllowed(plot.id, "weather")) event.isCancelled = true
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
            event.player.sendMessage(message.stringMessageToComponent("flags", "fire.not_allowed"))
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
        val block = event.block
        val loc = block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        val type = block.type

        if (type == Material.ICE || type == Material.SNOW) {
            if (!isFlagAllowed(plot.id, "iceform-world")) {
                event.isCancelled = true
                //logger.debug("Tworzenie lodu/śniegu zablokowane na działce ${plot.name} (${plot.id})")
            }
        }
/*
        if (!isFlagAllowed(plot.id, "fire")) {
            event.isCancelled = true
        }*/
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

        if (ent is LivingEntity && ent.type in passiveMobs) {
            if (!hasPlotPermission(player, plot, "animal-interact")) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags","animal-interact.not_allowed"))
            }else{
                return
            }
        }

        if (ent.type in containerEntities) {
            if (!hasPlotPermission(player, plot, "minecart")) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags","minecart.not_allowed"))
            }else{
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerLeashEntity(event: PlayerLeashEntityEvent) {
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(event.player, plot, "animal-interact")) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "animal-interact.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerUnleashEntity(event: PlayerUnleashEntityEvent) {
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(event.player, plot, "animal-interact")) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "animal-interact.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityBreed(event: EntityBreedEvent) {
        val player = event.breeder as? Player ?: return
        val plot = plotAt(event.entity) ?: return
        if (!hasPlotPermission(player, plot, "animal-interact")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "animal-interact.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityMount(event: EntityMountEvent) {
        val player = event.entity as? Player ?: return
        if (event.mount.type !in passiveMobs) return
        val plot = plotAt(event.mount) ?: return
        if (!hasPlotPermission(player, plot, "animal-interact")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "animal-interact.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val plot = plotAt(event.item) ?: return
        if (!hasPlotPermission(player, plot, "item-pickup")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "item-pickup.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val plot = plotAt(event.itemDrop) ?: return
        if (!hasPlotPermission(event.player, plot, "item-drop")) {
            event.isCancelled = true
            event.player.sendMessage(message.stringMessageToComponent("flags", "item-drop.not_allowed"))
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
                player.sendMessage(message.stringMessageToComponent("flags","minecart.not_allowed"))
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
                player.sendMessage(message.stringMessageToComponent("flags","minecart.not_allowed"))
            }
        }
    }

    /** Kontrola komend na cudzej działce z bezpiecznymi wyjątkami dla PlotsX. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val loc = player.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        val command = event.message
            .removePrefix("/")
            .substringBefore(' ')
            .substringAfter(':')
            .lowercase()

        if (command == "home" || command == "sethome") {
            if (!hasPlotPermission(player, plot, "allow-home")) {
                event.isCancelled = true
                player.sendMessage(message.stringMessageToComponent("flags", "allow-home.not_allowed"))
            }
            return
        }

        val safeCommands = setOf("plotsx", "ptx", "plot", "claim", "unclaim")
        if (command !in safeCommands && !hasPlotPermission(player, plot, "command-use")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "command-use.not_allowed"))
        }
    }

    /**
     * Blokada picia mikstur regularnych, jeśli use-potions = false.
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
            player.sendMessage(message.stringMessageToComponent("flags", "use-potions.not_allowed"))
        }
    }

    /**
     * Blokada rzucania splash mikstur, jeśli use-potions = false.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPotionSplash(event: PotionSplashEvent) {
        val potion = event.entity
        val shooter = potion.shooter as? Player ?: return

        val loc = potion.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(shooter, plot, "use-potions")) {
            event.isCancelled = true
            shooter.sendMessage(message.stringMessageToComponent("flags", "use-potions.not_allowed"))
        }
    }

    /**
     * Blokada rzucania lingering mikstur, jeśli use-potions = false.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPotionLingering(event: LingeringPotionSplashEvent) {
        val shooter = event.entity.shooter as? Player ?: return

        val loc = shooter.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(shooter, plot, "use-potions")) {
            event.isCancelled = true
            shooter.sendMessage(message.stringMessageToComponent("flags", "use-potions.not_allowed"))
        }
    }

    /**
     * Blokada lingering‐mikstur (AreaEffectCloud po trafieniu).
     * Kasujemy obłoczek, jeżeli ma się pojawić na zakazanej działce.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onProjectileHit(event: ProjectileHitEvent) {
        val potion = event.entity as? ThrownPotion ?: return
        if (potion.shooter !is Player) return

        val item: ItemStack = potion.item
        if (item.type != Material.LINGERING_POTION) return

        val loc = potion.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return
        val player = potion.shooter as Player

        if (!hasPlotPermission(player, plot, "use-potions")) {
            potion.remove()
            player.sendMessage(message.stringMessageToComponent("flags", "use-potions.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onProtectedProjectileHit(event: ProjectileHitEvent) {
        if (event.entity is ThrownPotion) return
        val player = projectilePlayer(event.entity) ?: return
        val targetLocation = event.hitEntity?.location ?: event.hitBlock?.location ?: event.entity.location
        val plot = getPlotAtLocation(targetLocation.world.name, targetLocation.blockX, targetLocation.blockZ) ?: return
        if (!hasPlotPermission(player, plot, "projectiles")) {
            event.isCancelled = true
            event.entity.remove()
            player.sendMessage(message.stringMessageToComponent("flags", "projectiles.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPortalCreate(event: PortalCreateEvent) {
        val protectedPlot = event.blocks
            .asSequence()
            .mapNotNull { plotAt(it.block) }
            .firstOrNull { !isFlagAllowed(it.id, "portal-create") }
        if (protectedPlot != null) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val player = event.player
        val fromPlot = getPlotAtLocation(event.from.world.name, event.from.blockX, event.from.blockZ)
        val to = event.to
        val toPlot = getPlotAtLocation(to.world.name, to.blockX, to.blockZ)
        val denied = listOfNotNull(fromPlot, toPlot)
            .distinctBy(PlotData::id)
            .any { !hasPlotPermission(player, it, "portal-use") }
        if (denied) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "portal-use.not_allowed"))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityBlockForm(event: EntityBlockFormEvent) {

        if (event.entity.type != EntityType.PLAYER) return
        val player = event.entity as Player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!hasPlotPermission(player, plot, "iceform-player")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockFormByWorld(event: BlockFormEvent) {
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!isFlagAllowed(plot.id, "block-transform")) {
            event.isCancelled = true
            return
        }

        val newType = event.newState.type
        if (newType != Material.ICE && newType != Material.SNOW && newType != Material.SNOW_BLOCK) return

        if (!isFlagAllowed(plot.id, "iceform-world")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!isFlagAllowed(plot.id, "block-transform")) {
            event.isCancelled = true
            return
        }

        val type = event.block.type
        if (type != Material.ICE && type != Material.SNOW && type != Material.SNOW_BLOCK) return

        if (!isFlagAllowed(plot.id, "iceform-world")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onLeavesDecay(event: LeavesDecayEvent) {
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!isFlagAllowed(plot.id, "leaves-decay")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        // dotyczy np. kukurydzy, pszenicy, trawy automatycznie
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (!isFlagAllowed(plot.id, "cant-grow")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPotionEffectAdd(event: EntityPotionEffectEvent) {
        val entity = event.entity as? Player ?: return


        val loc = entity.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?: return

        if (event.cause == EntityPotionEffectEvent.Cause.CONDUIT) {
            if (!isFlagAllowed(plot.id, "conduit-effects")) event.isCancelled = true
            return
        }

        if (!isFlagAllowed(plot.id, "effects")) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val cause = event.cause
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause.name != "CHORUS_FRUIT") return

        val player = event.player
        val fromPlot = getPlotAtLocation(
            event.from.world.name, event.from.blockX, event.from.blockZ
        )
        val toPlot   = getPlotAtLocation(
            event.to.world.name,   event.to.blockX,   event.to.blockZ
        )

        val plot = fromPlot ?: toPlot ?: return
        if (!hasPlotPermission(player, plot, "teleport")) {
            event.isCancelled = true
            player.sendMessage(message.stringMessageToComponent("flags", "teleport.not_allowed"))
        }
    }

}
