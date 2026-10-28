package pl.syntaxdevteam.plotsx.api

import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.plotsx.api.internal.ApiActorPolicy
import pl.syntaxdevteam.plotsx.cache.PlotCacheSnapshot
import pl.syntaxdevteam.plotsx.databases.*
import java.util.UUID

class ApiActorPolicyTest {
    @Test fun `queued permissions respect membership revocation role changes and explicit grants at execution`() {
        val player = UUID.randomUUID()
        val plot = PlotData(1, UUID.randomUUID(), 0, 0, 64, 1, "world", "home", 0)
        val policy = ApiActorPolicy(player, false, setOf("member", "manager"), setOf("invite"), mapOf("manager" to setOf("invite")))
        val initial = PlotCacheSnapshot.create(listOf(plot), members = mapOf(1 to listOf(PlotMemberData(1, player.toString(), "manager"))))
        assertTrue(policy.allowed(initial, plot, "invite"))
        val revoked = initial.withPlot(1, plot, emptyList(), emptyList())
        assertFalse(policy.allowed(revoked, plot, "invite"))
        val downgraded = initial.withPlot(1, plot, emptyList(), listOf(PlotMemberData(1, player.toString(), "member")))
        assertFalse(policy.allowed(downgraded, plot, "invite"))
        val disabled = initial.withPlot(1, plot, listOf(PlotFlagData(1, "role_permission.manager.invite", "false")), initial.members.getValue(1))
        assertFalse(policy.allowed(disabled, plot, "invite"))
        assertTrue(policy.allowed(initial, plot, "invite"))
    }
    @Test fun `ownership transfer invalidates previously captured owner authorization`() {
        val owner = UUID.randomUUID()
        val plot = PlotData(1, owner, 0, 0, 64, 1, "world", "home", 0)
        val policy = ApiActorPolicy(owner, false, emptySet(), emptySet(), emptyMap())
        assertTrue(policy.owner(plot))
        val transferred = plot.copy(ownerUuid = UUID.randomUUID())
        assertFalse(policy.owner(transferred))
        assertFalse(policy.allowed(PlotCacheSnapshot.create(listOf(transferred)), transferred, "invite"))
    }
}
