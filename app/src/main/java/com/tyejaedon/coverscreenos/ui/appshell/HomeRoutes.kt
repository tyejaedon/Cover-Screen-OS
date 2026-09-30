package com.tyejaedon.coverscreenos.ui.appshell

internal object HomeRoutes {
    const val Dashboard = "dashboard"
    const val Customize = "customize"
    const val Permissions = "permissions"
    const val About = "about"

    fun deepLink(route: String): String = "coverscreenos://$route"

    fun initialDestination(requiredMissing: Boolean): String =
        if (requiredMissing) Permissions else Dashboard
}

internal enum class CustomizeCategory(val segment: String, val title: String) {
    WALLPAPER("wallpaper", "Wallpaper"),
    DOCK("dock", "Dock"),
    APPEARANCE("appearance", "Appearance"),
    INPUT("input", "Input");

    val route: String get() = "${HomeRoutes.Customize}/$segment"
    val deepLink: String get() = HomeRoutes.deepLink(route)
}
