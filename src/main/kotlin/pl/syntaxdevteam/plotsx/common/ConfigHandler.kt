package pl.syntaxdevteam.plotsx.common

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

            defaultStream.reader().use { reader ->
                if (!messageFile.exists()) {
                    messageFile.parentFile.mkdirs()
                    plugin.saveResource(resourcePath, false)
                } else if (YamlFiles.updateMissing(messageFile, reader)) {
                    plugin.logger.success("Updating $messageFile file with missing entries.")
                }
            }
        }
    }

}
