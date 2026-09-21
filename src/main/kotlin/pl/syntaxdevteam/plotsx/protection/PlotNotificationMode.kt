package pl.syntaxdevteam.plotsx.protection

internal enum class PlotNotificationMode {
    ACTIONBAR,
    CHAT;

    companion object {
        fun fromConfig(value: String?): PlotNotificationMode =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: ACTIONBAR
    }
}
