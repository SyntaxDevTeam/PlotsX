package pl.syntaxdevteam.plotsx.interaction

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import pl.syntaxdevteam.plotsx.PlotsX
import pl.syntaxdevteam.plotsx.compatibility.DialogSupport
import pl.syntaxdevteam.plotsx.compatibility.VersionCompatibility
import pl.syntaxdevteam.plotsx.compatibility.platform.FoliaRunnable

/** No dialog API types may escape the optional adapter into this baseline facade. */
interface DialogBackend {
    fun showTextInput(player: Player, title: Component, body: List<Component>, initial: String,
                      yes: Component, no: Component, reply: (String?) -> Unit)
}

class PlotInteractions(private val plugin: PlotsX) : Listener {
    private val sessions = DialogSessions()
    private val backend: DialogBackend? = try {
        if (plugin.versionCompatibility.supports(VersionCompatibility.CompatibilityFlag.DIALOGS)) {
            Class.forName("io.papermc.paper.dialog.Dialog")
            Class.forName("pl.syntaxdevteam.plotsx.interaction.PaperDialogBackend")
                .getDeclaredConstructor().newInstance() as DialogBackend
        } else null
    } catch (_: ReflectiveOperationException) { null
    } catch (_: LinkageError) { null }

    fun text(key: String, values: Map<String, String> = emptyMap()): Component =
        plugin.messageHandler.stringMessageToComponentNoPrefix("dialogs", key, values)

    private fun available(): Boolean = backend != null &&
        DialogSupport.canUseDialogs(plugin)

    fun rename(player: Player, plotId: Int, initial: String, error: Component? = null): Boolean =
        show(player, text("rename"), listOfNotNull(error), initial, text("save"), text("cancel"), { p, value ->
            if (value != null) {
                RenamePlotService(plugin).rename(p, plotId, value) { result ->
                    if (result != null) {
                        if (!rename(p, plotId, value.take(255), result)) p.sendMessage(result)
                    }
                }
            }
        }, { plugin.renamePlotChatListener.startRenameProcess(player, plotId) })

    private fun show(player: Player, title: Component, body: List<Component>, initial: String,
                     yes: Component, no: Component, reply: (Player, String?) -> Unit,
                     fallback: () -> Unit): Boolean = showSession(player, fallback, { callback ->
        backend!!.showTextInput(player, title, body, initial, yes, no, callback)
    }, reply)

    private fun showSession(player: Player, fallback: () -> Unit,
                            display: ((String?) -> Unit) -> Unit,
                            reply: (Player, String?) -> Unit): Boolean {
        if (!available()) return false
        plugin.renamePlotChatListener.cancel(player)
        val token = sessions.start(player.uniqueId)
        // Inventory click handlers must finish before switching screens. Entity scheduler also supports Folia.
        FoliaRunnable.entity(player.scheduler, Runnable { sessions.remove(player.uniqueId, token) }) {
            if (!sessions.isCurrent(player.uniqueId, token)) return@entity
            plugin.guiHandler.unregisterGui(player)
            player.closeInventory()
            try {
                display { value ->
                    FoliaRunnable.entity(player.scheduler) {
                        if (sessions.consume(player.uniqueId, token)) reply(player, value)
                    }.run(plugin)
                }
                FoliaRunnable.entity(player.scheduler) {
                    if (sessions.remove(player.uniqueId, token)) {
                        player.closeInventory()
                        player.sendMessage(text("expired"))
                    }
                }.runDelayed(plugin, 1200L)
            } catch (ex: LinkageError) {
                sessions.remove(player.uniqueId, token)
                plugin.logger.warning("Dialog API unavailable: ${ex.javaClass.simpleName}; using legacy UI.")
                fallback()
            } catch (ex: RuntimeException) {
                sessions.remove(player.uniqueId, token)
                plugin.logger.warning("Cannot show dialog: ${ex.message}; using legacy UI.")
                fallback()
            }
        }.run(plugin)
        return true
    }

    @EventHandler fun onQuit(event: PlayerQuitEvent) { sessions.cancel(event.player.uniqueId) }
    @EventHandler fun onInventoryOpen(event: InventoryOpenEvent) { sessions.cancel(event.player.uniqueId) }
    fun close() { sessions.clear() }
}
