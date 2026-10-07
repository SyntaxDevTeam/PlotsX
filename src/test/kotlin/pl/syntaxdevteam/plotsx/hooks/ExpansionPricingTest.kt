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

    @Test fun `maximum price caps later expansions`() {
        listOf("500", "750", "1000", "1000", "1000").forEachIndexed { count, expected ->
            assertEquals(
                0,
                BigDecimal(expected).compareTo(ExpansionPricing.calculate("500", "1.5", count, "1000"))
            )
        }
    }

    @Test fun `disabled maximum price keeps existing pricing behavior`() {
        assertEquals(0, BigDecimal("1687.5").compareTo(ExpansionPricing.calculate("500", "1.5", 3, "-1")))
        assertEquals(0, BigDecimal("1687.5").compareTo(ExpansionPricing.calculate("500", "1.5", 3, "-1.00")))
    }

    @Test fun `maximum price is applied before Vault conversion overflow`() {
        assertEquals(0, BigDecimal("10000").compareTo(ExpansionPricing.calculate("500", "10", 400, "10000")))
    }

    @Test fun `zero maximum price makes expansions free`() {
        assertEquals(0, ExpansionPricing.calculate("500", "1.5", 10, "0")!!.signum())
    }

    @Test fun `unit multiplier and free expansions remain supported`() {
        assertEquals(0, BigDecimal("500").compareTo(ExpansionPricing.calculate("500", "1", 10)))
        assertEquals(0, ExpansionPricing.calculate("0", "1.5", 10)!!.signum())
    }

    @Test fun `later expansions quote the amount representable by Vault`() {
        // Reported failure: 500 * 1.2^16 = 9244.2129447518208.
        val price = ExpansionPricing.calculate("500", "1.2", 16)!!
        assertEquals(0, BigDecimal("9244.21294475182").compareTo(price))
        assertEquals(0, BigDecimal.valueOf(price.toDouble()).compareTo(price))

        for (level in 0..63) {
            val quote = ExpansionPricing.calculate("500", "1.2", level)!!
            assertEquals(0, BigDecimal.valueOf(quote.toDouble()).compareTo(quote))
        }
    }

    @Test fun `positive prices cannot underflow into a free purchase`() {
        assertNull(ExpansionPricing.calculate("1e-400", "1", 0))
    }

    @Test fun `invalid settings and overflowing price reject purchase`() {
        for (factor in listOf(null, "invalid", "NaN", "Infinity", "-1", "0", "0.9")) {
            assertNull(ExpansionPricing.calculate("500", factor, 0))
        }
        for (maximum in listOf("invalid", "NaN", "Infinity", "-2", "-0.01")) {
            assertNull(ExpansionPricing.calculate("500", "1.5", 0, maximum))
        }
        assertNull(ExpansionPricing.calculate("-1", "1.5", 0))
        assertNull(ExpansionPricing.calculate("invalid", "1.5", 0))
        assertNull(ExpansionPricing.calculate("500", "1.5", -1))
        assertNull(ExpansionPricing.calculate("500", "10", 400))
    }
}
