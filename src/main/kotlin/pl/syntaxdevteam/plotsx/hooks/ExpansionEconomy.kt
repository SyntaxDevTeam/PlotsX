package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.entity.Player
import pl.syntaxdevteam.plotsx.PlotsX
import java.math.BigDecimal

/** Optional APIs are resolved lazily; a receipt retains the provider used for withdrawal. */
object ExpansionEconomy {
    interface Account {
        fun withdraw(): Boolean
        fun refund(): Boolean
    }

    fun price(plugin: PlotsX): BigDecimal? = plugin.config
        .getString("plots.expansion.price", "0")?.toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 && it.toDouble().isFinite() }

    fun account(plugin: PlotsX, player: Player, amount: BigDecimal): Account? {
        try {
            val provider = plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault2.economy.Economy::class.java)?.provider
            if (provider != null && provider.isEnabled) return object : Account {
                override fun withdraw() = provider.withdraw(plugin.name, player.uniqueId, amount).transactionSuccess()
                override fun refund() = provider.deposit(plugin.name, player.uniqueId, amount).transactionSuccess()
            }
        } catch (_: LinkageError) { /* VaultUnlocked is optional. */ }
        try {
            val provider = plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
            if (provider != null && provider.isEnabled) return object : Account {
                override fun withdraw() = provider.withdrawPlayer(player, amount.toDouble()).transactionSuccess()
                override fun refund() = provider.depositPlayer(player, amount.toDouble()).transactionSuccess()
            }
        } catch (_: LinkageError) { /* Vault is optional. */ }
        return null
    }
}
