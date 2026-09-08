package pl.syntaxdevteam.plotsx.commands

import org.junit.Assert.*
import org.junit.Test

class RoleHierarchyTest {
    @Test fun `delegated kick respects strict hierarchy`() {
        assertTrue(RoleHierarchy.canRemove("manager", "builder"))
        assertTrue(RoleHierarchy.canRemove("builder", "member"))
        assertFalse(RoleHierarchy.canRemove("manager", "manager"))
        assertFalse(RoleHierarchy.canRemove("member", "builder"))
        assertFalse(RoleHierarchy.canRemove("manager", "legacy"))
        assertFalse(RoleHierarchy.canRemove(null, "member"))
        assertFalse(RoleHierarchy.canRemove("manager", null))
    }
}
