package pl.syntaxdevteam.plotsx.hooks

/** Reflection keeps the optional plugin's classes in its own classloader. */
internal object CleanerXNameFilter {
    fun isAllowed(api: Any, name: String): Boolean? {
        val banned = api.javaClass.getMethod("containsBannedWord", String::class.java)
            .invoke(api, name) as? Boolean ?: return null
        return !banned
    }
}
