package pl.syntaxdevteam.plotsx.protection

data class FlagDefinition(
    val name: String,
    val defaultValue: Boolean,
    val type: FlagType
)
