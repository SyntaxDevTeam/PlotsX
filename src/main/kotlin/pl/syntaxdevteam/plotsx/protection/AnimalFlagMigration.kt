package pl.syntaxdevteam.plotsx.protection

import pl.syntaxdevteam.plotsx.PlotsX

/**
 * Migrates the former broad `animal-interact` flag to the dedicated animal flags.
 *
 * The migration is intentionally idempotent:
 * - existing values of the new flags are never overwritten,
 * - the legacy value is copied only when a dedicated value is missing,
 * - legacy role grants are copied using the same rule,
 * - `animal-interact` is then forced to true so the old broad handlers stay neutral
 *   until they can be removed completely in a later cleanup.
 */
internal object AnimalFlagMigration {
    private const val LEGACY_FLAG = "animal-interact"
    private val splitFlags = listOf("animal-leash", "animal-ride", "animal-breed")
    private val roles = listOf("member", "builder", "manager")

    fun migrate(plugin: PlotsX) {
        var updates = 0

        plugin.databaseHandler.getPlotsFromAllUsers().forEach { plot ->
            val stored = plugin.databaseHandler.getPlotFlags(plot.id).associateBy { it.name }
            val legacyValue = stored[LEGACY_FLAG]?.value?.toBooleanStrictOrNull()

            if (legacyValue != null) {
                splitFlags.forEach { flag ->
                    if (flag !in stored && plugin.databaseHandler.updatePlotFlag(plot.id, flag, legacyValue)) {
                        updates++
                    }
                }

                if (legacyValue != true && plugin.databaseHandler.updatePlotFlag(plot.id, LEGACY_FLAG, true)) {
                    updates++
                }
            }

            roles.forEach { role ->
                val legacyGrant = "role_permission.$role.flag.$LEGACY_FLAG"
                val legacyGrantValue = stored[legacyGrant]?.value?.toBooleanStrictOrNull() ?: return@forEach

                splitFlags.forEach { flag ->
                    val newGrant = "role_permission.$role.flag.$flag"
                    if (newGrant !in stored && plugin.databaseHandler.updatePlotFlag(plot.id, newGrant, legacyGrantValue)) {
                        updates++
                    }
                }
            }
        }

        if (updates > 0) {
            plugin.logger.info("Migrated legacy animal-interact settings to animal-leash, animal-ride and animal-breed ($updates updates).")
        }
    }
}
