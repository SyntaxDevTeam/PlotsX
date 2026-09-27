package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffectedPlotsPolicyTest {
    @Test fun `action outside plots is unrestricted`() {
        assertTrue(AffectedPlotsPolicy.isAllowed(emptyList()) { false })
    }

    @Test fun `all affected plots must allow environmental action`() {
        assertTrue(AffectedPlotsPolicy.isAllowed(listOf(4, 4)) { it == 4 })
        assertFalse(AffectedPlotsPolicy.isAllowed(listOf(4, 5)) { it == 4 })
    }
}
