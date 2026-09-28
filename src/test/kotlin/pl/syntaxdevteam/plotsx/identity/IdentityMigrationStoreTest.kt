package pl.syntaxdevteam.plotsx.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.syntaxdevteam.plotsx.databases.DatabaseMigrations
import pl.syntaxdevteam.plotsx.databases.OperationJournal
import pl.syntaxdevteam.plotsx.geometry.ChunkPosition
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class IdentityMigrationStoreTest {
    private val source = UUID.fromString("11111111-1111-3111-8111-111111111111")
    private val target = UUID.fromString("22222222-2222-4222-8222-222222222222")

    @Test
    fun `migration and rollback preserve exact ownership and membership on sqlite and h2`() {
        databases { connection ->
            val plotId = insertPlot(connection, source)
            connection.prepareStatement(
                "INSERT INTO plot_members(plot_id, member_uuid, role) VALUES (?, ?, ?)",
            ).use {
                it.setInt(1, plotId)
                it.setString(2, source.toString())
                it.setString(3, "trusted")
                it.executeUpdate()
            }

            val migrationId = UUID.randomUUID()
            val inspection = IdentityMigrationStore.inspect(connection, migrationId, source, target)
            assertEquals(IdentityMigrationBridgeStatus.READY, inspection.status)
            assertTrue(inspection.legacyEvidence)

            val migrated = IdentityMigrationStore.migrate(connection, migrationId, source, target)
            assertEquals(IdentityMigrationBridgeStatus.SUCCESS, migrated.status)
            assertEquals(target.toString(), scalar(connection, "SELECT owner_uuid FROM plots WHERE plot_id = $plotId"))
            assertEquals(target.toString(), scalar(connection, "SELECT member_uuid FROM plot_members WHERE plot_id = $plotId"))
            assertEquals(target.toString(), scalar(connection, "SELECT target_uuid FROM plotsx_uuid_aliases WHERE source_uuid = '$source'"))

            val repeated = IdentityMigrationStore.migrate(connection, migrationId, source, target)
            assertEquals(IdentityMigrationBridgeStatus.SUCCESS, repeated.status)

            val rolledBack = IdentityMigrationStore.rollback(connection, migrationId, source, target)
            assertEquals(IdentityMigrationBridgeStatus.ROLLED_BACK, rolledBack.status)
            assertEquals(source.toString(), scalar(connection, "SELECT owner_uuid FROM plots WHERE plot_id = $plotId"))
            assertEquals(source.toString(), scalar(connection, "SELECT member_uuid FROM plot_members WHERE plot_id = $plotId"))
            assertEquals("trusted", scalar(connection, "SELECT role FROM plot_members WHERE plot_id = $plotId"))
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM plotsx_uuid_aliases WHERE source_uuid = '$source'"))

            val repeatedRollback = IdentityMigrationStore.rollback(connection, migrationId, source, target)
            assertEquals(IdentityMigrationBridgeStatus.ROLLED_BACK, repeatedRollback.status)
        }
    }

    @Test
    fun `member collision blocks migration without changing rows`() {
        databases { connection ->
            val plotId = insertPlot(connection, UUID.randomUUID())
            for (uuid in listOf(source, target)) {
                connection.prepareStatement(
                    "INSERT INTO plot_members(plot_id, member_uuid, role) VALUES (?, ?, ?)",
                ).use {
                    it.setInt(1, plotId)
                    it.setString(2, uuid.toString())
                    it.setString(3, "member")
                    it.executeUpdate()
                }
            }

            val result = IdentityMigrationStore.inspect(connection, UUID.randomUUID(), source, target)

            assertEquals(IdentityMigrationBridgeStatus.BLOCKED, result.status)
            assertEquals("PLOTSX_MEMBER_COLLISION", result.reasonCode)
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM plot_members WHERE plot_id = $plotId"))
        }
    }

    @Test
    fun `unresolved payment journal blocks identity migration`() {
        databases { connection ->
            val plotId = insertPlot(connection, source)
            val operation = OperationJournal.Operation(
                UUID.randomUUID(),
                plotId,
                source,
                source,
                "world",
                ChunkPosition(0, 0),
                ChunkPosition(1, 0),
                0,
                BigDecimal("10.00"),
                "Vault:Test",
                "default",
                createdAt = 100,
            )
            assertTrue(OperationJournal.prepare(connection, operation))

            val result = IdentityMigrationStore.inspect(connection, UUID.randomUUID(), source, target)

            assertEquals(IdentityMigrationBridgeStatus.BLOCKED, result.status)
            assertEquals("PLOTSX_PENDING_FINANCIAL_OPERATION", result.reasonCode)
            assertTrue(result.legacyEvidence)
        }
    }

    @Test
    fun `alias registry resolves chains and removes only matching alias`() {
        val registry = IdentityAliasRegistry()
        val third = UUID.fromString("33333333-3333-4333-8333-333333333333")
        registry.put(source, target)
        registry.put(target, third)
        assertEquals(third, registry.resolve(source))
        registry.remove(source, third)
        assertEquals(third, registry.resolve(source))
        registry.remove(source, target)
        assertEquals(source, registry.resolve(source))
        assertFalse(registry.snapshot().containsKey(source))
    }

    private fun databases(test: (Connection) -> Unit) {
        for (type in listOf("sqlite", "h2")) {
            val url = if (type == "sqlite") "jdbc:sqlite::memory:" else "jdbc:h2:mem:identity-${UUID.randomUUID()}"
            DriverManager.getConnection(url).use { connection ->
                if (type == "sqlite") {
                    connection.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                }
                DatabaseMigrations.migrate(connection, type)
                OperationJournal.migrate(connection)
                IdentityMigrationStore.migrateSchema(connection)
                test(connection)
            }
        }
    }

    private fun insertPlot(connection: Connection, owner: UUID): Int {
        connection.prepareStatement(
            """INSERT INTO plots
               (owner_uuid, x, z, y, radius, world, name, creation_time, geometry_type, geometry_revision)
               VALUES (?, ?, ?, 64, 8, 'world', 'home', 1, 'classic', 0)""".trimIndent(),
        ).use {
            it.setString(1, owner.toString())
            it.setInt(2, (Math.random() * 100000).toInt())
            it.setInt(3, (Math.random() * 100000).toInt())
            it.executeUpdate()
        }
        return connection.createStatement().use {
            it.executeQuery("SELECT MAX(plot_id) FROM plots").use { rows ->
                assertTrue(rows.next())
                rows.getInt(1)
            }
        }
    }

    private fun scalar(connection: Connection, sql: String): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                assertTrue(rows.next())
                rows.getString(1)
            }
        }

    private fun count(connection: Connection, sql: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                assertTrue(rows.next())
                rows.getInt(1)
            }
        }
}
