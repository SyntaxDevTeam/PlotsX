package pl.syntaxdevteam.plotsx.protection

internal data class FlagBehavior(val defaultValue: Boolean, val type: FlagType)

/** Semantic contract kept independent of Bukkit so every built-in flag can be unit-tested. */
internal object BuiltInFlagBehaviors {
    val all: Map<String, FlagBehavior> = buildMap {
        whitelist(defaultValue = true, "grave-create", "spawn-animals", "animal-interact", "command-use")
        whitelist(defaultValue = false,
            "build", "path-create", "log-strip", "pvp", "smart-door", "redstone", "pistons", "utility",
            "spawn-monsters", "passives", "flow-damage", "allow-home", "use-potions", "iceform-player",
            "iceform-world", "allow-spawners", "decorations", "portal-create", "portal-use", "projectiles",
            "item-pickup", "item-drop", "crop-trample", "animal-leash", "animal-ride", "animal-breed",
            "fishing", "elytra", "special-weapons", "bed-use", "crafting", "enchanting", "respawn-anchor",
            "flight")
        blacklist(defaultValue = true,
            "chest", "ender-chest", "lever", "button", "door", "minecart", "flow", "fire", "teleport",
            "cant-grow", "leaves-decay", "effects", "block-transform", "fall", "explosions", "item-transfer",
            "weather", "conduit-effects")
    }

    private fun MutableMap<String, FlagBehavior>.whitelist(defaultValue: Boolean, vararg names: String) =
        add(defaultValue, FlagType.WHITELIST, names)

    private fun MutableMap<String, FlagBehavior>.blacklist(defaultValue: Boolean, vararg names: String) =
        add(defaultValue, FlagType.BLACKLIST, names)

    private fun MutableMap<String, FlagBehavior>.add(defaultValue: Boolean, type: FlagType, names: Array<out String>) {
        names.forEach { name -> check(put(name, FlagBehavior(defaultValue, type)) == null) { "Duplicate flag: $name" } }
    }
}
