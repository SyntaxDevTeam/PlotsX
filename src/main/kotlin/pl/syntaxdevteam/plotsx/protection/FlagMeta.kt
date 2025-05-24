package pl.syntaxdevteam.plotsx.protection

import org.bukkit.Material

data class FlagMeta(
    val name: String,
    val defaultValue: Boolean,
    val type: FlagType,
    val material: Material,
    val displayKey: String,
    val descriptionKey: String
)