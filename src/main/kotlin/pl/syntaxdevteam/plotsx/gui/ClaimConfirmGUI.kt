package pl.syntaxdevteam.plotsx.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.databases.PlotLogEntry

class ClaimConfirmGUI(private var plugin: PlotsX) : GUI {
    private val claimConfirmTitleMaterialName = plugin.config.getString("GUI.claim_confirm.title", "<green><b>Create a plot")!!
    private val claimConfirmMaterialName = plugin.config.getString("GUI.claim_confirm.material", claimConfirmTitleMaterialName)!!
    private val claimConfirmMaterial = Material.matchMaterial(claimConfirmMaterialName) ?: Material.EMERALD_BLOCK
    private val claimConfirmIndex = plugin.config.getInt("GUI.claim_confirm.index", 11)

    private val claimNotConfirmTitleMaterialName = plugin.config.getString("GUI.claim_not_confirm.title", "<red>Cancel plot creation")!!
    private val claimNotConfirmMaterialName = plugin.config.getString("GUI.claim_not_confirm.material", claimNotConfirmTitleMaterialName)!!
    private val claimNotConfirmMaterial = Material.matchMaterial(claimNotConfirmMaterialName) ?: Material.REDSTONE_BLOCK
    private val claimNotConfirmIndex = plugin.config.getInt("GUI.claim_not_confirm.index", 15)

    override fun open(player: Player) {
        val inventory = Bukkit.createInventory(null, 27, getTitle())

        val claimConfirmItem = createItem(claimConfirmMaterial, claimConfirmTitleMaterialName)
        val claimNotConfirmItem = createItem(claimNotConfirmMaterial, claimNotConfirmTitleMaterialName)

        inventory.setItem(claimConfirmIndex, claimConfirmItem)
        inventory.setItem(claimNotConfirmIndex, claimNotConfirmItem)

        player.openInventory(inventory)
    }

    override fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem ?: return

        event.isCancelled = true

        when (clickedItem.type) {
            claimConfirmMaterial -> {
                val dbh = plugin.databaseHandler
                val location = player.location
                val world = location.world.name
                val x = location.blockX
                val z = location.blockZ
                val radius = plugin.config.getInt("plots.radius", 16)

                val currentPlot = dbh.getPlotAtLocation(world, x, z)
                if (currentPlot != null) {
                    player.sendMessage(plugin.messageHandler.getMessage("error", "is_plot"))
                    return
                }

                if (dbh.doesPlotOverlap(x, z, radius, world)) {
                    player.sendMessage(plugin.messageHandler.getMessage("error", "in_collision"))
                    return
                }

                val plotId = dbh.createNewPlot(player.uniqueId, world, x, z, radius, "${player.name}'s_Plot")
                if (plotId == null) {
                    player.sendMessage(plugin.messageHandler.getMessage("error", "create_error"))
                    return
                }

                dbh.logPlotAction(
                    PlotLogEntry(
                        plotId = plotId,
                        action = "CREATE",
                        actorUUID = player.uniqueId,
                        timestamp = System.currentTimeMillis()
                    )
                )
                player.sendMessage(plugin.messageHandler.getMessage("plots", "claim_success"))
                player.closeInventory()
            }
            claimNotConfirmMaterial -> {
                player.closeInventory()
            }
            else -> {

            }
        }
    }

    override fun getTitle(): Component {
        return Component.text(plugin.messageHandler.getCleanMessage("GUI", "title_initialGUI"))
    }

    private fun createItem(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(plugin.messageHandler.formatMixedTextToMiniMessage(name, TagResolver.empty()))
        item.itemMeta = meta
        return item
    }
}