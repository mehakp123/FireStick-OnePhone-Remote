package com.pannu.firestickremote.core

object FireTvEndpoints {
    const val API_KEY = "0987654321"
    const val DIAL_PORT = 8009
    const val CONTROL_PORT = 8080

    const val WAKE = "/apps/FireTVRemote"
    const val PIN_DISPLAY = "/v1/FireTV/pin/display"
    const val PIN_VERIFY = "/v1/FireTV/pin/verify"
    const val STATUS = "/v1/FireTV/status"
    const val PROPERTIES = "/v1/FireTV/properties"
    const val CAPABILITIES = "/v1/FireTV2"
    const val APPS = "/v1/FireTV/appsV2"
    const val TEXT = "/v1/FireTV/text"

    fun navigation(action: String): String = "/v1/FireTV?action=$action"
    fun media(action: String): String = "/v1/media?action=$action"
    fun launchApp(packageName: String): String = "/v1/FireTV/app/$packageName"

    val navigationActions = setOf(
        "dpad_up", "dpad_down", "dpad_left", "dpad_right",
        "select", "back", "home", "menu"
    )
}
