package pl.syntaxdevteam.plotsx.compat

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Tag
import org.bukkit.block.data.AnaloguePowerable
import org.bukkit.block.data.Openable
import org.bukkit.block.data.Powerable
import org.bukkit.block.data.type.Switch
import org.bukkit.entity.AbstractVillager
import org.bukkit.entity.Ambient
import org.bukkit.entity.Animals
import org.bukkit.entity.EntityType
import org.bukkit.entity.Enemy
import org.bukkit.entity.Golem
import org.bukkit.entity.Vehicle
import org.bukkit.entity.WaterMob

/**
 * Stabilna fasada klasyfikacji elementów Minecrafta.
 *
 * Reszta pluginu nie zna konkretnego adaptera ani wersji serwera. Nowe warianty
 * bloków i mobów są wykrywane przede wszystkim po interfejsach Bukkit i tagach
 * vanilla, a nazwy pozostają wyłącznie dla małej liczby wyjątków semantycznych.
 */
object PlotCompat {
    private val adapter: PlotPlatformAdapter by lazy {
        PlotPlatformAdapters.forMinecraftVersion(Bukkit.getMinecraftVersion())
    }

    fun loadAggressiveMobs(): Set<EntityType> = adapter.aggressiveMobs()
    fun loadPassiveMobs(): Set<EntityType> = adapter.passiveMobs()
    fun loadDoorsAndGates(): Set<Material> = adapter.doorsAndGates()
    fun loadButtonsAndLevers(): Set<Material> = adapter.buttonsAndLevers()
    fun loadContainers(): Set<Material> = adapter.containers()
    fun loadEnderChest(): Set<Material> = adapter.enderChests()
    fun loadDispenserBucketMaterials(): Set<Material> = adapter.dispenserItems()
    fun loadDamageableByFlow(): Set<Material> = adapter.flowDamageableBlocks()
    fun loadUtilityBlocks(): Set<Material> = adapter.utilityBlocks()
    fun loadRedstoneBlocks(): Set<Material> = adapter.redstoneBlocks()
    fun loadContainerEntities(): Set<EntityType> = adapter.vehicleEntities()
    fun loadContainerSpawner(): Set<Material> = adapter.spawnerBlocks()
    fun loadUnsafeBlocks(): Set<Material> = adapter.unsafeTeleportBlocks()

    fun safeEntityType(name: String): EntityType? = adapter.entityType(name)
    fun safeMaterial(name: String): Material? = adapter.material(name)
}

internal interface PlotPlatformAdapter {
    fun aggressiveMobs(): Set<EntityType>
    fun passiveMobs(): Set<EntityType>
    fun doorsAndGates(): Set<Material>
    fun buttonsAndLevers(): Set<Material>
    fun containers(): Set<Material>
    fun enderChests(): Set<Material>
    fun dispenserItems(): Set<Material>
    fun flowDamageableBlocks(): Set<Material>
    fun utilityBlocks(): Set<Material>
    fun redstoneBlocks(): Set<Material>
    fun vehicleEntities(): Set<EntityType>
    fun spawnerBlocks(): Set<Material>
    fun unsafeTeleportBlocks(): Set<Material>
    fun entityType(name: String): EntityType?
    fun material(name: String): Material?
}

internal object PlotPlatformAdapters {
    fun forMinecraftVersion(version: String): PlotPlatformAdapter {
        val major = version.substringBefore('.').toIntOrNull()
        return if (major != null && major >= 26) Modern26Adapter else Legacy120Adapter
    }
}

/** Adapter dla 1.20.6–1.21.x. */
private object Legacy120Adapter : StructuralBukkitAdapter() {
    override val materialAliases: Map<String, List<String>> = mapOf(
        "SHORT_GRASS" to listOf("SHORT_GRASS", "GRASS")
    )
}

/** Adapter dla nowego numerowania 26.x. Różnice trafiają wyłącznie tutaj. */
private object Modern26Adapter : StructuralBukkitAdapter()

private open class StructuralBukkitAdapter : PlotPlatformAdapter {
    protected open val materialAliases: Map<String, List<String>> = emptyMap()

    // Material.values() nadal zawiera wpisy LEGACY_*. Przekazanie jednego z nich
    // do Bukkit Tag#isTagged inicjalizuje CraftLegacy/DataFixerUpper na głównym
    // wątku serwera, co może zatrzymać tick na wiele sekund.
    @Suppress("DEPRECATION")
    private val allMaterials: List<Material> by lazy {
        Material.values().filterNot(Material::isLegacy)
    }
    private val allEntityTypes: Array<EntityType> by lazy(EntityType::values)

    override fun aggressiveMobs(): Set<EntityType> = entityTypesAssignableTo(Enemy::class.java)

    override fun passiveMobs(): Set<EntityType> = allEntityTypes.filterTo(mutableSetOf()) { type ->
        val entityClass = type.entityClass ?: return@filterTo false
        type.name in PASSIVE_ENTITY_EXCEPTIONS ||
            Animals::class.java.isAssignableFrom(entityClass) ||
            WaterMob::class.java.isAssignableFrom(entityClass) ||
            Ambient::class.java.isAssignableFrom(entityClass) ||
            AbstractVillager::class.java.isAssignableFrom(entityClass) ||
            Golem::class.java.isAssignableFrom(entityClass)
    }

    override fun doorsAndGates(): Set<Material> = buildSet {
        addAll(blockTag("doors"))
        addAll(blockTag("trapdoors"))
        addAll(blockTag("fence_gates"))
        addAll(materialsWithBlockData<Openable>())
    }

    override fun buttonsAndLevers(): Set<Material> = buildSet {
        addAll(blockTag("buttons"))
        addAll(blockTag("pressure_plates"))
        addAll(materialsWithBlockData<Switch>())
    }

    override fun containers(): Set<Material> = allMaterials.filterTo(mutableSetOf()) { material ->
        material.name == "CHEST" ||
            material.name == "TRAPPED_CHEST" ||
            material.name == "BARREL" ||
            material.name.endsWith("_SHULKER_BOX")
    }

    override fun enderChests(): Set<Material> = materials("ENDER_CHEST")

    override fun dispenserItems(): Set<Material> = allMaterials.filterTo(mutableSetOf()) { material ->
        material.name == "BUCKET" ||
            material.name.endsWith("_BUCKET") ||
            material.name == "EGG" ||
            material.name.endsWith("_EGG")
    }

    override fun flowDamageableBlocks(): Set<Material> = buildSet {
        addAll(blockTag("crops"))
        addAll(blockTag("flowers"))
        addAll(blockTag("saplings"))
        addAll(allMaterials.filter { material ->
            material.isBlock && !material.isAir && !material.isSolid && !isLiquid(material)
        })
    }

    override fun utilityBlocks(): Set<Material> = allMaterials.filterTo(mutableSetOf()) { material ->
        val name = material.name
        name.endsWith("FURNACE") ||
            name.endsWith("ANVIL") ||
            name.endsWith("_TABLE") ||
            name in UTILITY_EXCEPTIONS
    }

    override fun redstoneBlocks(): Set<Material> = buildSet {
        addAll(materialsWithBlockData<Powerable>())
        addAll(materialsWithBlockData<AnaloguePowerable>())
        addAll(materialsWithBlockData<Switch>())
        addAll(allMaterials.filter { it.name in REDSTONE_EXCEPTIONS })
    }.minus(doorsAndGates()).minus(buttonsAndLevers())

    override fun vehicleEntities(): Set<EntityType> = allEntityTypes.filterTo(mutableSetOf()) { type ->
        val entityClass = type.entityClass ?: return@filterTo false
        Vehicle::class.java.isAssignableFrom(entityClass)
    }

    override fun spawnerBlocks(): Set<Material> = allMaterials.filterTo(mutableSetOf()) { material ->
        material.name == "SPAWNER" ||
            material.name.endsWith("_SPAWNER") ||
            material.name == "CREAKING_HEART"
    }

    override fun unsafeTeleportBlocks(): Set<Material> = allMaterials.filterTo(mutableSetOf()) { material ->
        material.name in UNSAFE_TELEPORT_EXCEPTIONS
    }

    override fun entityType(name: String): EntityType? =
        allEntityTypes.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun material(name: String): Material? {
        val candidates = materialAliases[name.uppercase()] ?: listOf(name)
        return candidates.firstNotNullOfOrNull(Material::matchMaterial)
    }

    private fun entityTypesAssignableTo(parent: Class<*>): Set<EntityType> =
        allEntityTypes.filterTo(mutableSetOf()) { type ->
            type.entityClass?.let(parent::isAssignableFrom) ?: false
        }

    private inline fun <reified T> materialsWithBlockData(): Set<Material> =
        allMaterials.filterTo(mutableSetOf()) { material ->
            if (!material.isBlock) return@filterTo false
            runCatching { material.createBlockData() is T }.getOrDefault(false)
        }

    private fun blockTag(name: String): Set<Material> {
        val tag = Bukkit.getTag(
            Tag.REGISTRY_BLOCKS,
            NamespacedKey.minecraft(name),
            Material::class.java
        ) ?: return emptySet()
        return allMaterials.filterTo(mutableSetOf(), tag::isTagged)
    }

    private fun materials(vararg names: String): Set<Material> =
        names.mapNotNull(::material).toSet()

    private fun isLiquid(material: Material): Boolean =
        material.name == "WATER" || material.name == "LAVA"

    private companion object {
        val PASSIVE_ENTITY_EXCEPTIONS = setOf("ALLAY")

        val UTILITY_EXCEPTIONS = setOf(
            "BREWING_STAND", "CAULDRON", "LECTERN", "COMPOSTER",
            "GRINDSTONE", "STONECUTTER", "LOOM", "CAMPFIRE",
            "SOUL_CAMPFIRE", "CHISELED_BOOKSHELF"
        )

        val REDSTONE_EXCEPTIONS = setOf(
            "REDSTONE_BLOCK", "REDSTONE_TORCH", "REDSTONE_WALL_TORCH",
            "REDSTONE_LAMP", "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR",
            "NOTE_BLOCK", "DISPENSER", "DROPPER", "HOPPER", "OBSERVER",
            "TARGET", "TARGET_BLOCK", "CRAFTER"
        )

        val UNSAFE_TELEPORT_EXCEPTIONS = setOf(
            "LAVA", "WATER", "MAGMA_BLOCK", "CACTUS", "FIRE",
            "SOUL_FIRE", "CAMPFIRE", "SOUL_CAMPFIRE", "POWDER_SNOW",
            "VOID_AIR"
        )
    }
}
