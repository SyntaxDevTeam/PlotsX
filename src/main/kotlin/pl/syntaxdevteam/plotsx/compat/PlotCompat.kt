package pl.syntaxdevteam.plotsx.compat

import org.bukkit.Material
import org.bukkit.entity.EntityType

object PlotCompat {

    val aggressiveMobNames = listOf(
        "BLAZE", "CAVE_SPIDER", "CREAKING", "CREEPER", "DROWNED",
        "ELDER_GUARDIAN", "ENDERMAN", "ENDERMITE", "EVOKER", "GHAST",
        "GIANT", "GUARDIAN", "HUSK", "ILLUSIONER", "MAGMA_CUBE",
        "PHANTOM", "PIGLIN", "PIGLIN_BRUTE", "PILLAGER", "RAVAGER",
        "SHULKER", "SILVERFISH", "SKELETON", "SLIME", "SPIDER",
        "STRAY", "VEX", "VINDICATOR", "WITCH", "WITHER",
        "WITHER_SKELETON", "WARDEN", "ZOGLIN", "ZOMBIFIED_PIGLIN",
        "ZOMBIE", "ZOMBIE_VILLAGER"
    )

    val passiveMobNames = listOf(
        "ALLAY", "ARMADILLO", "AXOLOTL", "BAT", "BEE",
        "CAT", "CHICKEN", "COD", "COW", "DOLPHIN",
        "DONKEY", "FOX", "FROG", "GOAT", "HORSE",
        "MOOSHROOM", "MULE", "OCELOT", "PANDA", "PARROT",
        "PIG", "PUFFERFISH", "RABBIT", "SALMON", "SHEEP",
        "SNIFFER", "SNOW_GOLEM", "SQUID", "STRIDER", "TADPOLE",
        "TROPICAL_FISH", "TURTLE", "VILLAGER", "WANDERING_TRADER",
        "WOLF", "ZOMBIE_HORSE", "LAMA"
    )

    val doorNames = listOf(
        "OAK_DOOR", "SPRUCE_DOOR", "BIRCH_DOOR", "JUNGLE_DOOR",
        "ACACIA_DOOR", "DARK_OAK_DOOR", "MANGROVE_DOOR", "CHERRY_DOOR",
        "BAMBOO_DOOR", "CRIMSON_DOOR", "WARPED_DOOR", "PALE_OAK_DOOR",
        "COPPER_DOOR", "EXPOSED_COPPER_DOOR", "WEATHERED_COPPER_DOOR",
        "OXIDIZED_COPPER_DOOR", "WAXED_COPPER_DOOR", "WAXED_EXPOSED_COPPER_DOOR",
        "WAXED_WEATHERED_COPPER_DOOR", "WAXED_OXIDIZED_COPPER_DOOR", "IRON_DOOR"
    )

    val trapdoorNames = listOf(
        "OAK_TRAPDOOR", "SPRUCE_TRAPDOOR", "BIRCH_TRAPDOOR",
        "JUNGLE_TRAPDOOR", "ACACIA_TRAPDOOR", "DARK_OAK_TRAPDOOR",
        "MANGROVE_TRAPDOOR", "CHERRY_TRAPDOOR", "BAMBOO_TRAPDOOR",
        "CRIMSON_TRAPDOOR", "WARPED_TRAPDOOR", "IRON_TRAPDOOR",
        "PALE_OAK_TRAPDOOR", "IRON_TRAPDOOR", "COPPER_TRAPDOOR",
        "EXPOSED_COPPER_TRAPDOOR", "WEATHERED_COPPER_TRAPDOOR",
        "OXIDIZED_COPPER_TRAPDOOR", "WAXED_COPPER_TRAPDOOR",
        "WAXED_EXPOSED_COPPER_TRAPDOOR", "WAXED_WEATHERED_COPPER_TRAPDOOR",
        "WAXED_OXIDIZED_COPPER_TRAPDOOR"
    )

    val fenceGateNames = listOf(
        "OAK_FENCE_GATE", "SPRUCE_FENCE_GATE", "BIRCH_FENCE_GATE",
        "JUNGLE_FENCE_GATE", "ACACIA_FENCE_GATE", "DARK_OAK_FENCE_GATE",
        "MANGROVE_FENCE_GATE", "CHERRY_FENCE_GATE", "BAMBOO_FENCE_GATE",
        "CRIMSON_FENCE_GATE", "WARPED_FENCE_GATE", "PALE_OAK_FENCE_GATE",

    )

    val buttonNames = listOf(
        "LEVER", "STONE_BUTTON", "OAK_BUTTON", "SPRUCE_BUTTON",
        "BIRCH_BUTTON", "JUNGLE_BUTTON", "ACACIA_BUTTON", "DARK_OAK_BUTTON",
        "MANGROVE_BUTTON", "CHERRY_BUTTON", "BAMBOO_BUTTON",
        "CRIMSON_BUTTON", "WARPED_BUTTON", "POLISHED_BLACKSTONE_BUTTON",
        "PALE_OAK_BUTTON", "OAK_PRESSURE_PLATE", "SPRUCE_PRESSURE_PLATE",
        "BIRCH_PRESSURE_PLATE", "JUNGLE_PRESSURE_PLATE", "ACACIA_PRESSURE_PLATE",
        "DARK_OAK_PRESSURE_PLATE", "MANGROVE_PRESSURE_PLATE",
        "CHERRY_PRESSURE_PLATE", "BAMBOO_PRESSURE_PLATE",
        "CRIMSON_PRESSURE_PLATE", "WARPED_PRESSURE_PLATE",
        "POLISHED_BLACKSTONE_PRESSURE_PLATE", "PALE_OAK_PRESSURE_PLATE",
        "IRON_PRESSURE_PLATE", "GOLD_PRESSURE_PLATE"
    )

    val containerNames = listOf("CHEST", "TRAPPED_CHEST", "BARREL", "SHULKER_BOX", "CHEST_MINECART")

    val enderChestNames = listOf("ENDER_CHEST")

    val dispenserBucketNames = listOf(
        "WATER_BUCKET", "LAVA_BUCKET", "POWDER_SNOW_BUCKET",
        "BUCKET", "EGG", "BLUE_EGG", "BROWN_EGG",
        "SNIFFER_EGG", "TURTLE_EGG"
    )

    private val damageableByFlowNames = listOf(
        // Uprawy
        "WHEAT",
        "CARROTS",
        "POTATOES",
        "BEETROOTS",
        "NETHER_WART",
        "COCOA",
        "SWEET_BERRY_BUSH",
        "GLOW_BERRIES",

        // Rośliny półwodne
        "SUGAR_CANE",
        "BAMBOO",

        // Jednopłytkowe kwiaty i zioła
        "POPPY",
        "DANDELION",
        "BLUE_ORCHID",
        "ALLIUM",
        "AZURE_BLUET",
        "RED_TULIP",
        "ORANGE_TULIP",
        "WHITE_TULIP",
        "PINK_TULIP",
        "OXEYE_DAISY",
        "CORNFLOWER",
        "LILY_OF_THE_VALLEY",
        "WITHER_ROSE",

        // Dwublokowe rośliny ozdobne
        "SUNFLOWER",
        "LILAC",
        "ROSE_BUSH",
        "PEONY",
        "TALL_GRASS",
        "LARGE_FERN",

        // Zwykłe trawy i paprocie
        "GRASS",
        "TALL_GRASS",
        "FERN",
        "LARGE_FERN",

        // Nether / End
        "CHORUS_PLANT",
        "CHORUS_FLOWER",
        "CRIMSON_ROOTS",
        "WARPED_ROOTS",
        "NETHER_SPROUTS",
        "TWISTING_VINES",
        "WEEPING_VINES",

        // Sadzonki drzew i mangrowe
        "OAK_SAPLING",
        "BIRCH_SAPLING",
        "SPRUCE_SAPLING",
        "JUNGLE_SAPLING",
        "DARK_OAK_SAPLING",
        "ACACIA_SAPLING",
        "MANGROVE_PROPAGULE",
        "CHERRY_SAPLING",

        // Grzyby
        "RED_MUSHROOM",
        "BROWN_MUSHROOM",

        // Dekoracyjne i pozostałe
        "HANGING_ROOTS",
        "SMALL_DRIPLEAF",
        "BIG_DRIPLEAF",
        "BIG_DRIPLEAF_STEM",
        "DEAD_BUSH",
        "SPORE_BLOSSOM",
        "FLOWER_POT",
        "LILY_PAD",

        // Nowe rośliny netherowe (1.20+)
        "CRIMSON_FUNGI",
        "WARPED_FUNGI",
        "CRIMSON_ROOTS",
        "WARPED_ROOTS",

        // Nowości od 1.21
        "PITCHER_PLANT",
        "PITCHER_CROP",
        "TORCHFLOWER",
        "TORCHFLOWER_CROP",
        "PITCHER_PLANT",
        "PITCHER_CROP"
    )

    private val utilityBlockNames = listOf(
        // piece i ich warianty
        "FURNACE",
        "BLAST_FURNACE",
        "SMOKER",
        "CAMPFIRE",
        "SOUL_CAMPFIRE",

        // stoliki i warsztaty
        "CRAFTING_TABLE",
        "LOOM",
        "STONECUTTER",
        "SMITHING_TABLE",
        "GRINDSTONE",
        "ENCHANTING_TABLE",
        "BREWING_STAND",
        "ANVIL",
        "CHIPPED_ANVIL",
        "DAMAGED_ANVIL",

        // inne interaktywne
        "CAULDRON",
        "LECTERN",
        "BARREL",
        "COMPOSTER",
        "SMITHING_TABLE",
        "CARTOGRAPHY_TABLE",
        "FLETCHING_TABLE",
        "LOOM",
        "STONECUTTER",
        "CHISELED_BOOKSHELF",
        "JUKEBOX"
    )

    private val redstoneNames = listOf(
        "REDSTONE_BLOCK",
        "REDSTONE_TORCH",
        "REDSTONE_WALL_TORCH",
        "REDSTONE_LAMP",
        "REPEATER",
        "COMPARATOR",
        "DAYLIGHT_DETECTOR",
        "DAYLIGHT_DETECTOR_INVERTED",
        "NOTE_BLOCK",
        "DISPENSER",
        "DROPPER",
        "HOPPER",
        "TARGET_BLOCK",
        "CRAFTER",
        "OBSERVER"
    )

    private val containerMinecartNames = listOf(
        "CHEST_MINECART", "HOPPER_MINECART", "TNT_MINECART",
        "MINECART", "CHEST_BOAT", "BARREL_BOAT",
        "OAK_BOAT", "WARPED_BOAT", "PALE_OAK_BOAT",
        "SPRUCE_BOAT", "BIRCH_BOAT", "JUNGLE_BOAT",
        "ACACIA_BOAT", "DARK_OAK_BOAT", "MANGROVE_BOAT",
        "CRIMSON_BOAT", "BAMBOO_CHEST_RAFT",
        "CHERRY_BOAT", "BAMBOO_RAFT", "OAK_CHEST_BOAT",
        "SPRUCE_CHEST_BOAT", "BIRCH_CHEST_BOAT", "JUNGLE_CHEST_BOAT",
        "ACACIA_CHEST_BOAT", "DARK_OAK_CHEST_BOAT", "MANGROVE_CHEST_BOAT",
        "CHERRY_CHEST_BOAT", "BAMBOO_CHEST_BOAT", "CRIMSON_CHEST_BOAT"
    )

    // ----- Bezpieczne funkcje -----
    fun safeEntityType(name: String): EntityType? =
        try { EntityType.valueOf(name) } catch (_: IllegalArgumentException) { null }

    fun safeMaterial(name: String): Material? = Material.matchMaterial(name)

    // ----- Mapowania w zbiory -----
    fun loadAggressiveMobs(): Set<EntityType> =
        aggressiveMobNames.mapNotNull(::safeEntityType).toSet()

    fun loadPassiveMobs(): Set<EntityType> =
        passiveMobNames.mapNotNull(::safeEntityType).toSet()

    fun loadDoorsAndGates(): Set<Material> = (
            doorNames + trapdoorNames + fenceGateNames
            ).mapNotNull(::safeMaterial).toSet()

    fun loadButtonsAndLevers(): Set<Material> =
        buttonNames.mapNotNull(::safeMaterial).toSet()

    fun loadContainers(): Set<Material> = (
            containerNames + Material.entries.map { it.name }.filter { it.endsWith("_SHULKER_BOX") }
            ).mapNotNull(::safeMaterial).toSet()

    fun loadEnderChest(): Set<Material> =
        enderChestNames.mapNotNull(::safeMaterial).toSet()

    fun loadDispenserBucketMaterials(): Set<Material> =
        dispenserBucketNames.mapNotNull(::safeMaterial).toSet()

    fun loadDamageableByFlow(): Set<Material> =
        damageableByFlowNames.mapNotNull(::safeMaterial).toSet()

    fun loadUtilityBlocks(): Set<Material> =
        utilityBlockNames.mapNotNull(::safeMaterial).toSet()

    fun loadRedstoneBlocks(): Set<Material> =
        redstoneNames.mapNotNull(::safeMaterial).toSet()

    fun loadContainerEntities(): Set<EntityType> =
        containerMinecartNames.mapNotNull(::safeEntityType).toSet()
}
