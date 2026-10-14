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
    private fun load(
        file: File,
        steps: List<pl.syntaxdevteam.core.config.ConfigMigration> = ConfigMigrations.steps()
    ): YamlDocument {
        val type = Class.forName("pl.syntaxdevteam.core.config.ConfigFileLoader")
        val template: () -> InputStream = {
            requireNotNull(javaClass.classLoader.getResourceAsStream("config.yml"))
        }
        val loader = type.declaredConstructors.single().newInstance(
            file, template, "config-version", 100, 103, true, logger)
        return type.getMethod("load", List::class.java).invoke(loader, steps) as YamlDocument
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
    @Test fun `every customized template leaf survives migration`() = fixture { file ->
        val original = javaClass.classLoader.getResourceAsStream("config.yml")!!.use {
            YamlDocument.create(it)
        }
        val expected = linkedMapOf<String, Any?>()
        fun customize(section: dev.dejvokep.boostedyaml.block.implementation.Section, prefix: String) {
            for (key in section.keys) {
                val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
                if (path == "config-version") continue
                val value = section.get(key.toString())
                if (value is dev.dejvokep.boostedyaml.block.implementation.Section) customize(value, path)
                else {
                    val changed: Any? = when (value) {
                        is Boolean -> !value
                        is Int -> value + 37
                        is Double -> value + 0.37
                        is List<*> -> listOf("custom-A", "custom-B")
                        is String -> "custom-$value"
                        else -> error("Unexpected template type at $path: $value")
                    }
                    original.set(path, changed)
                    expected[path] = changed
                }
            }
        }
        customize(original, "")
        assertTrue("Exercise the whole bundled template", expected.size > 40)
        original.set("config-version", 102)
        original.save(file)
        val migrated = load(file)
        for ((path, value) in expected) assertEquals(path, value, migrated.get(path))
    }

    @Test fun `missing nested fields are added but false zero empty and custom values survive`() = fixture { file ->
        file.writeText("""
            config-version: 102
            language: ''
            plots:
              maxPlots: 0
              world: []
              chunks:
                expansion:
                  price: 0.0
                  standingCommand: false
            update:
              check-for-updates: false
            stats:
              enabled: false
            webhook:
              discord:
                url: ''
            plot-roles:
              manager:
                permissions: []
            privateChests:
              protectOnPlace: true
        """.trimIndent())
        val migrated = load(file)
        assertEquals("", migrated.getString("language"))
        assertEquals(0, migrated.getInt("plots.maxPlots"))
        assertEquals(emptyList<String>(), migrated.getStringList("plots.world"))
        assertEquals(0.0, migrated.getDouble("plots.chunks.expansion.price"), 0.0)
        assertFalse(migrated.getBoolean("plots.chunks.expansion.standingCommand"))
        assertFalse(migrated.getBoolean("update.check-for-updates"))
        assertFalse(migrated.getBoolean("stats.enabled"))
        assertEquals("", migrated.getString("webhook.discord.url"))
        assertEquals(emptyList<String>(), migrated.getStringList("plot-roles.manager.permissions"))
        assertTrue(migrated.getBoolean("privateChests.protectOnPlace"))
        for (path in listOf("plots.chunks.expansion.priceMultiplier", "plots.chunks.maxTotalOwned",
            "plots.radius", "database.sql.host", "webhook.discord.enabled", "update.github")) {
            assertTrue("Missing default supplied at $path", migrated.contains(path))
        }
    }

    @Test fun `backup precedes callbacks and retries preserve each exact original`() = fixture { file ->
        val attempts = listOf("first", "second", "third")
        for (marker in attempts) {
            val original = "# $marker\nconfig-version: 102\nlanguage: PL\n"
            file.writeText(original)
            val failing = object : pl.syntaxdevteam.core.config.BaseConfigMigration(102, 103) {
                override fun migrate(context: pl.syntaxdevteam.core.config.ConfigMigrationContext) {
                    val backups = file.parentFile.listFiles()!!.filter { it.name.endsWith(".bak") }
                    assertTrue("Exact backup exists before migration callback", backups.any { it.readText() == original })
                    context.config.set("language", "overwrite-attempt")
                    error("simulated migration failure")
                }
            }
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) { load(file, listOf(failing)) }
            assertEquals(original, file.readText())
            assertFalse(file.parentFile.listFiles()!!.any { it.name.startsWith(".syntaxcore-config") })
        }
        val backupContents = file.parentFile.listFiles()!!.filter { it.name.endsWith(".bak") }.map { it.readText() }
        assertEquals(attempts.size, backupContents.size)
        for (marker in attempts) assertTrue(backupContents.any { it.startsWith("# $marker\n") })
        load(file)
        assertEquals(4, file.parentFile.listFiles()!!.count { it.name.endsWith(".bak") })
    }

    @Test fun `existing backup directory is preserved and unique backup protects original`() = fixture { file ->
        val original = "config-version: 102\nlanguage: PL\n"
        file.writeText(original)
        // Backup path cannot be used: occupy it with a nonempty directory.
        val blocked = file.resolveSibling("config.yml.102.bak")
        blocked.mkdirs()
        blocked.resolve("occupied").writeText("keep")
        // Existing named backup gets a unique sibling instead, so this must remain intact.
        load(file)
        assertEquals("keep", blocked.resolve("occupied").readText())
        assertTrue(file.parentFile.listFiles()!!.any { it.isFile && it.name.endsWith(".bak") && it.readText() == original })
    }

    @Test fun `invalid YAML at every schema never copies defaults or alters original bytes`() {
        val badBodies = listOf("plots: [unterminated\n", "language: \"unterminated\n",
            "plots:\n\tradius: 20\n", "plots: {radius: 20\n")
        for (version in listOf<Int?>(null, 100, 102, 103, 104)) for (body in badBodies) fixture { file ->
            val original = ((version?.let { "config-version: $it\n" } ?: "") + body).toByteArray()
            file.writeBytes(original)
            assertThrows(java.lang.reflect.InvocationTargetException::class.java) { load(file) }
            assertArrayEquals(original, file.readBytes())
            assertEquals(listOf("config.yml"), file.parentFile.listFiles()!!.map { it.name })
        }
    }

    @Test fun `unwritable backup directory stops migration before callbacks`() = fixture { file ->
        val original = "config-version: 102\nlanguage: PL\n".toByteArray()
        file.writeBytes(original)
        val directory = file.parentFile.toPath()
        org.junit.Assume.assumeNotNull(Files.getFileAttributeView(directory,
            java.nio.file.attribute.PosixFileAttributeView::class.java))
        val permissions = Files.getPosixFilePermissions(directory)
        var callbackStarted = false
        val step = object : pl.syntaxdevteam.core.config.BaseConfigMigration(102, 103) {
            override fun migrate(context: pl.syntaxdevteam.core.config.ConfigMigrationContext) {
                callbackStarted = true
                context.config.set("language", "overwrite-attempt")
            }
        }
        try {
            Files.setPosixFilePermissions(directory, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"))
            org.junit.Assume.assumeFalse("Privileged users bypass filesystem permissions", Files.isWritable(directory))
            val failure = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { load(file, listOf(step)) }
            assertTrue(failure.cause is java.io.IOException)
            assertFalse(callbackStarted)
            assertArrayEquals(original, file.readBytes())
            assertEquals(listOf("config.yml"), file.parentFile.listFiles()!!.map { it.name })
        } finally { Files.setPosixFilePermissions(directory, permissions) }
    }

}
