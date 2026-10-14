package pl.syntaxdevteam.plotsx.common

import dev.dejvokep.boostedyaml.YamlDocument
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.core.logging.DebugLevel
import pl.syntaxdevteam.core.logging.Logger
import pl.syntaxdevteam.core.plugin.PluginMetaProvider
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** Consumer contract for our actual template/steps against the resolved SyntaxCore loader.
 * The public facade requires a live JavaPlugin. Reflect only the file engine in tests
 * to exercise the identical flow without starting a server or adding a mocking framework.
 */
class ConfigMigrationsTest {
    private val logger = Logger(object : PluginMetaProvider {
        override val name = "test"
        override val version = "1"
        override val debugLevel = DebugLevel.OFF
    })
    private fun load(file: File): YamlDocument {
        val type = Class.forName("pl.syntaxdevteam.core.config.ConfigFileLoader")
        val template: () -> InputStream = {
            requireNotNull(javaClass.classLoader.getResourceAsStream("config.yml"))
        }
        val loader = type.declaredConstructors.single().newInstance(
            file, template, "config-version", 100, 103, true, logger)
        return type.getMethod("load", List::class.java).invoke(loader, ConfigMigrations.steps()) as YamlDocument
    }
    private fun fixture(action: (File) -> Unit) {
        val folder = Files.createTempDirectory("plotsx-config")
        try { action(folder.resolve("config.yml").toFile()) }
        finally { folder.toFile().deleteRecursively() }
    }

    @Test fun `all historical schemas keep custom values and create original backup`() {
        for (version in listOf<Int?>(null, 100, 101, 102)) fixture { file ->
            val original = (version?.let { "config-version: $it\n" } ?: "") +
                "database:\n  type: postgresql\n  sql:\n    password: custom-secret\n" +
                "plots:\n  radius: 23\n  custom-list: [alpha, beta]\n  custom-map:\n    enabled: true\nlanguage: PL\ncustom-extension: keep\n"
            file.writeText(original)
            val config = load(file)
            assertEquals(103, config.getInt("config-version"))
            assertEquals("postgresql", config.getString("database.type"))
            assertEquals("custom-secret", config.getString("database.sql.password"))
            assertEquals(23, config.getInt("plots.radius"))
            assertEquals("PL", config.getString("language"))
            assertEquals("keep", config.getString("custom-extension"))
            assertEquals(listOf("alpha", "beta"), config.getStringList("plots.custom-list"))
            assertTrue(config.getBoolean("plots.custom-map.enabled"))
            assertTrue(config.contains("plots.claiming.mode"))
            assertEquals(original, file.resolveSibling("config.yml.${version ?: 100}.bak").readText())
            val bukkit = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
            assertEquals(103, bukkit.getInt("config-version"))
            assertEquals(23, bukkit.getInt("plots.radius"))
        }
    }

    @Test fun `fresh config uses 103 and current or future files remain untouched`() {
        fixture { file ->
            assertEquals(103, load(file).getInt("config-version"))
            assertFalse(file.resolveSibling("config.yml.100.bak").exists())
        }
        for (version in listOf(103, 104)) fixture { file ->
            val original = "# custom comment\nconfig-version: $version\nplots:\n  radius: 31\n"
            file.writeText(original)
            assertEquals(version, load(file).getInt("config-version"))
            assertEquals(original, file.readText())
        }
    }

    @Test fun `repeated load is stable and malformed YAML never replaces original`() = fixture { file ->
        file.writeText("config-version: 102\nplots:\n  radius: 29\n")
        load(file)
        val migrated = file.readText()
        load(file)
        assertEquals(migrated, file.readText())
        val invalid = "config-version: 102\nplots: [unterminated\n"
        file.writeText(invalid)
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) { load(file) }
        assertEquals(invalid, file.readText())
    }
}
