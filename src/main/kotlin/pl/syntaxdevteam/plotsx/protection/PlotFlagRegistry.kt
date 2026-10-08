package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material
import pl.syntaxdevteam.plotsx.compat.PlotCompat

object PlotFlagRegistry {
    private fun flag(
        name: String,
        material: Material,
        displayKey: String,
        descriptionKey: String,
        memberBypass: Boolean = true,
        visibleInGui: Boolean = true
    ): FlagMeta {
        val behavior = requireNotNull(BuiltInFlagBehaviors.all[name]) { "Missing behavior for built-in flag: $name" }
        return FlagMeta(name, behavior.defaultValue, behavior.type, material, displayKey, descriptionKey,
            memberBypass = memberBypass, visibleInGui = visibleInGui)
    }

    private val builtInFlags: Map<String, FlagMeta> = listOf(
        flag("grave-create", Material.SOUL_SAND,
            "grave-create.name", "grave-create.description", memberBypass = false),
        flag("build", Material.STONE, "build.name", "build.description"),
        flag("path-create", Material.DIRT_PATH,
            "path-create.name", "path-create.description"),
        flag("log-strip", Material.IRON_AXE,
            "log-strip.name", "log-strip.description"),
        flag("pvp", Material.WOODEN_SWORD, "pvp.name", "pvp.description"),
        flag("chest", Material.CHEST, "chest.name", "chest.description"),
        flag("ender-chest", Material.ENDER_CHEST, "ender-chest.name", "ender-chest.description"),
        flag("lever", Material.LEVER, "lever.name", "lever.description"),
        flag("button", Material.STONE_BUTTON, "button.name", "button.description"),
        flag("door", Material.BAMBOO_DOOR, "door.name", "door.description"),
        flag("smart-door", Material.OAK_DOOR, "smart-door.name", "smart-door.description", memberBypass = false),
        flag("iron-door", Material.IRON_DOOR, "iron-door.name", "iron-door.description", memberBypass = false),
        flag("minecart", Material.MINECART, "minecart.name", "minecart.description"),
        flag("redstone", Material.REDSTONE, "redstone.name", "redstone.description"),
        flag("pistons", Material.PISTON, "pistons.name", "pistons.description"),
        flag("utility", Material.FURNACE, "utility.name", "utility.description"),
        flag("spawn-monsters", Material.CARVED_PUMPKIN, "spawn-monsters.name", "spawn-monsters.description"),
        flag("spawn-animals", Material.EGG, "spawn-animals.name", "spawn-animals.description"),
        flag("passives", Material.SADDLE, "passives.name", "passives.description"),
        flag("flow", Material.WATER_BUCKET, "flow.name", "flow.description"),
        flag("flow-damage", Material.LAVA_BUCKET, "flow-damage.name", "flow-damage.description"),
        flag("fire", Material.FLINT_AND_STEEL, "fire.name", "fire.description"),
        flag("allow-home", Material.COMPASS, "allow-home.name", "allow-home.description"),
        flag("use-potions", Material.EXPERIENCE_BOTTLE, "use-potions.name", "use-potions.description"),
        flag("iceform-player", Material.SNOWBALL, "iceform-player.name", "iceform-player.description"),
        flag("iceform-world", Material.ICE, "iceform-world.name", "iceform-world.description"),
        flag("teleport", Material.ENDER_PEARL, "teleport.name", "teleport.description"),
        flag("cant-grow", Material.WHEAT, "cant-grow.name", "cant-grow.description"),
        flag("allow-spawners", Material.SPAWNER, "allow-spawners.name", "allow-spawners.description"),
        flag("leaves-decay", Material.OAK_LEAVES, "leaves-decay.name", "leaves-decay.description"),
        flag("effects", Material.BEACON, "effects.name", "effects.description"),
        flag("block-transform", Material.MOSS_BLOCK, "block-transform.name", "block-transform.description"),
        flag("fall", Material.SAND, "fall.name", "fall.description"),
        flag("explosions", Material.TNT, "explosions.name", "explosions.description"),
        flag("decorations", Material.ARMOR_STAND, "decorations.name", "decorations.description"),
        flag("item-transfer", Material.HOPPER, "item-transfer.name", "item-transfer.description"),
        flag("portal-create", Material.OBSIDIAN, "portal-create.name", "portal-create.description"),
        flag("portal-use", Material.ENDER_EYE, "portal-use.name", "portal-use.description"),
        flag("projectiles", Material.BOW, "projectiles.name", "projectiles.description"),
        flag("item-pickup", Material.DIAMOND, "item-pickup.name", "item-pickup.description"),
        flag("item-drop", Material.DROPPER, "item-drop.name", "item-drop.description"),
        flag("crop-trample", Material.FARMLAND, "crop-trample.name", "crop-trample.description"),
        flag("animal-leash", Material.LEAD,
            "animal-leash.name", "animal-leash.description"),
        flag("animal-ride", Material.SADDLE,
            "animal-ride.name", "animal-ride.description"),
        flag("animal-breed", Material.WHEAT,
            "animal-breed.name", "animal-breed.description"),
        // Legacy compatibility flag. Existing values are copied to the three dedicated flags at startup.
        // It remains enabled and hidden so the old broad handlers are neutral while upgraded databases migrate safely.
        flag("animal-interact", Material.LEAD,
            "animal-interact.name", "animal-interact.description", visibleInGui = false),
        flag("command-use", Material.COMMAND_BLOCK, "command-use.name", "command-use.description"),
        flag("fishing", Material.FISHING_ROD, "fishing.name", "fishing.description"),
        flag("elytra", Material.ELYTRA, "elytra.name", "elytra.description"),
        flag("special-weapons", PlotCompat.safeMaterial("MACE") ?: Material.DIAMOND_SWORD, "special-weapons.name", "special-weapons.description"),
        flag("weather", Material.LIGHTNING_ROD, "weather.name", "weather.description"),
        flag("bed-use", Material.RED_BED, "bed-use.name", "bed-use.description"),
        flag("crafting", Material.CRAFTING_TABLE, "crafting.name", "crafting.description"),
        flag("enchanting", Material.ENCHANTING_TABLE, "enchanting.name", "enchanting.description"),
        flag("respawn-anchor", Material.RESPAWN_ANCHOR, "respawn-anchor.name", "respawn-anchor.description"),
        flag("conduit-effects", Material.CONDUIT, "conduit-effects.name", "conduit-effects.description"),
        flag("flight", Material.FEATHER, "flight.name", "flight.description"),
        // FlagMeta("block-transform", true, FlagType.BLACKLIST, Material.MOSS_BLOCK, "block-transform.name", "block-transform.description")
        // FlagMeta("team", false, FlagType.WHITELIST, Material.NAME_TAG, "team.name", "team.description"),
        // FlagMeta("mob-loot", false, FlagType.WHITELIST, Material.MYCELIUM, "mob-loot.name", "mob-loot.description"),
        // FlagMeta("allow-fly", false, FlagType.WHITELIST, Material.ELYTRA, "allow-fly.name", "allow-fly.description")

    ).associateBy { it.name }.also { flags ->
        check(flags.keys == BuiltInFlagBehaviors.all.keys) {
            "Built-in flag registry and behavior contract differ: registry=${flags.keys}, behaviors=${BuiltInFlagBehaviors.all.keys}"
        }
    }

    @Volatile private var registered: Map<String, FlagMeta> = java.util.Collections.unmodifiableMap(builtInFlags)
    val allFlags: Map<String, FlagMeta> get() = registered
    val visibleFlags: List<FlagMeta> get() = registered.values.filter(FlagMeta::visibleInGui)

    @Synchronized internal fun register(flag: FlagMeta): Boolean {
        if (flag.name in registered) return false
        registered = java.util.Collections.unmodifiableMap(LinkedHashMap(registered).apply { put(flag.name, flag) })
        return true
    }

    @Synchronized internal fun unregister(key: String): Boolean {
        if (key in builtInFlags || key !in registered) return false
        registered = java.util.Collections.unmodifiableMap(LinkedHashMap(registered).apply { remove(key) })
        return true
    }
}
