
fun stripPlatform(name: String): String {
    return name.replace(Regex("\\s*\\[.*]$"), "").trim()
}

fun testMatch() {
    val currentStation = "Kamenická [A]"
    val targetWorkStation = "Křižíkova"
    
    val baseCurrent = stripPlatform(currentStation)
    val baseTarget = stripPlatform(targetWorkStation)
    
    val routeNames = listOf(
        "Nádraží Podbaba", "Zelená", "Lotyšská", "Vítězné náměstí", "Hradčanská", "Sparta", "Korunovační", "Letenské náměstí", "Kamenická", "Strossmayerovo náměstí", "Vltavská", "Těšnov", "Bílá labuť", "Florenc", "Karlínské náměstí", "Křižíkova", "Urxova", "Invalidovna", "Palmovka", "Balabenka", "Ocelářská", "Multiaréna Praha", "Nádraží Libeň", "Kabešova", "Podkovářská", "U Elektry", "Nademlejnská", "Starý Hloubětín"
    )
    
    val currentIndex = routeNames.indexOfFirst { it.equals(baseCurrent, ignoreCase = true) }
    val targetIndex = routeNames.indexOfFirst { it.equals(baseTarget, ignoreCase = true) }
    
    println("Base Current: '$baseCurrent', Index: $currentIndex")
    println("Base Target: '$baseTarget', Index: $targetIndex")
    println("Is Bound: ${targetIndex > currentIndex}")
}

testMatch()
