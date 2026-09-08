package pl.syntaxdevteam.plotsx.api

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/** Notification after a successful API/GUI save and cache update. Not cancellable. */
class PlotFlagChangedEvent(val plot: PlotSnapshot, val flag: String, val oldValue: Boolean,
                           val newValue: Boolean, val actor: UUID?) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
