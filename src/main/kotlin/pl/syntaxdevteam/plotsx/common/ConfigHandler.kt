package pl.syntaxdevteam.plotsx.common

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import pl.syntaxdevteam.plotsx.PlotsX
import java.io.File

class ConfigHandler(private val plugin: PlotsX) {

    private val bundledLanguages = listOf("pl", "en")

    fun verifyAndUpdateConfig() {
        val confFile = File(plugin.dataFolder, "config.yml")
        val defaultConfStream = plugin.getResource("config.yml")

        if (defaultConfStream == null) {
            plugin.logger.err("Default $confFile file not found in plugin resources!")
            return
        }

        val defaultConfig = YamlConfiguration.loadConfiguration(defaultConfStream.reader())
        val currentConfig = YamlConfiguration.loadConfiguration(confFile)

        if (synchronizeSections(defaultConfig, currentConfig)) {
            plugin.logger.success("Updating $confFile file with missing entries.")
            currentConfig.save(confFile)
        }
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

            if (!messageFile.exists()) {
                messageFile.parentFile.mkdirs()
                plugin.saveResource(resourcePath, false)
                return@forEach
            }

            val defaultMessages = defaultStream.reader().use { reader ->
                YamlConfiguration.loadConfiguration(reader)
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
