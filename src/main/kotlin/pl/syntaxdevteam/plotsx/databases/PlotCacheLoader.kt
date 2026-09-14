package pl.syntaxdevteam.plotsx.databases

import java.sql.Connection
import java.util.UUID

internal data class PlotCacheData(val plots: List<PlotData>, val flags: Map<Int, List<PlotFlagData>>,
                                     val members: Map<Int, List<PlotMemberData>>)

/** Strict snapshot read for the current runtime. SQL failures must not become an empty protection cache. */
internal object PlotCacheLoader {
    fun load(conn: Connection, plotId: Int? = null): PlotCacheData {
        require(conn.autoCommit) { "Cache load requires a dedicated connection" }
        val isolation = conn.transactionIsolation
        if (conn.metaData.databaseProductName != "SQLite") conn.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        conn.autoCommit = false
        try {
            val suffix = if (plotId == null) "" else " WHERE plot_id = ?"
            fun query(table: String, consume: (java.sql.ResultSet) -> Unit) {
                conn.prepareStatement("SELECT * FROM $table$suffix").use { stmt ->
                    if (plotId != null) stmt.setInt(1, plotId)
                    stmt.executeQuery().use(consume)
                }
            }
            val chunkSchema = DatabaseMigrations.isChunkSchema(conn)
            val extensions = mutableMapOf<Int, MutableList<PlotSegment>>()
            if (!chunkSchema) query("plot_segments") { rows -> while (rows.next()) {
                extensions.getOrPut(rows.getInt("plot_id")) { mutableListOf() }
                    .add(PlotSegment(rows.getInt("x"), rows.getInt("z"), rows.getInt("radius")))
            } }
            val plots = mutableListOf<PlotData>()
            if (chunkSchema) {
                plots.addAll(PlotRepository.readAll(conn).filter { plotId == null || it.id == plotId }.map { it.toPlotData() })
            } else {
            query("plots") { rows ->
                while (rows.next()) {
                    val radius = (rows.getObject("radius") as? Number)?.toInt()
                    require(radius != null && radius >= 0) { "Invalid classic radius" }
                    val id = rows.getInt("plot_id")
                    plots.add(PlotData(id, UUID.fromString(rows.getString("owner_uuid")), rows.getInt("x"), rows.getInt("z"),
                        rows.getInt("y"), radius, rows.getString("world"), rows.getString("name"), rows.getLong("creation_time"),
                        extensions[id].orEmpty()))
                }
            }
            }
            val flags = mutableMapOf<Int, MutableList<PlotFlagData>>()
            query("plot_flags") { rows -> while (rows.next()) {
                val id = rows.getInt("plot_id")
                flags.getOrPut(id) { mutableListOf() }.add(PlotFlagData(id, rows.getString("flag_name"), rows.getString("flag_value")))
            } }
            val members = mutableMapOf<Int, MutableList<PlotMemberData>>()
            query("plot_members") { rows -> while (rows.next()) {
                val id = rows.getInt("plot_id")
                members.getOrPut(id) { mutableListOf() }.add(PlotMemberData(id, rows.getString("member_uuid"), rows.getString("role")))
            } }
            val levels = mutableMapOf<Int, Int>()
            query("plot_expansion_levels") { rows -> while (rows.next()) {
                val level = rows.getInt("expansion_level")
                require(level >= 0) { "Invalid expansion level" }
                levels[rows.getInt("plot_id")] = level
            } }
            conn.commit()
            return PlotCacheData(plots.map { plot ->
                if (plot.radius == null) plot.copy(expansionLevel = levels[plot.id] ?: (plot.chunks.size - 1)) else plot
            }, flags, members)
        } catch (failure: Exception) {
            conn.rollback()
            throw failure
        } finally {
            conn.autoCommit = true
            if (conn.transactionIsolation != isolation) conn.transactionIsolation = isolation
        }
    }
}
