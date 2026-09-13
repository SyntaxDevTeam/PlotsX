package pl.syntaxdevteam.plotsx.api

import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Obtain through Bukkit's ServicesManager. Do not shade these classes into consumers.
 * Snapshot/flag reads use caches and may run asynchronously; they never load worlds/chunks.
 * getRolePermissions is a database-backed exception requiring the server thread.
 * Mutation, registration and CommandSender operations require the Paper server thread.
 * A retained service becomes invalid when PlotsX is disabled.
 */
interface PlotsXApi {
    val apiVersion: Int
    fun getPlot(id: Int): PlotSnapshot?
    fun getPlotAt(world: String, blockX: Int, blockZ: Int): PlotSnapshot?
    fun getPlots(): List<PlotSnapshot>
    fun getOwnedPlots(owner: UUID): List<PlotSnapshot>
    fun getAccessiblePlots(player: UUID): List<PlotSnapshot>
    fun getMembers(plotId: Int): List<MemberSnapshot>
    fun isMember(plotId: Int, player: UUID): Boolean
    fun getFlags(): List<FlagDefinition>
    /** Effective value (explicit setting or default); null for unknown plot/flag. */
    fun getFlagValue(plotId: Int, flag: String): Boolean?
    /** Unknown flags deny explicitly, even outside plots. No permission-node bypass is applied.
     * subject is optional: without it evaluate the rule as a visitor/environmental action.
     */
    fun evaluateFlag(world: String, blockX: Int, blockZ: Int, flag: String, subject: UUID?): FlagDecision
    fun canManage(actor: CommandSender, plotId: Int, action: String): Boolean
    /** Database-backed, server thread only. */
    fun getRolePermissions(plotId: Int, role: String): Set<String>
    fun getRoles(): List<String>
    fun addMember(actor: CommandSender, plotId: Int, player: UUID): MemberUpdateResult
    fun removeMember(actor: CommandSender, plotId: Int, player: UUID): MemberUpdateResult
    fun setMemberRole(actor: CommandSender, plotId: Int, player: UUID, role: String): MemberUpdateResult
    fun setRolePermission(actor: CommandSender, plotId: Int, role: String, action: String, allowed: Boolean): MemberUpdateResult
    /** Consumer is responsible for user confirmation before calling. Recipient must be online. */
    fun transferOwnership(actor: CommandSender, plotId: Int, recipient: UUID): MemberUpdateResult
    /** Checks current owner/rank permissions, persists, refreshes cache and emits a changed event. */
    fun setFlag(actor: CommandSender, plotId: Int, flag: String, value: Boolean): FlagUpdateResult
    /** Register a namespaced flag owned by provider, e.g. myplugin:machines. Never replaces a flag. */
    fun registerFlag(provider: Plugin, definition: FlagDefinition): Boolean
    /** Removes the definition only; saved values survive disable/re-enable. */
    fun unregisterFlag(provider: Plugin, key: String): Boolean
}

data class PlotSnapshot @JvmOverloads constructor(val id: Int, val owner: UUID, val world: String, val x: Int, val y: Int,
                        val z: Int, val radius: Int?, val name: String, val createdAt: Long,
                        val extensions: List<PlotRegionSnapshot> = emptyList(),
                        val chunks: List<ChunkSnapshot> = emptyList(), val geometryRevision: Long = 0) {
    /** Claimed block columns; classic segment areas are summed as in the storage contract. */
    val area: Long get() {
        if (radius == null) return chunks.distinct().size.toLong() * 256
        fun square(r: Int): Long {
            val side = r.toLong() * 2 + 1
            return if (side > 3037000499L) Long.MAX_VALUE else side * side
        }
        return extensions.fold(square(radius)) { sum, region ->
            val added = square(region.radius)
            if (sum > Long.MAX_VALUE - added) Long.MAX_VALUE else sum + added
        }
    }
    val geometryType: String get() = if (radius == null) "chunks" else "classic"
    fun contains(world: String, x: Int, z: Int): Boolean = this.world.equals(world, true) &&
        (if (radius == null) chunks.any { it.x == Math.floorDiv(x, 16) && it.z == Math.floorDiv(z, 16) }
        else (kotlin.math.abs(x.toLong() - this.x) <= radius.toLong() &&
        kotlin.math.abs(z.toLong() - this.z) <= radius.toLong()) || extensions.any { it.contains(x, z) })
}

/** API v2: complete 16 x 16 block columns; radius is null for chunk plots. */
data class ChunkSnapshot(val x: Int, val z: Int)

data class MemberSnapshot(val player: UUID, val role: String)

/** enabledMeansAllowed handles the historical inverse (blacklist) flags explicitly.
 * memberBypass=false makes the rule apply to owners and members as well (e.g. graves).
 * Display strings are plain text for custom flags; built-in definitions expose translation keys.
 */
data class FlagDefinition(val key: String, val defaultValue: Boolean, val enabledMeansAllowed: Boolean,
                          val memberBypass: Boolean, val material: Material,
                          val displayName: String, val description: String)

enum class FlagDecision { ALLOW, DENY, UNKNOWN_FLAG }
enum class FlagUpdateResult { UPDATED, UNCHANGED, PLOT_NOT_FOUND, UNKNOWN_FLAG, DENIED, DATABASE_ERROR }
enum class MemberUpdateResult {
    UPDATED, PLOT_NOT_FOUND, DENIED, OWNER, ALREADY_MEMBER, NOT_MEMBER, UNKNOWN_ROLE,
    UNKNOWN_ACTION, RECIPIENT_OFFLINE, TRANSFER_REJECTED, DATABASE_ERROR
}

/** One purchased region; radius on PlotSnapshot describes only the original square. */
data class PlotRegionSnapshot(val x: Int, val z: Int, val radius: Int) {
    fun contains(x: Int, z: Int): Boolean = kotlin.math.abs(x.toLong() - this.x) <= radius &&
        kotlin.math.abs(z.toLong() - this.z) <= radius
}
