package pl.syntaxdevteam.plotsx.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.commands.PlotMembers
import pl.syntaxdevteam.plotsx.permissions.PlotAccess
import java.util.UUID

/** All pages carry only identifiers; click authorization uses the current database state. */
class MembersGUI(
    private val plugin: PlotsX,
    private val plotId: Int,
    private val screen: String = "members",
    private val target: UUID? = null,
    private val role: String? = null,
    private val page: Int = 0
) : AbstractGUI(plugin.messageHandler.stringMessageToComponentNoPrefix("members", "title"), 54) {
    private val access = PlotAccess(plugin)
    private val operations = PlotMembers(plugin)
    private val clicks = mutableMapOf<Int, (Player) -> Unit>()

    private fun button(slot: Int, material: Material, key: String, values: Map<String, String> = emptyMap(), action: (Player) -> Unit) {
        inventory.setItem(slot, plugin.guiHandler.createItem(material,
            plugin.messageHandler.stringMessageToComponentNoPrefix("members", key, values)))
        clicks[slot] = action
    }

    private fun show(player: Player, screen: String = "members", target: UUID? = null, role: String? = null, page: Int = 0) {
        plugin.guiHandler.registerGui(player, MembersGUI(plugin, plotId, screen, target, role, page))
    }

    override fun open(player: Player) {
        inventory.clear()
        clicks.clear()
        val plot = plugin.databaseHandler.getPlotById(plotId)
        if (plot == null || !access.canOpen(player, plot)) { operations.reply(player, "denied"); return }
        val members = plugin.databaseHandler.getPlotMembers(plotId)
        fun run(p: Player, args: List<String>) { operations.execute(p, plotId, args); show(p) }
        when (screen) {
            "members" -> {
                val entries = members.sortedBy { operations.name(UUID.fromString(it.memberUuid)).lowercase() }
                entries.drop(page * 45).take(45).forEachIndexed { slot, member ->
                    val id = UUID.fromString(member.memberUuid)
                    button(slot, Material.PLAYER_HEAD, "member_item", mapOf("player" to operations.name(id), "role" to member.memberRole)) {
                        show(it, "member", id)
                    }
                }
                pages(entries.size)
                if (access.allowed(player, plot, "invite")) button(46, Material.EMERALD, "add_button") { show(it, "add") }
                if (access.owner(player, plot)) button(48, Material.BOOK, "roles_button") { show(it, "roles") }
            }
            "add" -> {
                // Known offline players can be selected as well; no external profile lookup.
                val excluded = members.map { it.memberUuid }.toSet() + plot.ownerUuid.toString()
                val entries = (plugin.server.offlinePlayers.toList() + plugin.server.onlinePlayers)
                    .distinctBy { it.uniqueId }.filter { it.uniqueId.toString() !in excluded }
                    .sortedBy { it.name ?: it.uniqueId.toString() }
                entries.drop(page * 45).take(45).forEachIndexed { slot, candidate ->
                    button(slot, Material.PLAYER_HEAD, "player_item", mapOf("player" to (candidate.name ?: candidate.uniqueId.toString()))) {
                        run(it, listOf("add", candidate.uniqueId.toString()))
                    }
                }
                pages(entries.size)
            }
            "member" -> {
                if (target == null || members.none { it.memberUuid == target.toString() }) { show(player); return }
                if (access.allowed(player, plot, "kick")) button(20, Material.BARRIER, "remove_button") { show(it, "remove", target) }
                if (access.owner(player, plot)) {
                    button(22, Material.NAME_TAG, "rank_button") { show(it, "rank", target) }
                    button(24, Material.GOLDEN_HELMET, "transfer_button") { show(it, "transfer", target) }
                }
            }
            "rank", "roles" -> {
                access.roles.forEachIndexed { index, rank ->
                    button(20 + index * 2, Material.NAME_TAG, "role_item", mapOf("role" to rank)) {
                        if (screen == "roles") show(it, "permissions", role = rank)
                        else run(it, listOf("role", target.toString(), rank))
                    }
                }
            }
            "permissions" -> {
                val rank = role ?: return
                val actions = access.actions
                val grants = access.grants(plotId, rank)
                actions.drop(page * 45).take(45).forEachIndexed { slot, action ->
                    val granted = action in grants
                    button(slot, if (granted) Material.LIME_DYE else Material.GRAY_DYE, "permission_item",
                        mapOf("action" to action, "value" to granted.toString())) {
                        operations.execute(it, plotId, listOf("permission", rank, action, (!granted).toString()))
                        show(it, "permissions", role = rank, page = page)
                    }
                }
                pages(actions.size)
            }
            "remove", "transfer" -> {
                button(22, Material.LIME_CONCRETE, "confirm_$screen", mapOf("player" to operations.name(target ?: return))) {
                    run(it, if (screen == "transfer") listOf("transfer", target.toString(), "confirm")
                        else listOf("remove", target.toString()))
                }
            }
        }
        button(49, Material.ARROW, "back") {
            if (screen == "members") plugin.guiHandler.registerGui(it, PlotGUI(plugin, plot, plot.ownerUuid)) else show(it)
        }
        super.open(player)
    }

    private fun pages(count: Int) {
        if (page > 0) button(45, Material.ARROW, "previous") { show(it, screen, target, role, page - 1) }
        if ((page + 1) * 45 < count) button(53, Material.ARROW, "next") { show(it, screen, target, role, page + 1) }
    }

    override fun handleClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== inventory) return
        val player = event.whoClicked as? Player ?: return
        val plot = plugin.databaseHandler.getPlotById(plotId)
        if (plot == null || !access.canOpen(player, plot)) {
            player.closeInventory(); operations.reply(player, "denied"); return
        }
        clicks[event.rawSlot]?.invoke(player)
    }
}
