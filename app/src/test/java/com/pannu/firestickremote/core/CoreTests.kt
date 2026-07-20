package com.pannu.firestickremote.core

import org.junit.Assert.*
import org.junit.Test

class CoreTests {
    @Test fun privateIpValidation() {
        assertTrue(IpTools.isPrivateIpv4("192.168.43.1"))
        assertTrue(IpTools.isPrivateIpv4("10.0.0.1"))
        assertFalse(IpTools.isPrivateIpv4("8.8.8.8"))
    }

    @Test fun endpointGeneration() {
        assertEquals("/v1/FireTV?action=dpad_up", FireTvEndpoints.navigation("dpad_up"))
        assertEquals("/v1/media?action=play", FireTvEndpoints.media("play"))
        assertEquals("/v1/FireTV/app/com.netflix.ninja", FireTvEndpoints.launchApp("com.netflix.ninja"))
    }

    @Test fun modelListHasAutoAndVega() {
        assertEquals(FireTvModel.AUTO, FireTvModel.fromPosition(0))
        assertTrue(FireTvModel.entries.any { it.profile.mode == ProtocolMode.VEGA_EXPERIMENTAL })
    }
}
