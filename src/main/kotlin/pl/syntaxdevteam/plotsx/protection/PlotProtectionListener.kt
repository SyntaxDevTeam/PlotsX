package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerInteractEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotData

class PlotProtectionListener(private val plugin: PlotsX) : Listener {

    private val logger = plugin.logger

    /** Znajduje działkę obejmującą dane współrzędne. */
    private fun getPlotAtLocation(world: String, x: Int, z: Int): PlotData? {
        return plugin.cacheManager.getCachedPlots().firstOrNull { plot ->
            plot.world.equals(world, ignoreCase = true) &&
                    x in (plot.x - plot.radius .. plot.x + plot.radius) &&
                    z in (plot.z - plot.radius .. plot.z + plot.radius)
        }
    }

    /** Wspólny handler blokujący i przywracający stan bloku. */
    private fun cancelAndRestore(block: Block) {
        block.state.update(true, false)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val to = event.block.location
        val plot = getPlotAtLocation(to.world.name, to.blockX, to.blockZ)

        logger.debug("BlockPlaceEvent at ${to.blockX},${to.blockZ} => plot=${plot?.id}")

        if (plot != null && !playerHasBuildPermission(player, plot)) {
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage("§cNie możesz stawiać bloków na tej działce!")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val loc = event.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)

        logger.debug("BlockBreakEvent at ${loc.blockX},${loc.blockZ} => plot=${plot?.id}")

        if (plot != null && !playerHasBuildPermission(player, plot)) {
            event.isCancelled = true
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                cancelAndRestore(event.block)
            }, 1L)
            player.sendMessage("§cNie możesz niszczyć bloków na tej działce!")
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        val block = event.clickedBlock ?: return
        val loc = block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ)

        val flag = when (block.type) {
            // Pojemniki
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.BARREL,
            Material.ENDER_CHEST,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX -> "use-container"

            // Drzwi
            Material.IRON_DOOR,
            Material.OAK_DOOR,
            Material.SPRUCE_DOOR,
            Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR,
            Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR,
            Material.CHERRY_DOOR,
            Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR,
            Material.WARPED_DOOR,
            Material.OAK_TRAPDOOR,
            Material.SPRUCE_TRAPDOOR,
            Material.BIRCH_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR,
            Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR,
            Material.WARPED_TRAPDOOR,
            Material.IRON_TRAPDOOR,
            Material.OAK_FENCE_GATE,
            Material.SPRUCE_FENCE_GATE,
            Material.BIRCH_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE,
            Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE,
            Material.CHERRY_FENCE_GATE,
            Material.BAMBOO_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE,
            Material.WARPED_FENCE_GATE -> "use-door"

            // Przełączniki
            Material.STONE_BUTTON,
            Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON,
            Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON,
            Material.LEVER -> "use-button"

            else -> "interact"
        }

        if (plot != null) {
            val flags = plugin.cacheManager.getFlags(plot.id)
            val hasPermission = when (flag) {
                "use-container" -> flags?.get("use-container") ?: false
                "use-door" -> flags?.get("use-door") ?: false
                "use-button" -> flags?.get("use-button") ?: false
                else -> false
            }

            if (!hasPermission) {
                event.isCancelled = true
                player.sendMessage("Nie masz dostępu do tej akcji na działce.")
            }
        }
    }

    /** Zapobiega ghost‑blokom z opadającymi blokami jak piasek/żwir */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onFallingBlock(e: EntityChangeBlockEvent) {
        if (e.entityType == org.bukkit.entity.EntityType.FALLING_BLOCK) {
            e.isCancelled = true
            e.block.blockData = e.block.blockData // wymuś update
        }
    }

    /** Blokuje przepływ wody i lawy przez granice działki */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onLiquidFlow(e: BlockFromToEvent) {
        val loc = e.block.location
        val plot = getPlotAtLocation(loc.world.name, loc.blockX, loc.blockZ) ?:
        getPlotAtLocation(e.toBlock.location.world.name, e.toBlock.location.blockX, e.toBlock.location.blockZ)
        if (plot != null && plugin.cacheManager.getFlags(plot.id)?.get("flow") == false) {
            e.isCancelled = true
        }
    }

    private fun playerHasBuildPermission(player: Player, plot: PlotData): Boolean {
        val uuid = plugin.uuidManager.getUUID(player.name)
        if (plot.ownerUuid == uuid) return true

        val members = plugin.cacheManager.getMembers(plot.id) ?: emptyList()
        if (members.any { it.memberUuid == uuid.toString() }) return true

        val flags = plugin.cacheManager.getFlags(plot.id)
        return flags?.get("build") == true
    }

}