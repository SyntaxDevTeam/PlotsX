package pl.syntaxdevteam.plotsx.identity

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves historical player UUIDs to the currently active identity without touching world
 * chunks. This is used by UUID-bearing block PDC (for example private chests), where eagerly
 * scanning every chunk during an account migration would be unsafe and prohibitively expensive.
 */
class IdentityAliasRegistry {
    private val aliases = ConcurrentHashMap<UUID, UUID>()

    fun replaceAll(values: Map<UUID, UUID>) {
        aliases.clear()
        aliases.putAll(values)
        validateNoCycles()
    }

    fun put(source: UUID, target: UUID) {
        require(source != target) { "Identity alias cannot point to itself" }
        aliases[source] = target
        try {
            validateNoCycles()
        } catch (failure: Throwable) {
            aliases.remove(source, target)
            throw failure
        }
    }

    fun remove(source: UUID, target: UUID) {
        aliases.remove(source, target)
    }

    fun resolve(uuid: UUID): UUID {
        var current = uuid
        val visited = linkedSetOf<UUID>()
        repeat(MAX_DEPTH) {
            if (!visited.add(current)) error("Identity alias cycle detected")
            val next = aliases[current] ?: return current
            current = next
        }
        error("Identity alias chain exceeds $MAX_DEPTH entries")
    }

    fun snapshot(): Map<UUID, UUID> = aliases.toMap()

    private fun validateNoCycles() {
        aliases.keys.forEach(::resolve)
    }

    companion object {
        private const val MAX_DEPTH = 16
    }
}
