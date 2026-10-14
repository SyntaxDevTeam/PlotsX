package pl.syntaxdevteam.plotsx.common

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import pl.syntaxdevteam.plotsx.PlotsX
import java.io.File

class ConfigHandler(private val plugin: PlotsX) {

    private val bundledLanguages = listOf("pl", "en")

    fun verifyAndUpdateConfig() {
        plugin.configManager.load()
        // PlotsX services consume Bukkit's view, not the separate BoostedYAML document.
        plugin.reloadConfig()
        plugin.pluginConfig = plugin.config
    }

    /**
     * Uzupełnia wszystkie dostarczane przez plugin pliki językowe, a nie tylko język
     * wybrany w config.yml. MessageHandler synchronizuje wyłącznie aktywny plik, więc
     * po zmianie języka stary plik mógł nie zawierać wiadomości dodanych w aktualizacji.
     */
    fun verifyAndUpdateLanguageFiles() {
        bundledLanguages.forEach { language ->
            val resourcePath = "lang/messages_$language.yml"
            val messageFile = File(plugin.dataFolder, resourcePath)
            val defaultStream = plugin.getResource(resourcePath)

            if (defaultStream == null) {
                plugin.logger.err("Default $resourcePath file not found in plugin resources!")
                return@forEach
            }

            val defaultMessages = defaultStream.reader().use { reader ->
                YamlConfiguration.loadConfiguration(reader)
            }
            if (!messageFile.exists()) {
                messageFile.parentFile.mkdirs()
                plugin.saveResource(resourcePath, false)
                return@forEach
            }
            val currentMessages = YamlConfiguration.loadConfiguration(messageFile)

            if (synchronizeSections(defaultMessages, currentMessages)) {
                plugin.logger.success("Updating $messageFile file with missing entries.")
                currentMessages.save(messageFile)
            }
        }
    }

    private fun synchronizeSections(
        defaultSection: ConfigurationSection,
        currentSection: ConfigurationSection
    ): Boolean {
        var updated = false
        for (key in defaultSection.getKeys(false)) {
            if (!currentSection.contains(key)) {
                currentSection[key] = defaultSection[key]
                updated = true
            } else if (defaultSection.isConfigurationSection(key)) {
                require(currentSection.isConfigurationSection(key)) {
                    "${currentSection.currentPath}.$key must be a configuration section"
                }
                updated = synchronizeSections(
                    defaultSection.getConfigurationSection(key)!!,
                    currentSection.getConfigurationSection(key)!!
                ) || updated
            }
        }
        return updated
    }
}
