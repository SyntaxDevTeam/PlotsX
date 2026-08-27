package pl.syntaxdevteam.plotsx.loader

import org.bukkit.Bukkit
import pl.syntaxdevteam.plotsx.PlotsX
class VersionChecker(private val plugin: PlotsX) {

    private fun getRawVersion(): String =
        Bukkit.getServer().bukkitVersion

    fun getServerVersion(): String =
        getRawVersion().substringBefore("-")

    fun isSupported(): Boolean {
        val version = getServerVersion().normalizeVersion()
        return when (version.getOrElse(0) { 0 }) {
            1 -> {
                val minor = version.getOrElse(1) { 0 }
                val patch = version.getOrElse(2) { 0 }
                (minor == 20 && patch >= 6) || (minor == 21 && patch <= 11)
            }
            26 -> version.getOrElse(1) { 0 } in 1..3
            else -> false
        }
    }

    fun checkAndLog(): Boolean {
        val version = getServerVersion()
        return if (isSupported()) {
            plugin.logger.success("The server is running on a supported version $version.")
            true
        } else {
            plugin.logger.warning("Warning! Unsupported version $version – use with caution!")
            false
        }
    }

    fun isAtLeast(minVersion: String): Boolean {
        val current = getServerVersion().normalizeVersion()
        val required = minVersion.normalizeVersion()
        return compareVersions(current, required) >= 0
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0..2) {
            val cmp = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    private fun String.normalizeVersion(): List<Int> {
        return this.split(".")
            .map { it.toIntOrNull() ?: 0 }
            .let {
                when (it.size) {
                    1 -> listOf(it[0], 0, 0)
                    2 -> listOf(it[0], it[1], 0)
                    else -> listOf(it[0], it[1], it[2])
                }
            }
    }
}
