package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import java.math.BigDecimal

/** Optional APIs are resolved lazily; a receipt retains the provider used for withdrawal. */
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
        try {
            val provider = plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault2.economy.Economy::class.java)?.provider
            if (provider != null && provider.isEnabled) return object : Account {
                override val providerId = "VaultUnlocked:${provider.javaClass.name}"
                override val currencyId = if (pinCurrency) provider.getDefaultCurrency(plugin.name) else "provider-default"
                private val accountId = player.uniqueId
                private val worldName = player.world.name
                override fun withdraw() = (if (pinCurrency) provider.withdraw(plugin.name, accountId, worldName, currencyId, amount)
                    else provider.withdraw(plugin.name, accountId, amount)).transactionSuccess()
                override fun refund() = (if (pinCurrency) provider.deposit(plugin.name, accountId, worldName, currencyId, amount)
                    else provider.deposit(plugin.name, accountId, amount)).transactionSuccess()
            }
        } catch (_: LinkageError) { /* VaultUnlocked is optional. */ }
        try {
            val provider = plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
            if (pinCurrency && (!amount.toDouble().isFinite() || BigDecimal.valueOf(amount.toDouble()).compareTo(amount) != 0)) return null
            if (provider != null && provider.isEnabled) return object : Account {
                override val currencyId = if (pinCurrency) provider.currencyNameSingular().ifBlank { "default" } else "provider-default"
                override val providerId = "Vault:${provider.javaClass.name}"
                override fun withdraw() = provider.withdrawPlayer(player, amount.toDouble()).transactionSuccess()
                override fun refund() = provider.depositPlayer(player, amount.toDouble()).transactionSuccess()
            }
        } catch (_: LinkageError) { /* Vault is optional. */ }
        return null
    }
}
