package pl.syntaxdevteam.plotsx.common

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.Reader

/** Unlike loadConfiguration, strict load throws on malformed YAML instead of returning an empty document. */
internal object YamlFiles {
    fun read(file: File): YamlConfiguration = YamlConfiguration().apply { load(file) }

    fun updateMissing(file: File, template: Reader): Boolean {
        val defaults = YamlConfiguration().apply { load(template) }
        val current = read(file)
        val changed = synchronizeSections(defaults, current)
        if (changed) current.save(file)
        return changed
    }

    private fun synchronizeSections(defaults: ConfigurationSection, current: ConfigurationSection): Boolean {
        var updated = false
        for (key in defaults.getKeys(false)) {
            if (!current.contains(key)) {
                current[key] = defaults[key]
                updated = true
            } else if (defaults.isConfigurationSection(key)) {
                require(current.isConfigurationSection(key)) {
                    "${current.currentPath}.$key must be a configuration section"
                }
                updated = synchronizeSections(defaults.getConfigurationSection(key)!!,
                    current.getConfigurationSection(key)!!) || updated
            }
        }
        return updated
    }
}
