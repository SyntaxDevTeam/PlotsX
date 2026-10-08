package pl.syntaxdevteam.plotsx.compatibility

/**
 * Handles runtime compatibility between different Minecraft versions.
 *
 * This class is intended to own feature flags and behavioral toggles that
 * cannot be expressed through standard API checks. Material and entity
 * classification is handled by [pl.syntaxdevteam.plotsx.compat.PlotCompat].
 */
class VersionCompatibility(
    versionChecker: VersionChecker,
    parseVersion: (String) -> SemanticVersion = { SemanticVersion.parse(it) }
) {

    private val currentVersion: SemanticVersion = versionChecker.getSemanticVersion()

    enum class CompatibilityFlag {
        MODERN_LOGIN_EVENTS,
        DIALOGS
    }

    private val featureIntroductions: Map<CompatibilityFlag, SemanticVersion> = mapOf(
        CompatibilityFlag.MODERN_LOGIN_EVENTS to parseVersion("1.21.7"),
        CompatibilityFlag.DIALOGS to parseVersion("1.21.7")
    )

    fun supports(flag: CompatibilityFlag): Boolean {
        val introducedAt = featureIntroductions[flag] ?: return true
        return currentVersion >= introducedAt
    }
}
