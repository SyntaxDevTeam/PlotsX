package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import java.math.BigDecimal

/** Pricing facade; provider lifecycle and account creation are owned by [EconomyHook]. */
object ExpansionEconomy {
    interface Account {
        val providerId: String get() = "unspecified"
        val currencyId: String get() = "provider-default"
        fun withdraw(): Boolean
        fun refund(): Boolean
    }

    fun price(plugin: PlotsX, purchasedSegments: Int): BigDecimal? = ExpansionPricing.calculate(
        plugin.config.getString("plots.expansion.price", "500.0"),
        plugin.config.getString("plots.expansion.priceMultiplier", "1.5"),
        purchasedSegments
    )

    fun chunkPrice(plugin: PlotsX, level: Int): BigDecimal? = ExpansionPricing.calculate(
        plugin.config.getString("plots.chunks.expansion.price", "500.0"),
        plugin.config.getString("plots.chunks.expansion.priceMultiplier", "1.5"), level
    )

    fun account(plugin: PlotsX, player: Player, amount: BigDecimal, pinCurrency: Boolean = false): Account? {
        return plugin.economyHook.account(player, amount, pinCurrency)
    }
}
