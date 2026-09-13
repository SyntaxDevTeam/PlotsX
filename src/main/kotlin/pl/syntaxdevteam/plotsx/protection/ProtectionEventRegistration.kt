package pl.syntaxdevteam.plotsx.protection

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import pl.syntaxdevteam.plotsx.PlotsX
import java.lang.reflect.InvocationTargetException

/** Every annotated protection handler receives a pinned cache view and the same mutation barrier. */
internal object ProtectionEventRegistration {
    fun register(plugin: PlotsX, listener: Listener) {
        for (method in listener.javaClass.methods) {
            val annotation = method.getAnnotation(EventHandler::class.java) ?: continue
            require(method.parameterCount == 1 && Event::class.java.isAssignableFrom(method.parameterTypes[0]))
            val type = method.parameterTypes[0].asSubclass(Event::class.java)
            plugin.server.pluginManager.registerEvent(type, listener, annotation.priority, { _, event ->
                if (!type.isInstance(event)) return@registerEvent
                plugin.protectionCoordinator.decision(
                    { if (event is Cancellable) event.isCancelled = true },
                    { plugin.cacheManager.withDecisionSnapshot {
                        try { method.invoke(listener, event) }
                        catch (failure: InvocationTargetException) { throw org.bukkit.event.EventException(failure.cause) }
                    } }
                )
            }, plugin, annotation.ignoreCancelled)
        }
    }
}
