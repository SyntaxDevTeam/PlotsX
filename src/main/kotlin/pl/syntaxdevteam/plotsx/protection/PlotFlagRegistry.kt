package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material
import pl.syntaxdevteam.plotsx.compat.PlotCompat

object PlotFlagRegistry {
    private val builtInFlags: Map<String, FlagMeta> = listOf(
        FlagMeta("grave-create", true, FlagType.WHITELIST, Material.SOUL_SAND,
            "grave-create.name", "grave-create.description", memberBypass = false),
        FlagMeta("build", false, FlagType.WHITELIST, Material.STONE, "build.name", "build.description"),
        FlagMeta("pvp", false, FlagType.WHITELIST, Material.WOODEN_SWORD, "pvp.name", "pvp.description"),
        FlagMeta("chest", true, FlagType.BLACKLIST, Material.CHEST, "chest.name", "chest.description"),
        FlagMeta("ender-chest", true, FlagType.BLACKLIST, Material.ENDER_CHEST, "ender-chest.name", "ender-chest.description"),
        FlagMeta("lever", true, FlagType.BLACKLIST, Material.LEVER, "lever.name", "lever.description"),
        FlagMeta("button", true, FlagType.BLACKLIST, Material.STONE_BUTTON, "button.name", "button.description"),
        FlagMeta("door", true, FlagType.BLACKLIST, Material.BAMBOO_DOOR, "door.name", "door.description"),
        FlagMeta("smart-door", false, FlagType.WHITELIST, Material.IRON_DOOR, "smart-door.name", "smart-door.description"),
        FlagMeta("minecart", true, FlagType.BLACKLIST, Material.MINECART, "minecart.name", "minecart.description"),
        FlagMeta("redstone", false, FlagType.WHITELIST, Material.REDSTONE, "redstone.name", "redstone.description"),
        FlagMeta("utility", false, FlagType.WHITELIST, Material.FURNACE, "utility.name", "utility.description"),
        FlagMeta("spawn-monsters", false, FlagType.WHITELIST, Material.CARVED_PUMPKIN, "spawn-monsters.name", "spawn-monsters.description"),
        FlagMeta("spawn-animals", true, FlagType.WHITELIST, Material.EGG, "spawn-animals.name", "spawn-animals.description"),
        FlagMeta("passives", false, FlagType.WHITELIST, Material.SADDLE, "passives.name", "passives.description"),
        FlagMeta("flow", true, FlagType.BLACKLIST, Material.WATER_BUCKET, "flow.name", "flow.description"),
        FlagMeta("flow-damage", false, FlagType.WHITELIST, Material.LAVA_BUCKET, "flow-damage.name", "flow-damage.description"),
        FlagMeta("fire", true, FlagType.BLACKLIST, Material.FLINT_AND_STEEL, "fire.name", "fire.description"),
        FlagMeta("allow-home", false, FlagType.WHITELIST, Material.COMPASS, "allow-home.name", "allow-home.description"),
        FlagMeta("use-potions", false, FlagType.WHITELIST, Material.EXPERIENCE_BOTTLE, "use-potions.name", "use-potions.description"),
        FlagMeta("iceform-player", false, FlagType.WHITELIST, Material.SNOWBALL, "iceform-player.name", "iceform-player.description"),
        FlagMeta("iceform-world", false, FlagType.WHITELIST, Material.ICE, "iceform-world.name", "iceform-world.description"),
        FlagMeta("teleport", true, FlagType.BLACKLIST, Material.ENDER_PEARL, "teleport.name", "teleport.description"),
        FlagMeta("cant-grow", true, FlagType.BLACKLIST, Material.WHEAT, "cant-grow.name", "cant-grow.description"),
        FlagMeta("allow-spawners", false, FlagType.WHITELIST, Material.SPAWNER, "allow-spawners.name", "allow-spawners.description"),
        FlagMeta("leaves-decay", true, FlagType.BLACKLIST, Material.OAK_LEAVES, "leaves-decay.name", "leaves-decay.description"),
        FlagMeta("effects", true, FlagType.BLACKLIST, Material.BEACON, "effects.name", "effects.description"),
        FlagMeta("block-transform", true, FlagType.BLACKLIST, Material.MOSS_BLOCK, "block-transform.name", "block-transform.description"),
        FlagMeta("fall", true, FlagType.BLACKLIST, Material.SAND, "fall.name", "fall.description"),
        FlagMeta("explosions", true, FlagType.BLACKLIST, Material.TNT, "explosions.name", "explosions.description"),
        FlagMeta("decorations", false, FlagType.WHITELIST, Material.ARMOR_STAND, "decorations.name", "decorations.description"),
        FlagMeta("item-transfer", true, FlagType.BLACKLIST, Material.HOPPER, "item-transfer.name", "item-transfer.description"),
        FlagMeta("portal-create", true, FlagType.BLACKLIST, Material.OBSIDIAN, "portal-create.name", "portal-create.description"),
        FlagMeta("portal-use", false, FlagType.WHITELIST, Material.ENDER_EYE, "portal-use.name", "portal-use.description"),
        FlagMeta("projectiles", false, FlagType.WHITELIST, Material.BOW, "projectiles.name", "projectiles.description"),
        FlagMeta("item-pickup", false, FlagType.WHITELIST, Material.DIAMOND, "item-pickup.name", "item-pickup.description"),
        FlagMeta("item-drop", false, FlagType.WHITELIST, Material.DROPPER, "item-drop.name", "item-drop.description"),
        FlagMeta("crop-trample", false, FlagType.WHITELIST, Material.FARMLAND, "crop-trample.name", "crop-trample.description"),
        FlagMeta("animal-leash", false, FlagType.WHITELIST, Material.LEAD,
            "animal-leash.name", "animal-leash.description",
            customDisplayName = "animal-leash", customDescription = "Controls leashing and unleashing animals on the plot."),
        FlagMeta("animal-ride", false, FlagType.WHITELIST, Material.SADDLE,
            "animal-ride.name", "animal-ride.description",
            customDisplayName = "animal-ride", customDescription = "Controls mounting and riding animals on the plot."),
        FlagMeta("animal-breed", false, FlagType.WHITELIST, Material.WHEAT,
            "animal-breed.name", "animal-breed.description",
            customDisplayName = "animal-breed", customDescription = "Controls breeding and egg fertilization on the plot."),
        // Legacy compatibility flag. Existing values are copied to the three dedicated flags at startup.
        // It remains enabled and hidden so the old broad handlers are neutral while upgraded databases migrate safely.
        FlagMeta("animal-interact", true, FlagType.WHITELIST, Material.LEAD,
            "animal-interact.name", "animal-interact.description", visibleInGui = false),
        FlagMeta("command-use", true, FlagType.WHITELIST, Material.COMMAND_BLOCK, "command-use.name", "command-use.description"),
        FlagMeta("fishing", false, FlagType.WHITELIST, Material.FISHING_ROD, "fishing.name", "fishing.description"),
        FlagMeta("elytra", false, FlagType.WHITELIST, Material.ELYTRA, "elytra.name", "elytra.description"),
        FlagMeta("special-weapons", false, FlagType.WHITELIST, PlotCompat.safeMaterial("MACE") ?: Material.DIAMOND_SWORD, "special-weapons.name", "special-weapons.description"),
        FlagMeta("weather", true, FlagType.BLACKLIST, Material.LIGHTNING_ROD, "weather.name", "weather.description"),
        FlagMeta("bed-use", false, FlagType.WHITELIST, Material.RED_BED, "bed-use.name", "bed-use.description"),
        FlagMeta("crafting", false, FlagType.WHITELIST, Material.CRAFTING_TABLE, "crafting.name", "crafting.description"),
        FlagMeta("enchanting", false, FlagType.WHITELIST, Material.ENCHANTING_TABLE, "enchanting.name", "enchanting.description"),
        FlagMeta("respawn-anchor", false, FlagType.WHITELIST, Material.RESPAWN_ANCHOR, "respawn-anchor.name", "respawn-anchor.description"),
        FlagMeta("conduit-effects", true, FlagType.BLACKLIST, Material.CONDUIT, "conduit-effects.name", "conduit-effects.description"),
        FlagMeta("flight", false, FlagType.WHITELIST, Material.FEATHER, "flight.name", "flight.description"),
        // FlagMeta("block-transform", true, FlagType.BLACKLIST, Material.MOSS_BLOCK, "block-transform.name", "block-transform.description")
        // FlagMeta("team", false, FlagType.WHITELIST, Material.NAME_TAG, "team.name", "team.description"),
        // FlagMeta("mob-loot", false, FlagType.WHITELIST, Material.MYCELIUM, "mob-loot.name", "mob-loot.description"),
        // FlagMeta("allow-fly", false, FlagType.WHITELIST, Material.ELYTRA, "allow-fly.name", "allow-fly.description")

    ).associateBy { it.name }

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
