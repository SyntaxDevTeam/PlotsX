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
            if (provider != null && provider.isEnabled) {
                val currency = if (pinCurrency) provider.getDefaultCurrency(plugin.name)
                    .takeIf { it.isNotBlank() && provider.hasCurrency(it) } else null
                if (!pinCurrency || currency != null) return object : Account {
                    override val providerId = "VaultUnlocked:${provider.javaClass.name}"
                    override val currencyId = currency ?: "provider-default"
                    private val accountId = player.uniqueId
                    private val worldName = player.world.name
                    override fun withdraw() = (if (currency != null)
                        provider.withdraw(plugin.name, accountId, worldName, currency, amount)
                    else provider.withdraw(plugin.name, accountId, amount)).transactionSuccess()
                    override fun refund() = (if (currency != null)
                        provider.deposit(plugin.name, accountId, worldName, currency, amount)
                    else provider.deposit(plugin.name, accountId, amount)).transactionSuccess()
                }
                plugin.logger.warning(
                    "VaultUnlocked economy ${provider.javaClass.name} did not expose a valid default currency; trying Vault."
                )
            }
        } catch (_: LinkageError) { /* VaultUnlocked is optional. */ }
        catch (failure: Exception) {
            plugin.logger.warning("VaultUnlocked economy lookup failed; trying Vault: ${failure.message}")
        }
        try {
            val provider = plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
            if (provider != null && provider.isEnabled) {
                val doubleAmount = amount.toDouble()
                if (pinCurrency && (!doubleAmount.isFinite() || BigDecimal.valueOf(doubleAmount).compareTo(amount) != 0)) {
                    plugin.logger.warning("Expansion price $amount cannot be represented safely by the Vault economy API.")
                    return null
                }
                val currency = if (pinCurrency) provider.currencyNameSingular().ifBlank { "default" }
                    else "provider-default"
                return object : Account {
                    override val currencyId = currency
                    override val providerId = "Vault:${provider.javaClass.name}"
                    override fun withdraw() = provider.withdrawPlayer(player, doubleAmount).transactionSuccess()
                    override fun refund() = provider.depositPlayer(player, doubleAmount).transactionSuccess()
                }
            }
        } catch (_: LinkageError) { /* Vault is optional. */ }
        catch (failure: Exception) {
            plugin.logger.warning("Vault economy lookup failed: ${failure.message}")
        }
        return null
    }
}
