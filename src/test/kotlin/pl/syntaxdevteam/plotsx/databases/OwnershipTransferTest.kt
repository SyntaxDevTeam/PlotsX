package pl.syntaxdevteam.plotsx.databases

import org.junit.Assert.*
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class OwnershipTransferTest {
    private val owner = UUID.randomUUID()
    private val recipient = UUID.randomUUID()

    private fun databases(test: (Connection) -> Unit) {
        listOf("jdbc:sqlite::memory:", "jdbc:h2:mem:${UUID.randomUUID()}").forEach { url ->
            DriverManager.getConnection(url).use { c ->
                DatabaseSchema.statements(if (url.contains("sqlite")) "sqlite" else "h2").forEach { sql ->
                    c.createStatement().use { it.execute(sql) }
                }
                c.prepareStatement("INSERT INTO plots (plot_id, owner_uuid, x, z, y, radius, world, name, creation_time) VALUES (1, ?, 0, 0, 64, 16, 'world', 'Home', 0)").use {
                    it.setString(1, owner.toString()); it.executeUpdate()
                }
                c.prepareStatement("INSERT INTO plot_members VALUES (1, ?, 'manager')").use {
                    it.setString(1, recipient.toString()); it.executeUpdate()
                }
                test(c)
            }
        }
    }

    private fun owner(c: Connection) = c.createStatement().use {
        it.executeQuery("SELECT owner_uuid FROM plots WHERE plot_id = 1").use { rs -> rs.next(); rs.getString(1) }
    }

    @Test fun `transfers owner and demotes previous owner atomically`() = databases { c ->
        assertTrue(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 64, 10000))
        assertEquals(recipient.toString(), owner(c))
        c.createStatement().use { it.executeQuery("SELECT member_uuid, role FROM plot_members").use { rs ->
            assertTrue(rs.next()); assertEquals(owner.toString(), rs.getString(1)); assertEquals("member", rs.getString(2))
            assertFalse(rs.next())
        } }
        assertTrue(c.autoCommit)
    }

    @Test fun `rejects stale owner and recipients outside membership`() = databases { c ->
        assertFalse(OwnershipTransfer.transfer(c, 1, UUID.randomUUID(), recipient, 5, 64, 10000))
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, UUID.randomUUID(), 5, 64, 10000))
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, owner, 5, 64, 10000))
        assertEquals(owner.toString(), owner(c))
    }

    @Test fun `enforces count radius and area limits`() = databases { c ->
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 0, 64, 10000))
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 15, 10000))
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 64, 1088))
        assertEquals(owner.toString(), owner(c))
        assertTrue(OwnershipTransfer.transfer(c, 1, owner, recipient, 1, 16, 1089))
    }

    @Test fun `expanded plot transfer enforces real area and extent`() = databases { c ->
        c.createStatement().use { it.execute("INSERT INTO plot_segments VALUES (1, 33, 0, 16)") }
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 48, 10000))
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 49, 2177))
        assertEquals(owner.toString(), owner(c))
        assertTrue(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 49, 2178))
    }

    @Test fun `rejects duplicate name belonging to recipient`() = databases { c ->
        c.prepareStatement("INSERT INTO plots (plot_id, owner_uuid, x, z, y, radius, world, name, creation_time) VALUES (2, ?, 200, 0, 64, 16, 'world', 'Home', 0)").use {
            it.setString(1, recipient.toString()); it.executeUpdate()
        }
        assertFalse(OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 64, 10000))
        assertEquals(owner.toString(), owner(c))
    }

    @Test fun `database failure rolls back ownership and membership`() = databases { c ->
        c.createStatement().use { it.execute("CREATE TABLE saved_members AS SELECT * FROM plot_members") }
        c.createStatement().use { it.execute("DROP TABLE plot_members") }
        c.createStatement().use { it.execute("CREATE TABLE plot_members (plot_id INTEGER, member_uuid VARCHAR(36), role VARCHAR(255) CHECK (role <> 'member'))") }
        c.createStatement().use { it.execute("INSERT INTO plot_members SELECT * FROM saved_members") }
        try {
            OwnershipTransfer.transfer(c, 1, owner, recipient, 5, 64, 10000)
            fail("Expected a failed insert")
        } catch (_: java.sql.SQLException) { }
        assertEquals(owner.toString(), owner(c))
        c.createStatement().use { it.executeQuery("SELECT member_uuid FROM plot_members").use { rs ->
            assertTrue(rs.next()); assertEquals(recipient.toString(), rs.getString(1))
        } }
    }
}
