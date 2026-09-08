package pl.syntaxdevteam.plotsx.api

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.api.internal.FlagRules
import java.util.UUID

class FlagRulesTest {
    @Test fun `grave prohibition applies to visitors and members`() {
        for (member in listOf(false, true)) {
            assertFalse(FlagRules.allowed(false, true, false, member))
            assertTrue(FlagRules.allowed(true, true, false, member))
        }
    }

    @Test fun `legacy blacklist and whitelist semantics remain explicit`() {
        assertTrue(FlagRules.allowed(false, false, true, false))
        assertFalse(FlagRules.allowed(true, false, true, false))
        assertFalse(FlagRules.allowed(false, true, true, false))
        assertTrue(FlagRules.allowed(false, true, true, true))
        assertTrue(FlagRules.allowed(true, false, true, true))
    }

    @Test fun `custom keys cannot replace builtins or another namespace`() {
        assertTrue(FlagRules.validKey("machines", "machines:use"))
        assertFalse(FlagRules.validKey("machines", "build"))
        assertFalse(FlagRules.validKey("machines", "other:use"))
        assertFalse(FlagRules.validKey("machines", "machines:role_permission.member.invite"))
        assertFalse(FlagRules.validKey("machines", "machines:Use"))
        assertFalse(FlagRules.validKey("machines", "machines:" + "a".repeat(120)))
    }

    @Test fun `plot bounds include edges and avoid coordinate overflow`() {
        val plot = PlotSnapshot(1, UUID.randomUUID(), "world", Int.MAX_VALUE, 64, -10, 16, "Plot", 0)
        assertTrue(plot.contains("WORLD", Int.MAX_VALUE - 16, 6))
        assertFalse(plot.contains("world", Int.MAX_VALUE - 17, 6))
        assertFalse(plot.contains("world", Int.MIN_VALUE, -10))
        assertFalse(plot.contains("nether", Int.MAX_VALUE, -10))
    }
}
