package pl.syntaxdevteam.plotsx.common

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class YamlFilesTest {
    private val template = "messages:\n  greeting: default\n  farewell: bye\nflags:\n  enabled: true\n"
    @Test fun `language completion preserves customized values and adds only missing entries`() {
        val folder = Files.createTempDirectory("plotsx-language")
        try {
            val file = folder.resolve("messages_pl.yml").toFile()
            file.writeText("messages:\n  greeting: własna wiadomość\nflags:\n  enabled: false\nextra: keep\n")
            assertTrue(YamlFiles.updateMissing(file, template.reader()))
            val config = YamlFiles.read(file)
            assertEquals("własna wiadomość", config.getString("messages.greeting"))
            assertEquals("bye", config.getString("messages.farewell"))
            assertFalse(config.getBoolean("flags.enabled"))
            assertEquals("keep", config.getString("extra"))
            val before = file.readBytes()
            assertFalse(YamlFiles.updateMissing(file, template.reader()))
            assertArrayEquals(before, file.readBytes())
        } finally { folder.toFile().deleteRecursively() }
    }
    @Test fun `malformed language or reload candidate is never converted into defaults`() {
        val folder = Files.createTempDirectory("plotsx-bad-yaml")
        try {
            for (invalid in listOf("messages: [unterminated\n", "messages:\n\tgreeting: tab\n")) {
                val file = folder.resolve("config.yml").toFile()
                file.writeText(invalid)
                val before = file.readBytes()
                assertThrows(org.bukkit.configuration.InvalidConfigurationException::class.java) { YamlFiles.read(file) }
                assertThrows(org.bukkit.configuration.InvalidConfigurationException::class.java) {
                    YamlFiles.updateMissing(file, template.reader())
                }
                assertArrayEquals(before, file.readBytes())
            }
        } finally { folder.toFile().deleteRecursively() }
    }
    @Test fun `invalid language template and incompatible section leave original intact`() {
        val folder = Files.createTempDirectory("plotsx-template")
        try {
            val file = folder.resolve("messages_en.yml").toFile()
            file.writeText("messages: customized-scalar\n")
            val before = file.readBytes()
            assertThrows(IllegalArgumentException::class.java) { YamlFiles.updateMissing(file, template.reader()) }
            assertArrayEquals(before, file.readBytes())
            assertThrows(org.bukkit.configuration.InvalidConfigurationException::class.java) {
                YamlFiles.updateMissing(file, "messages: [bad\n".reader())
            }
            assertArrayEquals(before, file.readBytes())
        } finally { folder.toFile().deleteRecursively() }
    }
}
