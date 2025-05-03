package pl.syntaxdevteam.plotsx.protection

enum class FlagType {
    WHITELIST, // true = zezwól obcym (np. build, interact, open)
    BLACKLIST  // true = zablokuj obcych (np. lever, button)
}