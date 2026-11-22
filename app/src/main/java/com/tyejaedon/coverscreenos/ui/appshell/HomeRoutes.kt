package com.tyejaedon.coverscreenos.ui.appshell

/**
 * Top-level Jetpack Navigation routes, deep-link URI builder, and start-route policy for [AppShell].
 */
internal object HomeRoutes {
    const val Dashboard = "dashboard"
    const val Customize = "customize"
    const val Permissions = "permissions"
    const val About = "about"

    /** Formats a `coverscreenos://` deep-link pattern for [route] matching manifest intent filters. */
    fun deepLink(route: String): String = "coverscreenos://$route"

    /**
     * Chooses the initial [NavHost][androidx.navigation.compose.NavHost] destination:
     * routes directly to [Permissions] when any required permission is missing, otherwise [Dashboard].
     */
    fun initialDestination(requiredMissing: Boolean): String =
        if (requiredMissing) Permissions else Dashboard
}

/**
 * Nested sub-destinations inside the [HomeRoutes.Customize] navigation subgraph.
 *
 * Each entry maps a customization tab to its URL-like route segment (`customize/<segment>`)
 * and corresponding deep-link URI.
 */
internal enum class CustomizeCategory(val segment: String, val title: String) {
    WALLPAPER("wallpaper", "Wallpaper"),
    DOCK("dock", "Dock"),
    APPEARANCE("appearance", "Appearance"),
    INPUT("input", "Input");

    /** Full Compose Navigation route under [HomeRoutes.Customize]. */
    val route: String get() = "${HomeRoutes.Customize}/$segment"

    /** Deep-link URI targeting this customization category directly. */
    val deepLink: String get() = HomeRoutes.deepLink(route)
}
