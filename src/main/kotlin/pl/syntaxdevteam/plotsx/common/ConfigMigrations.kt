package pl.syntaxdevteam.plotsx.common

import pl.syntaxdevteam.core.config.BaseConfigMigration
import pl.syntaxdevteam.core.config.ConfigMigration
import pl.syntaxdevteam.core.config.ConfigMigrationContext

/** Historical 101/102 releases changed backend documentation, not configuration paths.
 * 103 adopts the versioned loader while retaining the old additive loader's custom keys.
 * Database backend selections are preserved; migration never silently switches storage.
 */
internal object ConfigMigrations {
    fun steps(): List<ConfigMigration> = listOf(
        documentationStep(100, 101), documentationStep(101, 102),
        object : BaseConfigMigration(102, 103) {
            override fun migrate(context: ConfigMigrationContext) {
                val user = context.userConfig ?: return
                for (key in user.keys) {
                    val path = key.toString()
                    val section = context.readUserSectionMap(path)
                    context.config.set(path, if (section == null) user.get(path)
                        else merge(context.readSectionMap(path).orEmpty(), section))
                }
            }
        }
    )

    // Merge defaults first and raw user settings last, preserving unknown nested fields.
    private fun merge(defaults: Map<String, Any?>, user: Map<String, Any?>): Map<String, Any?> {
        val result = defaults.toMutableMap()
        for ((key, value) in user) {
            val fallback = defaults[key]
            result[key] = if (value is Map<*, *> && fallback is Map<*, *>) {
                merge(stringMap(fallback), stringMap(value))
            } else value
        }
        return result
    }
    private fun stringMap(value: Map<*, *>): Map<String, Any?> =
        value.entries.associate { it.key.toString() to it.value }

    private fun documentationStep(from: Int, to: Int) = object : BaseConfigMigration(from, to) {
        override fun migrate(context: ConfigMigrationContext) = Unit
    }
}
