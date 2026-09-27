package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PistonMovementPolicyTest {
    @Test fun `wilderness piston remains unrestricted`() {
        assertTrue(PistonMovementPolicy.isAllowed(setOf(null)) { false })
    }

    @Test fun `piston wholly inside one plot follows its flag`() {
        assertTrue(PistonMovementPolicy.isAllowed(setOf(7)) { it == 7 })
        assertFalse(PistonMovementPolicy.isAllowed(setOf(7)) { false })
    }

    @Test fun `piston cannot cross any plot boundary even with flag enabled`() {
        assertFalse(PistonMovementPolicy.isAllowed(setOf(null, 7)) { true })
        assertFalse(PistonMovementPolicy.isAllowed(setOf(7, 8)) { true })
    }
}
