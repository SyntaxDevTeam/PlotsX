package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material

object PlotFlagRegistry {
    val allFlags: Map<String, FlagMeta> = listOf(
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
        FlagMeta("animal-interact", false, FlagType.WHITELIST, Material.LEAD, "animal-interact.name", "animal-interact.description"),
        // FlagMeta("block-transform", true, FlagType.BLACKLIST, Material.MOSS_BLOCK, "block-transform.name", "block-transform.description")
        // FlagMeta("team", false, FlagType.WHITELIST, Material.NAME_TAG, "team.name", "team.description"),
        // FlagMeta("mob-loot", false, FlagType.WHITELIST, Material.MYCELIUM, "mob-loot.name", "mob-loot.description"),
        // FlagMeta("allow-fly", false, FlagType.WHITELIST, Material.ELYTRA, "allow-fly.name", "allow-fly.description")

    ).associateBy { it.name }
}
