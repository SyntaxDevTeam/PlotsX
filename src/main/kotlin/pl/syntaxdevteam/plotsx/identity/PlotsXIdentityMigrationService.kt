package pl.syntaxdevteam.plotsx.identity

import pl.syntaxdevteam.plotsx.PlotsX
import java.util.UUID

/**
 * Runtime bridge intentionally exposes only JDK types plus this small result DTO. AuthGatewayX can
 * discover it through Bukkit ServicesManager without making PlotsX depend on AuthGatewayX classes.
 */
data class PlotsXIdentityMigrationResult(
    val status: String,
    val reasonCode: String,
    val legacyEvidence: Boolean,
)

class PlotsXIdentityMigrationService(private val plugin: PlotsX) {
    fun inspect(migrationId: UUID, sourceUuid: UUID, targetUuid: UUID): PlotsXIdentityMigrationResult =
        plugin.databaseHandler.inspectIdentityMigration(migrationId, sourceUuid, targetUuid).external()

    fun migrate(migrationId: UUID, sourceUuid: UUID, targetUuid: UUID): PlotsXIdentityMigrationResult =
        plugin.databaseHandler.migrateIdentity(migrationId, sourceUuid, targetUuid).external()

    fun rollback(migrationId: UUID, sourceUuid: UUID, targetUuid: UUID): PlotsXIdentityMigrationResult =
        plugin.databaseHandler.rollbackIdentityMigration(migrationId, sourceUuid, targetUuid).external()

    private fun IdentityMigrationBridgeResult.external() = PlotsXIdentityMigrationResult(
        status.name,
        reasonCode,
        legacyEvidence,
    )
}
