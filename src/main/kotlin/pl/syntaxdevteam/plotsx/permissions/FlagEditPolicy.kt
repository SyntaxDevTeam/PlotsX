package pl.syntaxdevteam.plotsx.permissions

/** Shared, Bukkit-free policy. Null means the permission provider has no effective assignment. */
internal object FlagEditPolicy {
    fun allowed(enabled: Boolean, bypass: Boolean, defaultAccess: Boolean,
                permission: (String) -> Boolean?, flag: String): Boolean {
        if (!enabled || bypass) return true
        return permission("plotsx.flag.$flag") ?: permission("plotsx.flag.*") ?: defaultAccess
    }

    fun roleDefault(grants: Collection<String>, action: String): Boolean =
        action in grants || (action.startsWith("flag.") && "flag.*" in grants)
}
