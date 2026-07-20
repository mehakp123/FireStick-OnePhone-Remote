package com.pannu.firestickremote.core

enum class ProtocolMode {
    AUTO,
    LIGHTNING_MODERN,
    LIGHTNING_LEGACY,
    VEGA_EXPERIMENTAL
}

data class FireTvProfile(
    val displayName: String,
    val mode: ProtocolMode,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val sendKeyUp: Boolean,
    val allowHttpFallback: Boolean
)

enum class FireTvModel(val profile: FireTvProfile) {
    AUTO(
        FireTvProfile(
            "Auto detect (recommended)",
            ProtocolMode.AUTO,
            900,
            2500,
            true,
            true
        )
    ),
    STICK_GEN_1(
        FireTvProfile("Fire TV Stick 1st Gen (2014)", ProtocolMode.LIGHTNING_LEGACY, 1500, 3500, false, true)
    ),
    STICK_GEN_2(
        FireTvProfile("Fire TV Stick 2nd Gen (2016)", ProtocolMode.LIGHTNING_LEGACY, 1400, 3500, false, true)
    ),
    BASIC_EDITION(
        FireTvProfile("Fire TV Stick Basic Edition (2017)", ProtocolMode.LIGHTNING_LEGACY, 1400, 3500, false, true)
    ),
    STICK_4K_GEN_1(
        FireTvProfile("Fire TV Stick 4K 1st Gen (2018)", ProtocolMode.LIGHTNING_LEGACY, 1200, 3000, true, true)
    ),
    STICK_LITE_GEN_1(
        FireTvProfile("Fire TV Stick Lite / 3rd Gen (2020)", ProtocolMode.LIGHTNING_MODERN, 1000, 2800, true, true)
    ),
    STICK_4K_MAX_GEN_1(
        FireTvProfile("Fire TV Stick 4K Max 1st Gen (2021)", ProtocolMode.LIGHTNING_MODERN, 900, 2500, true, true)
    ),
    STICK_4K_GEN_2(
        FireTvProfile("Fire TV Stick 4K / 4K Max 2nd Gen (2023)", ProtocolMode.LIGHTNING_MODERN, 800, 2500, true, true)
    ),
    STICK_HD_FIRE_OS(
        FireTvProfile("Fire TV Stick HD (Fire OS family)", ProtocolMode.LIGHTNING_MODERN, 900, 2800, true, true)
    ),
    STICK_4K_SELECT_VEGA(
        FireTvProfile("Fire TV Stick 4K Select (Vega OS, experimental)", ProtocolMode.VEGA_EXPERIMENTAL, 1200, 3500, true, true)
    ),
    STICK_HD_VEGA(
        FireTvProfile("Fire TV Stick HD (Vega OS, experimental)", ProtocolMode.VEGA_EXPERIMENTAL, 1200, 3500, true, true)
    ),
    CUBE_OR_TV(
        FireTvProfile("Fire TV Cube / Fire TV television", ProtocolMode.AUTO, 1200, 3000, true, true)
    );

    companion object {
        fun displayNames(): List<String> = entries.map { it.profile.displayName }
        fun fromPosition(position: Int): FireTvModel = entries.getOrElse(position) { AUTO }
    }
}
