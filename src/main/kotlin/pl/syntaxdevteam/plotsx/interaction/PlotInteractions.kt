package pl.syntaxdevteam.plotsx.interaction

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import pl.syntaxdevteam.plotsx.PlotsX

/** No dialog API types may escape the optional adapter into this baseline facade. */
interface DialogBackend {
    fun show(player: Player, title: Component, body: List<Component>, initial: String?,
             yes: Component, no: Component, reply: (String?) -> Unit)
    fun showChoices(player: Player, title: Component, body: List<Component>, choices: Map<String, Component>,
                    cancel: Component, reply: (String?) -> Unit)
}

class PlotInteractions(private val plugin: PlotsX) : Listener {
    private val sessions = DialogSessions()
    private val backend: DialogBackend? = try {
        Class.forName("io.papermc.paper.dialog.Dialog")
        Class.forName("pl.syntaxdevteam.plotsx.interaction.PaperDialogBackend")
            .getDeclaredConstructor().newInstance() as DialogBackend
    } catch (_: ReflectiveOperationException) { null
    } catch (_: LinkageError) { null }

    fun text(key: String, values: Map<String, String> = emptyMap()): Component =
        plugin.messageHandler.stringMessageToComponentNoPrefix("dialogs", key, values)

    private fun available(): Boolean = backend != null &&
        !plugin.config.getString("interactions.mode", "auto").equals("legacy", true) &&
        // Without reliable per-client negotiation, use the compatible UI on translated servers.
        listOf("ViaVersion", "ViaBackwards", "ProtocolSupport", "Geyser-Spigot", "floodgate")
            .none { plugin.server.pluginManager.isPluginEnabled(it) } &&
        !plugin.config.getBoolean("interactions.translated-clients", false)

    fun confirm(player: Player, title: Component, body: List<Component>, yes: Component = text("confirm"),
                no: Component = text("cancel"), onConfirm: (Player) -> Unit, onCancel: (Player) -> Unit = {},
                fallback: () -> Unit): Boolean =
        show(player, title, body, null, yes, no, { p, value ->
            if (value != null) onConfirm(p) else onCancel(p)
        }, fallback)

    fun choose(player: Player, title: Component, body: List<Component>, choices: Map<String, Component>,
               onChoice: (Player, String) -> Unit, onCancel: (Player) -> Unit = {},
               fallback: () -> Unit): Boolean {
        if (choices.isEmpty()) return false
        return showSession(player, fallback, { reply ->
            backend!!.showChoices(player, title, body, choices, text("cancel"), reply)
        }) { p, value -> if (value == null) onCancel(p) else onChoice(p, value) }
    }

    fun rename(player: Player, plotId: Int, initial: String, error: Component? = null): Boolean =
        show(player, text("rename"), listOfNotNull(error), initial, text("save"), text("cancel"), { p, value ->
            if (value != null) {
                val result = RenamePlotService(plugin).rename(p, plotId, value)
                if (result != null) {
                    if (!rename(p, plotId, value.take(255), result)) p.sendMessage(result)
                }
            }
        }, { plugin.renamePlotChatListener.startRenameProcess(player, plotId) })

    private fun show(player: Player, title: Component, body: List<Component>, initial: String?,
                     yes: Component, no: Component, reply: (Player, String?) -> Unit,
                     fallback: () -> Unit): Boolean = showSession(player, fallback, { callback ->
        backend!!.show(player, title, body, initial, yes, no, callback)
    }, reply)

    private fun showSession(player: Player, fallback: () -> Unit,
                            display: ((String?) -> Unit) -> Unit,
                            reply: (Player, String?) -> Unit): Boolean {
        if (!available()) return false
        plugin.renamePlotChatListener.cancel(player)
        val token = sessions.start(player.uniqueId)
        // Inventory click handlers must finish before switching screens. Entity scheduler also supports Folia.
        player.scheduler.run(plugin, {
            if (!sessions.isCurrent(player.uniqueId, token)) return@run
            plugin.guiHandler.unregisterGui(player)
            player.closeInventory()
            try {
                display { value ->
                    player.scheduler.run(plugin, {
                        if (sessions.consume(player.uniqueId, token)) reply(player, value)
                    }, null)
                }
                player.scheduler.runDelayed(plugin, {
                    if (sessions.remove(player.uniqueId, token)) {
                        player.closeInventory()
                        player.sendMessage(text("expired"))
                    }
                }, null, 1200L)
            } catch (ex: LinkageError) {
                sessions.remove(player.uniqueId, token)
                plugin.logger.warning("Dialog API unavailable: ${ex.javaClass.simpleName}; using legacy UI.")
                fallback()
            } catch (ex: RuntimeException) {
                sessions.remove(player.uniqueId, token)
                plugin.logger.warning("Cannot show dialog: ${ex.message}; using legacy UI.")
                fallback()
            }
        }, { sessions.remove(player.uniqueId, token) })
        return true
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) { sessions.cancel(event.player.uniqueId) }
    @EventHandler fun onInventoryOpen(event: InventoryOpenEvent) { sessions.cancel(event.player.uniqueId) }
    fun close() { sessions.clear() }
}
