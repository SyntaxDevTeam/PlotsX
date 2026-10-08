package pl.syntaxdevteam.plotsx.hooks

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import pl.syntaxdevteam.plotsx.PlotsX
import java.math.BigDecimal

/** Tracks economy services for diagnostics while revalidating the selected provider per purchase. */
class EconomyHook(private val plugin: PlotsX) : Listener {
    @Volatile private var vaultUnlockedProvider: Any? = null
    @Volatile private var vaultProvider: Any? = null
    @Volatile private var lastState: State? = null

    private data class State(val vaultUnlocked: String?, val vault: String?)

    init { refresh(forceLog = true) }

    @EventHandler
    fun onServiceRegister(@Suppress("UNUSED_PARAMETER") event: ServiceRegisterEvent) = refresh()

    @EventHandler
    fun onServiceUnregister(@Suppress("UNUSED_PARAMETER") event: ServiceUnregisterEvent) = refresh()

    @Synchronized
    fun refresh(forceLog: Boolean = false) {
        val unlocked = try {
            plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault2.economy.Economy::class.java)?.provider
        } catch (_: LinkageError) { null }
        val vault = try {
            plugin.server.servicesManager
                .getRegistration(net.milkbowl.vault.economy.Economy::class.java)?.provider
        } catch (_: LinkageError) { null }

        vaultUnlockedProvider = unlocked
        vaultProvider = vault
        val state = State(unlocked?.javaClass?.name, vault?.javaClass?.name)
        if (!forceLog && state == lastState) return
        lastState = state

        when {
            state.vaultUnlocked != null -> plugin.logger.success(
                "Economy connected through VaultUnlocked: ${state.vaultUnlocked}"
            )
            state.vault != null -> plugin.logger.success("Economy connected through Vault: ${state.vault}")
            plugin.server.pluginManager.isPluginEnabled("Vault") ||
                plugin.server.pluginManager.isPluginEnabled("VaultUnlocked") -> plugin.logger.warning(
                "Vault facade detected, but no economy provider is registered. Install or enable an economy plugin."
            )
            else -> plugin.logger.warning(
                "No Vault/VaultUnlocked economy service detected; paid plot expansion is unavailable."
            )
        }
    }

    fun account(player: Player, amount: BigDecimal, pinCurrency: Boolean): ExpansionEconomy.Account? {
        val unlocked = vaultUnlockedProvider
        if (unlocked != null) try {
            val provider = unlocked as net.milkbowl.vault2.economy.Economy
            if (provider.isEnabled) {
                val currency = if (pinCurrency) provider.getDefaultCurrency(plugin.name)
                    .takeIf { it.isNotBlank() && provider.hasCurrency(it) } else null
                if (!pinCurrency || currency != null) return object : ExpansionEconomy.Account {
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
                    "VaultUnlocked economy ${provider.javaClass.name} has no valid default currency; trying Vault."
                )
            }
        } catch (_: LinkageError) {
            refresh()
        } catch (failure: Exception) {
            plugin.logger.warning("VaultUnlocked economy failed; trying Vault: ${failure.message}")
        }

        val classic = vaultProvider
        if (classic != null) try {
            val provider = classic as net.milkbowl.vault.economy.Economy
            if (provider.isEnabled) {
                val doubleAmount = amount.toDouble()
                if (pinCurrency && (!doubleAmount.isFinite() ||
                        BigDecimal.valueOf(doubleAmount).compareTo(amount) != 0)) {
                    plugin.logger.warning("Expansion price $amount cannot be represented safely by the Vault API.")
                    return null
                }
                val offlinePlayer = plugin.server.getOfflinePlayer(player.uniqueId)
                val worldName = player.world.name
                val currency = if (pinCurrency) provider.currencyNameSingular().ifBlank { "default" }
                    else "provider-default"
                return object : ExpansionEconomy.Account {
                    override val providerId = "Vault:${provider.javaClass.name}"
                    override val currencyId = currency
                    override fun withdraw() = provider.withdrawPlayer(offlinePlayer, worldName, doubleAmount).transactionSuccess()
                    override fun refund() = provider.depositPlayer(offlinePlayer, worldName, doubleAmount).transactionSuccess()
                }
            }
        } catch (_: LinkageError) {
            refresh()
        } catch (failure: Exception) {
            plugin.logger.warning("Vault economy failed: ${failure.message}")
        }
        return null
    }

    fun close() {
        vaultUnlockedProvider = null
        vaultProvider = null
        lastState = null
    }
}
