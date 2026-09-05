package pl.syntaxdevteam.plotsx.protection

import org.bukkit.NamespacedKey
import org.bukkit.block.Barrel
import org.bukkit.block.Block
import org.bukkit.block.Chest
import org.bukkit.block.Container
import org.bukkit.block.DoubleChest
import org.bukkit.block.ShulkerBox
import org.bukkit.block.TileState
import org.bukkit.persistence.PersistentDataType
import pl.syntaxdevteam.plotsx.PlotsX
import java.util.UUID

/** Stores private-container ownership in the block itself, so it follows normal chunk persistence. */
class PrivateChestManager(plugin: PlotsX) {
    private val ownerKey = NamespacedKey(plugin, "private_chest_owner")
    private val trustedKey = NamespacedKey(plugin, "private_chest_trusted")
    private val plotKey = NamespacedKey(plugin, "private_chest_plot")

    data class Protection(val plotId: Int, val owner: UUID, val trusted: Set<UUID>) {
        fun canAccess(player: UUID): Boolean = player == owner || player in trusted
    }

    fun isSupported(block: Block): Boolean {
        val state = block.state
        return state is Chest || state is Barrel || state is ShulkerBox
    }

    fun getProtection(block: Block, plotId: Int): Protection? =
        containerStates(block)
            .mapNotNull(::readProtection)
            .firstOrNull { it.plotId == plotId }

    fun lock(block: Block, plotId: Int, owner: UUID) {
        write(block, Protection(plotId, owner, emptySet()))
    }

    fun unlock(block: Block) {
        containerStates(block).forEach { state ->
            state.persistentDataContainer.remove(ownerKey)
            state.persistentDataContainer.remove(trustedKey)
            state.persistentDataContainer.remove(plotKey)
            state.update(true, false)
        }
    }

    fun trust(block: Block, protection: Protection, player: UUID) {
        write(block, protection.copy(trusted = protection.trusted + player))
    }

    fun untrust(block: Block, protection: Protection, player: UUID) {
        write(block, protection.copy(trusted = protection.trusted - player))
    }

    fun synchronize(block: Block, protection: Protection) {
        write(block, protection)
    }

    private fun write(block: Block, protection: Protection) {
        val trusted = protection.trusted.joinToString(",")
        containerStates(block).forEach { state ->
            val data = state.persistentDataContainer
            data.set(ownerKey, PersistentDataType.STRING, protection.owner.toString())
            data.set(plotKey, PersistentDataType.INTEGER, protection.plotId)
            if (trusted.isEmpty()) data.remove(trustedKey)
            else data.set(trustedKey, PersistentDataType.STRING, trusted)
            state.update(true, false)
        }
    }

    private fun readProtection(state: TileState): Protection? {
        val data = state.persistentDataContainer
        val owner = data.get(ownerKey, PersistentDataType.STRING)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        } ?: return null
        val plotId = data.get(plotKey, PersistentDataType.INTEGER) ?: return null
        val trusted = data.get(trustedKey, PersistentDataType.STRING)
            .orEmpty()
            .split(',')
            .mapNotNull { value -> runCatching { UUID.fromString(value) }.getOrNull() }
            .toSet()
        return Protection(plotId, owner, trusted)
    }

    private fun containerStates(block: Block): List<TileState> {
        val state = block.state
        if (state !is Container) return emptyList()
        if (state !is Chest) return listOf(state)

        val holder = state.inventory.holder
        if (holder !is DoubleChest) return listOf(state)
        return listOfNotNull(holder.leftSide as? TileState, holder.rightSide as? TileState).distinctBy {
            val location = it.location
            Triple(location.blockX, location.blockY, location.blockZ)
        }
    }
}
