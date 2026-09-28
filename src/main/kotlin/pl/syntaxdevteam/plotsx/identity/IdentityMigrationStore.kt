package pl.syntaxdevteam.plotsx.identity

import pl.syntaxdevteam.plotsx.databases.OperationJournal
import java.sql.Connection
import java.util.UUID

internal enum class IdentityMigrationBridgeStatus { READY, NO_DATA, BLOCKED, SUCCESS, FAILURE, ROLLED_BACK }

internal data class IdentityMigrationBridgeResult(
    val status: IdentityMigrationBridgeStatus,
    val reasonCode: String,
    val legacyEvidence: Boolean = false,
)

internal object IdentityMigrationStore {
    private const val PREPARED = "PREPARED"
    private const val COMPLETED = "COMPLETED"
    private const val ROLLED_BACK = "ROLLED_BACK"

    fun schema(): List<String> = listOf(
        """CREATE TABLE IF NOT EXISTS plotsx_identity_migrations (
            migration_id VARCHAR(36) PRIMARY KEY,
            source_uuid VARCHAR(36) NOT NULL,
            target_uuid VARCHAR(36) NOT NULL,
            state VARCHAR(24) NOT NULL
        )""".trimIndent(),
        """CREATE TABLE IF NOT EXISTS plotsx_identity_migration_items (
            migration_id VARCHAR(36) NOT NULL,
            entity_type VARCHAR(24) NOT NULL,
            entity_key VARCHAR(64) NOT NULL,
            payload VARCHAR(255),
            PRIMARY KEY (migration_id, entity_type, entity_key)
        )""".trimIndent(),
        """CREATE TABLE IF NOT EXISTS plotsx_uuid_aliases (
            source_uuid VARCHAR(36) PRIMARY KEY,
            target_uuid VARCHAR(36) NOT NULL,
            migration_id VARCHAR(36) NOT NULL
        )""".trimIndent(),
    )

    fun migrateSchema(connection: Connection) {
        schema().forEach { sql -> connection.createStatement().use { it.execute(sql) } }
        loadAliases(connection)
    }

    fun loadAliases(connection: Connection): Map<UUID, UUID> =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT source_uuid, target_uuid FROM plotsx_uuid_aliases").use { rows ->
                buildMap {
                    while (rows.next()) {
                        val source = UUID.fromString(rows.getString(1))
                        val target = UUID.fromString(rows.getString(2))
                        require(source != target) { "PlotsX identity alias points to itself" }
                        put(source, target)
                    }
                }
            }
        }

    fun inspect(connection: Connection, migrationId: UUID, source: UUID, target: UUID): IdentityMigrationBridgeResult {
        require(source != target)
        val existing = migration(connection, migrationId)
        if (existing != null && (existing.source != source || existing.target != target)) {
            return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_MIGRATION_ID_CONFLICT")
        }
        val alias = alias(connection, source)
        if (alias != null && alias.first != target) {
            return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_ALIAS_CONFLICT", true)
        }
        if (hasMemberCollision(connection, source, target)) {
            return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_MEMBER_COLLISION", true)
        }
        if (hasPendingFinancialOperation(connection, source)) {
            return IdentityMigrationBridgeResult(
                IdentityMigrationBridgeStatus.BLOCKED,
                "PLOTSX_PENDING_FINANCIAL_OPERATION",
                true,
            )
        }

        val ownerCount = count(connection, "SELECT COUNT(*) FROM plots WHERE owner_uuid = ?", source)
        val memberCount = count(connection, "SELECT COUNT(*) FROM plot_members WHERE member_uuid = ?", source)
        val logCount = count(connection, "SELECT COUNT(*) FROM plot_logs WHERE actor_uuid = ?", source)
        val operationCount = if (OperationJournal.exists(connection)) {
            count(connection, "SELECT COUNT(*) FROM plot_operations WHERE owner_uuid = ? OR actor_uuid = ?", source, source)
        } else 0
        val itemEvidence = existing?.let { migrationItemCount(connection, it.id) } ?: 0
        val evidence = ownerCount + memberCount + logCount + operationCount + itemEvidence > 0
        return IdentityMigrationBridgeResult(
            IdentityMigrationBridgeStatus.READY,
            "PLOTSX_OWNER_$ownerCount" +
                "_MEMBER_$memberCount" +
                "_HISTORY_$logCount" +
                "_OPERATIONS_$operationCount",
            evidence,
        )
    }

    fun migrate(connection: Connection, migrationId: UUID, source: UUID, target: UUID): IdentityMigrationBridgeResult {
        require(source != target)
        val isolation = connection.transactionIsolation
        val autoCommit = connection.autoCommit
        try {
            if (connection.metaData.databaseProductName != "SQLite") {
                connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            }
            connection.autoCommit = false
            val existing = migration(connection, migrationId)
            if (existing != null) {
                if (existing.source != source || existing.target != target) {
                    connection.rollback()
                    return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_MIGRATION_ID_CONFLICT")
                }
                if (existing.state == COMPLETED) {
                    connection.commit()
                    return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.SUCCESS, "PLOTSX_ALREADY_MIGRATED")
                }
                connection.prepareStatement("DELETE FROM plotsx_identity_migration_items WHERE migration_id = ?").use {
                    it.setString(1, migrationId.toString())
                    it.executeUpdate()
                }
                connection.prepareStatement("UPDATE plotsx_identity_migrations SET state = ? WHERE migration_id = ?").use {
                    it.setString(1, PREPARED)
                    it.setString(2, migrationId.toString())
                    check(it.executeUpdate() == 1)
                }
            } else {
                connection.prepareStatement(
                    "INSERT INTO plotsx_identity_migrations (migration_id, source_uuid, target_uuid, state) VALUES (?, ?, ?, ?)",
                ).use {
                    it.setString(1, migrationId.toString())
                    it.setString(2, source.toString())
                    it.setString(3, target.toString())
                    it.setString(4, PREPARED)
                    it.executeUpdate()
                }
            }

            val check = inspect(connection, migrationId, source, target)
            if (check.status == IdentityMigrationBridgeStatus.BLOCKED) {
                connection.rollback()
                return check
            }

            val plotIds = ids(connection, "SELECT plot_id FROM plots WHERE owner_uuid = ?", source)
            val memberRows = members(connection, source)
            plotIds.forEach { addItem(connection, migrationId, "PLOT_OWNER", it.toString(), null) }
            memberRows.forEach { addItem(connection, migrationId, "PLOT_MEMBER", it.first.toString(), it.second) }

            val currentAlias = alias(connection, source)
            if (currentAlias == null) {
                connection.prepareStatement(
                    "INSERT INTO plotsx_uuid_aliases(source_uuid, target_uuid, migration_id) VALUES (?, ?, ?)",
                ).use {
                    it.setString(1, source.toString())
                    it.setString(2, target.toString())
                    it.setString(3, migrationId.toString())
                    it.executeUpdate()
                }
                addItem(connection, migrationId, "UUID_ALIAS", source.toString(), null)
            } else if (currentAlias.first != target) {
                connection.rollback()
                return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_ALIAS_CONFLICT")
            }

            connection.prepareStatement("UPDATE plots SET owner_uuid = ? WHERE owner_uuid = ?").use {
                it.setString(1, target.toString())
                it.setString(2, source.toString())
                check(it.executeUpdate() == plotIds.size) { "PlotsX plot ownership changed during UUID migration" }
            }
            connection.prepareStatement("UPDATE plot_members SET member_uuid = ? WHERE member_uuid = ?").use {
                it.setString(1, target.toString())
                it.setString(2, source.toString())
                check(it.executeUpdate() == memberRows.size) { "PlotsX membership changed during UUID migration" }
            }
            connection.prepareStatement("UPDATE plotsx_identity_migrations SET state = ? WHERE migration_id = ?").use {
                it.setString(1, COMPLETED)
                it.setString(2, migrationId.toString())
                check(it.executeUpdate() == 1)
            }
            connection.commit()
            return IdentityMigrationBridgeResult(
                IdentityMigrationBridgeStatus.SUCCESS,
                "PLOTSX_MIGRATED_OWNER_${plotIds.size}_MEMBER_${memberRows.size}",
                check.legacyEvidence,
            )
        } catch (failure: Throwable) {
            runCatching { connection.rollback() }
            throw failure
        } finally {
            connection.autoCommit = autoCommit
            if (connection.transactionIsolation != isolation) connection.transactionIsolation = isolation
        }
    }

    fun rollback(connection: Connection, migrationId: UUID, source: UUID, target: UUID): IdentityMigrationBridgeResult {
        val isolation = connection.transactionIsolation
        val autoCommit = connection.autoCommit
        try {
            if (connection.metaData.databaseProductName != "SQLite") {
                connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            }
            connection.autoCommit = false
            val row = migration(connection, migrationId)
                ?: run {
                    connection.rollback()
                    return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.NO_DATA, "PLOTSX_NO_MIGRATION")
                }
            if (row.source != source || row.target != target) {
                connection.rollback()
                return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.BLOCKED, "PLOTSX_MIGRATION_ID_CONFLICT")
            }
            if (row.state == ROLLED_BACK) {
                connection.commit()
                return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.ROLLED_BACK, "PLOTSX_ALREADY_ROLLED_BACK")
            }

            val items = items(connection, migrationId)
            for (item in items.filter { it.type == "PLOT_OWNER" }) {
                val plotId = item.key.toInt()
                val changed = connection.prepareStatement(
                    "UPDATE plots SET owner_uuid = ? WHERE plot_id = ? AND owner_uuid = ?",
                ).use {
                    it.setString(1, source.toString())
                    it.setInt(2, plotId)
                    it.setString(3, target.toString())
                    it.executeUpdate()
                }
                check(changed == 1) { "PlotsX cannot rollback owner of plot $plotId because it changed after migration" }
            }

            for (item in items.filter { it.type == "PLOT_MEMBER" }) {
                val plotId = item.key.toInt()
                val sourceExists = connection.prepareStatement(
                    "SELECT 1 FROM plot_members WHERE plot_id = ? AND member_uuid = ?",
                ).use {
                    it.setInt(1, plotId)
                    it.setString(2, source.toString())
                    it.executeQuery().use { rows -> rows.next() }
                }
                check(!sourceExists) { "PlotsX cannot rollback member $plotId because legacy membership already exists" }
                val changed = connection.prepareStatement(
                    "UPDATE plot_members SET member_uuid = ?, role = ? WHERE plot_id = ? AND member_uuid = ?",
                ).use {
                    it.setString(1, source.toString())
                    it.setString(2, requireNotNull(item.payload))
                    it.setInt(3, plotId)
                    it.setString(4, target.toString())
                    it.executeUpdate()
                }
                if (changed == 0) {
                    connection.prepareStatement(
                        "INSERT INTO plot_members(plot_id, member_uuid, role) VALUES (?, ?, ?)",
                    ).use {
                        it.setInt(1, plotId)
                        it.setString(2, source.toString())
                        it.setString(3, requireNotNull(item.payload))
                        it.executeUpdate()
                    }
                }
            }

            if (items.any { it.type == "UUID_ALIAS" }) {
                connection.prepareStatement(
                    "DELETE FROM plotsx_uuid_aliases WHERE source_uuid = ? AND target_uuid = ? AND migration_id = ?",
                ).use {
                    it.setString(1, source.toString())
                    it.setString(2, target.toString())
                    it.setString(3, migrationId.toString())
                    check(it.executeUpdate() == 1) { "PlotsX UUID alias changed after migration" }
                }
            }
            connection.prepareStatement("UPDATE plotsx_identity_migrations SET state = ? WHERE migration_id = ?").use {
                it.setString(1, ROLLED_BACK)
                it.setString(2, migrationId.toString())
                check(it.executeUpdate() == 1)
            }
            connection.commit()
            return IdentityMigrationBridgeResult(IdentityMigrationBridgeStatus.ROLLED_BACK, "PLOTSX_ROLLBACK_COMPLETED")
        } catch (failure: Throwable) {
            runCatching { connection.rollback() }
            throw failure
        } finally {
            connection.autoCommit = autoCommit
            if (connection.transactionIsolation != isolation) connection.transactionIsolation = isolation
        }
    }

    private fun hasMemberCollision(connection: Connection, source: UUID, target: UUID): Boolean =
        connection.prepareStatement(
            """SELECT 1 FROM plot_members s JOIN plot_members t ON t.plot_id = s.plot_id
               WHERE s.member_uuid = ? AND t.member_uuid = ?""".trimIndent(),
        ).use {
            it.setString(1, source.toString())
            it.setString(2, target.toString())
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun hasPendingFinancialOperation(connection: Connection, source: UUID): Boolean {
        if (!OperationJournal.exists(connection)) return false
        return connection.prepareStatement(
            """SELECT 1 FROM plot_operations
               WHERE (owner_uuid = ? OR actor_uuid = ?)
                 AND state NOT IN ('LAND_COMMITTED', 'REFUNDED', 'DECLINED', 'CANCELLED')""".trimIndent(),
        ).use {
            it.setString(1, source.toString())
            it.setString(2, source.toString())
            it.executeQuery().use { rows -> rows.next() }
        }
    }

    private fun count(connection: Connection, sql: String, vararg values: UUID): Int =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setString(index + 1, value.toString()) }
            statement.executeQuery().use { rows -> check(rows.next()); rows.getInt(1) }
        }

    private fun ids(connection: Connection, sql: String, value: UUID): List<Int> =
        connection.prepareStatement(sql).use {
            it.setString(1, value.toString())
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getInt(1)) } }
        }

    private fun members(connection: Connection, source: UUID): List<Pair<Int, String>> =
        connection.prepareStatement("SELECT plot_id, role FROM plot_members WHERE member_uuid = ? ORDER BY plot_id").use {
            it.setString(1, source.toString())
            it.executeQuery().use { rows -> buildList {
                while (rows.next()) add(rows.getInt(1) to rows.getString(2))
            } }
        }

    private fun addItem(connection: Connection, migrationId: UUID, type: String, key: String, payload: String?) {
        connection.prepareStatement(
            "INSERT INTO plotsx_identity_migration_items (migration_id, entity_type, entity_key, payload) VALUES (?, ?, ?, ?)",
        ).use {
            it.setString(1, migrationId.toString())
            it.setString(2, type)
            it.setString(3, key)
            it.setString(4, payload)
            it.executeUpdate()
        }
    }

    private fun migrationItemCount(connection: Connection, migrationId: UUID): Int =
        connection.prepareStatement(
            "SELECT COUNT(*) FROM plotsx_identity_migration_items WHERE migration_id = ? AND entity_type <> 'UUID_ALIAS'",
        ).use {
            it.setString(1, migrationId.toString())
            it.executeQuery().use { rows -> check(rows.next()); rows.getInt(1) }
        }

    private fun migration(connection: Connection, migrationId: UUID): MigrationRow? =
        connection.prepareStatement(
            "SELECT source_uuid, target_uuid, state FROM plotsx_identity_migrations WHERE migration_id = ?",
        ).use {
            it.setString(1, migrationId.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) null else MigrationRow(
                    migrationId,
                    UUID.fromString(rows.getString(1)),
                    UUID.fromString(rows.getString(2)),
                    rows.getString(3),
                )
            }
        }

    private fun alias(connection: Connection, source: UUID): Pair<UUID, UUID>? =
        connection.prepareStatement(
            "SELECT target_uuid, migration_id FROM plotsx_uuid_aliases WHERE source_uuid = ?",
        ).use {
            it.setString(1, source.toString())
            it.executeQuery().use { rows ->
                if (!rows.next()) null else UUID.fromString(rows.getString(1)) to UUID.fromString(rows.getString(2))
            }
        }

    private fun items(connection: Connection, migrationId: UUID): List<Item> =
        connection.prepareStatement(
            "SELECT entity_type, entity_key, payload FROM plotsx_identity_migration_items WHERE migration_id = ? ORDER BY entity_type, entity_key",
        ).use {
            it.setString(1, migrationId.toString())
            it.executeQuery().use { rows -> buildList {
                while (rows.next()) add(Item(rows.getString(1), rows.getString(2), rows.getString(3)))
            } }
        }

    private data class MigrationRow(val id: UUID, val source: UUID, val target: UUID, val state: String)
    private data class Item(val type: String, val key: String, val payload: String?)
}
