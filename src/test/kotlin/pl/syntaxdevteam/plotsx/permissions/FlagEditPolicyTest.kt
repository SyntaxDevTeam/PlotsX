package pl.syntaxdevteam.plotsx.permissions

import org.junit.Assert.*
import org.junit.Test

class FlagEditPolicyTest {
    private fun allowed(vararg grants: Pair<String, Boolean>, enabled: Boolean = true,
                        bypass: Boolean = false, defaultAccess: Boolean = true,
                        flag: String = "pvp"): Boolean =
        FlagEditPolicy.allowed(enabled, bypass, defaultAccess, grants.toMap()::get, flag)

    @Test fun `specific grant or denial overrides wildcard and default`() {
        assertFalse(allowed("plotsx.flag.*" to true, "plotsx.flag.pvp" to false))
        assertTrue(allowed("plotsx.flag.*" to false, "plotsx.flag.pvp" to true))
        assertFalse(allowed("plotsx.flag.*" to false))
        assertTrue(allowed("plotsx.flag.*" to true, defaultAccess = false))
        assertTrue(allowed())
        assertFalse(allowed(defaultAccess = false))
    }

    @Test fun `disabled checks and explicit admin bypass allow editing`() {
        assertTrue(allowed("plotsx.flag.pvp" to false, enabled = false))
        assertTrue(allowed("plotsx.flag.pvp" to false, bypass = true))
        assertFalse(allowed("plotsx.flag.pvp" to false, bypass = false))
    }

    @Test fun `namespaced integration flags and wildcard role grants`() {
        assertFalse(allowed("plotsx.flag.demo:machines" to false, flag = "demo:machines"))
        assertTrue(allowed("plotsx.flag.*" to true, defaultAccess = false, flag = "demo:machines"))
        assertTrue(FlagEditPolicy.roleDefault(setOf("flag.*"), "flag.pvp"))
        assertTrue(FlagEditPolicy.roleDefault(setOf("flag.*"), "flag.demo:machines"))
        assertFalse(FlagEditPolicy.roleDefault(setOf("flag.*"), "rename"))
        assertFalse(FlagEditPolicy.roleDefault(setOf("flag.build"), "flag.pvp"))
    }
}
