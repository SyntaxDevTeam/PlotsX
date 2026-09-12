package pl.syntaxdevteam.plotsx.claiming

import java.util.Locale

/** Selects NEW claims only. Existing plots must always use their persisted geometry. */
enum class ClaimMode(val configValue: String) {
    CLASSIC("classic"), CHUNKS("chunks");

    companion object {
        /** Parses the values of plots.claiming. Missing mode defaults; explicit null is invalid. */
        fun fromSection(section: Map<String, *>): ClaimMode {
            if (!section.containsKey("mode")) return CLASSIC
            val value = section["mode"]
            require(value is String) { "plots.claiming.mode must be classic or chunks" }
            return when (value.trim().lowercase(Locale.ROOT)) {
                "classic" -> CLASSIC
                "chunks" -> CHUNKS
                else -> throw IllegalArgumentException("plots.claiming.mode must be classic or chunks")
            }
        }
    }
}
