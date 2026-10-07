package pl.syntaxdevteam.plotsx.hooks

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException

/**
 * PlotsX-owned WorldGuard flags.
 *
 * WorldGuard locks its flag registry during enable, so registration must happen from
 * [pl.syntaxdevteam.plotsx.PlotsX.onLoad]. Keeping the flag instance here also lets a
 * hot-reloaded PlotsX reuse the already registered WorldGuard flag instead of trying
 * to replace it.
 */
object WorldGuardFlags {
    const val CLAIM_FLAG_NAME = "plotsx-claim"

    @Volatile
    var claimFlag: StateFlag? = null
        private set

    /**
     * Registers the flag or reuses an existing compatible registration.
     *
     * `false` intentionally means that an unset flag has no implicit ALLOW value.
     * PlotsX therefore keeps its historical fail-safe behaviour: a WorldGuard region
     * blocks claiming until the effective flag value resolves to ALLOW.
     */
    fun register(): StateFlag {
        val registry = WorldGuard.getInstance().flagRegistry
        registry[CLAIM_FLAG_NAME]?.let { return useExisting(it) }

        val created = StateFlag(CLAIM_FLAG_NAME, false)
        return try {
            registry.register(created)
            created.also { claimFlag = it }
        } catch (_: FlagConflictException) {
            val existing = registry[CLAIM_FLAG_NAME]
                ?: throw IllegalStateException(
                    "WorldGuard reported a conflict for '$CLAIM_FLAG_NAME', but the existing flag is unavailable."
                )
            useExisting(existing)
        }
    }

    private fun useExisting(existing: com.sk89q.worldguard.protection.flags.Flag<*>): StateFlag {
        require(existing is StateFlag) {
            "WorldGuard flag '$CLAIM_FLAG_NAME' is already registered with incompatible type ${existing.javaClass.name}."
        }
        claimFlag = existing
        return existing
    }
}
