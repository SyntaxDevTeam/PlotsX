package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInFlagBehaviorsTest {
    @Test fun `all built-in flags preserve their documented polarity and defaults`() {
        val expected = buildMap {
            add(true, FlagType.WHITELIST,
                "grave-create", "spawn-animals", "animal-interact", "command-use")
            add(false, FlagType.WHITELIST,
                "build", "path-create", "log-strip", "pvp", "smart-door", "redstone", "pistons", "utility",
                "spawn-monsters", "passives", "flow-damage", "allow-home", "use-potions", "iceform-player",
                "iceform-world", "allow-spawners", "decorations", "portal-create", "portal-use", "projectiles",
                "item-pickup", "item-drop", "crop-trample", "animal-leash", "animal-ride", "animal-breed",
                "fishing", "elytra", "special-weapons", "bed-use", "crafting", "enchanting", "respawn-anchor",
                "flight")
            add(true, FlagType.BLACKLIST,
                "chest", "ender-chest", "lever", "button", "door", "minecart", "flow", "fire", "teleport",
                "cant-grow", "leaves-decay", "effects", "block-transform", "fall", "explosions", "item-transfer",
                "weather", "conduit-effects")
        }

        assertEquals(expected, BuiltInFlagBehaviors.all)
    }

    @Test fun `whitelist and blacklist values have opposite meaning`() {
        assertTrue(FlagType.WHITELIST.allows(true))
        assertFalse(FlagType.WHITELIST.allows(false))
        assertFalse(FlagType.BLACKLIST.allows(true))
        assertTrue(FlagType.BLACKLIST.allows(false))
    }

    private fun MutableMap<String, FlagBehavior>.add(
        defaultValue: Boolean,
        type: FlagType,
        vararg names: String
    ) {
        names.forEach { name -> put(name, FlagBehavior(defaultValue, type)) }
    }
}
