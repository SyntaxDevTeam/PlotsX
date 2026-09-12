package pl.syntaxdevteam.plotsx.hooks

import java.math.BigDecimal
import java.math.MathContext

internal object ExpansionPricing {
    /** purchasedSegments excludes the original plot and unsuccessful purchases. */
    fun calculate(basePrice: String?, multiplier: String?, purchasedSegments: Int): BigDecimal? {
        val base = basePrice?.toBigDecimalOrNull() ?: return null
        val factor = multiplier?.toBigDecimalOrNull() ?: return null
        if (purchasedSegments < 0 || base.signum() < 0 || factor < BigDecimal.ONE ||
            !base.toDouble().isFinite() || !factor.toDouble().isFinite()) return null
        return try {
            base.multiply(factor.pow(purchasedSegments, MathContext.DECIMAL128), MathContext.DECIMAL128)
                .takeIf { it.toDouble().isFinite() }
        } catch (_: ArithmeticException) {
            null
        }
    }
}
