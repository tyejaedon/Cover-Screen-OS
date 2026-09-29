package com.tyejaedon.coverscreenos.ui.appshell

internal object HomeRoutes {
    const val Dashboard = "dashboard"
    const val Customize = "customize"
    const val Permissions = "permissions"
    const val About = "about"

    fun deepLink(route: String): String = "coverscreenos://$route"
}
