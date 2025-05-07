package pl.syntaxdevteam.plotsx.protection

object PlotFlagRegistry {
    val allFlags: Map<String, FlagDefinition> = mapOf(
        "build"            to FlagDefinition("build", false, FlagType.WHITELIST), // ✅ ok
        "pvp"              to FlagDefinition("pvp", false, FlagType.WHITELIST),
        "chest"            to FlagDefinition("chest", true, FlagType.BLACKLIST), // ✅ ok
        "ender-chest"      to FlagDefinition("ender-chest", true, FlagType.BLACKLIST), // ✅ ok
        "lever"            to FlagDefinition("lever", true, FlagType.BLACKLIST), // ✅ ok
        "button"           to FlagDefinition("button", true, FlagType.BLACKLIST), // ✅ ok
        "redstone"         to FlagDefinition("redstone", false, FlagType.WHITELIST),
        "utility"          to FlagDefinition("utility", false, FlagType.WHITELIST), // ✅ ok
        "door"             to FlagDefinition("door", true, FlagType.BLACKLIST), // ✅ ok
        "smart-door"       to FlagDefinition("smart-door", false, FlagType.WHITELIST), // ✅ ok
        "spawn-monsters"   to FlagDefinition("spawn-monsters", false, FlagType.WHITELIST), // ??
        "spawn-animals"    to FlagDefinition("spawn-animals", true, FlagType.BLACKLIST), // ??
        "passives"         to FlagDefinition("passives", false, FlagType.WHITELIST),
        "flow"             to FlagDefinition("flow", true, FlagType.BLACKLIST), // ✅ ok
        "flow-damage"      to FlagDefinition("flow-damage", false, FlagType.WHITELIST), // ✅ ok
        "fire"             to FlagDefinition("fire", true, FlagType.BLACKLIST),
        "minecart"         to FlagDefinition("minecart", true, FlagType.BLACKLIST),
        "allow-home"       to FlagDefinition("allow-home", false, FlagType.WHITELIST),
        "use-potions"      to FlagDefinition("use-potions", false, FlagType.WHITELIST),
        "mob-loot"         to FlagDefinition("mob-loot", false, FlagType.WHITELIST),
        "iceform-player"   to FlagDefinition("iceform-player", false, FlagType.WHITELIST),
        "iceform-world"    to FlagDefinition("iceform-world", false, FlagType.WHITELIST),
        "allow-fly"        to FlagDefinition("allow-fly", false, FlagType.WHITELIST),
        "teleport"         to FlagDefinition("teleport", true, FlagType.BLACKLIST),
        "cant-grow"        to FlagDefinition("cant-grow", true, FlagType.BLACKLIST),
        "allow-spawners"   to FlagDefinition("allow-spawners", false, FlagType.WHITELIST),
        "leaves-decay"     to FlagDefinition("leaves-decay", true, FlagType.BLACKLIST),
        "effects"          to FlagDefinition("effects", true, FlagType.BLACKLIST),
        "block-transform"  to FlagDefinition("block-transform", true, FlagType.BLACKLIST),
        "team"             to FlagDefinition("team", false, FlagType.WHITELIST)
    )
}
