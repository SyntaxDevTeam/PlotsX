package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalFlagRegistryTest {
    @Test
    fun `animal protections are exposed as three independent flags`() {
        val keys = PlotFlagRegistry.visibleFlags.map { it.name }.toSet()

        assertTrue("animal-leash should be visible", "animal-leash" in keys)
        assertTrue("animal-ride should be visible", "animal-ride" in keys)
        assertTrue("animal-breed should be visible", "animal-breed" in keys)
        assertFalse("legacy animal-interact must stay hidden", "animal-interact" in keys)

        listOf("animal-leash", "animal-ride", "animal-breed").forEach { key ->
            val flag = PlotFlagRegistry.allFlags[key]
            assertNotNull("missing $key", flag)
            assertFalse("$key should deny visitors by default", flag!!.defaultValue)
            assertTrue("$key should use whitelist semantics", flag.type == FlagType.WHITELIST)
        }

        val legacy = PlotFlagRegistry.allFlags["animal-interact"]
        assertNotNull("legacy compatibility flag missing", legacy)
        assertTrue("legacy flag should be neutral after migration", legacy!!.defaultValue)
        assertFalse("legacy flag should not be visible", legacy.visibleInGui)
    }
}
