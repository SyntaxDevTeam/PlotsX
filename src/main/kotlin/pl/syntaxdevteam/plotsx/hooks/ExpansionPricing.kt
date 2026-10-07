package pl.syntaxdevteam.plotsx.hooks

import java.math.BigDecimal
import java.math.MathContext

internal object ExpansionPricing {
    /** purchasedSegments excludes the original plot and unsuccessful purchases. */
    fun calculate(
        basePrice: String?,
        multiplier: String?,
        purchasedSegments: Int,
        maxPrice: String? = null
    ): BigDecimal? {
        val base = basePrice?.toBigDecimalOrNull() ?: return null
        val factor = multiplier?.toBigDecimalOrNull() ?: return null
        val cap = if (maxPrice == null) {
            null
        } else {
            val parsed = maxPrice.toBigDecimalOrNull() ?: return null
            if (!parsed.toDouble().isFinite()) return null
            when {
                parsed.compareTo(BigDecimal.valueOf(-1L)) == 0 -> null
                parsed.signum() < 0 -> return null
                else -> parsed
            }
        }

        if (purchasedSegments < 0 || base.signum() < 0 || factor < BigDecimal.ONE ||
            !base.toDouble().isFinite() || !factor.toDouble().isFinite()) return null

        return try {
            val calculated = base.multiply(factor.pow(purchasedSegments, MathContext.DECIMAL128), MathContext.DECIMAL128)
            val capped = if (cap != null && calculated > cap) cap else calculated
            val vaultAmount = capped.toDouble()
            if (!vaultAmount.isFinite() || (capped.signum() > 0 && vaultAmount == 0.0)) return null
            // Quote and persist the same amount Vault can debit/refund. Multiplication
            // can otherwise produce more decimal digits than its Double API supports.
            BigDecimal.valueOf(vaultAmount)
        } catch (_: ArithmeticException) {
            null
        }
    }
}
