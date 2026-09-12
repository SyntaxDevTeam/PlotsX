package pl.syntaxdevteam.plotsx.hooks

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ExpansionPricingTest {
    @Test fun `price increases for every purchased segment`() {
        listOf("500", "750", "1125", "1687.5").forEachIndexed { count, expected ->
            assertEquals(0, BigDecimal(expected).compareTo(ExpansionPricing.calculate("500", "1.5", count)))
        }
    }

    @Test fun `unit multiplier and free expansions remain supported`() {
        assertEquals(0, BigDecimal("500").compareTo(ExpansionPricing.calculate("500", "1", 10)))
        assertEquals(0, ExpansionPricing.calculate("0", "1.5", 10)!!.signum())
    }

    @Test fun `invalid settings and overflowing price reject purchase`() {
        for (factor in listOf(null, "invalid", "NaN", "Infinity", "-1", "0", "0.9")) {
            assertNull(ExpansionPricing.calculate("500", factor, 0))
        }
        assertNull(ExpansionPricing.calculate("-1", "1.5", 0))
        assertNull(ExpansionPricing.calculate("invalid", "1.5", 0))
        assertNull(ExpansionPricing.calculate("500", "1.5", -1))
        assertNull(ExpansionPricing.calculate("500", "10", 400))
    }
}
