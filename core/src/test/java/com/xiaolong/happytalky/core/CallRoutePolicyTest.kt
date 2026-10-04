package com.xiaolong.happytalky.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRoutePolicyTest {
    @Test
    fun nearbyDirectAllowsCallWhenConnected() {
        assertTrue(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.CONNECTED,
                PeerRoute.NEARBY_DIRECT
            )
        )
    }

    @Test
    fun remoteWifiAllowsCallWhenConnected() {
        assertTrue(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.CONNECTED,
                PeerRoute.REMOTE_WIFI
            )
        )
    }

    @Test
    fun remoteCellularAllowsCallWhenConnected() {
        assertTrue(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.CONNECTED,
                PeerRoute.REMOTE_CELLULAR
            )
        )
        assertFalse(
            CallRoutePolicy.preferTalk(
                PeerRoute.REMOTE_CELLULAR
            )
        )
    }

    @Test
    fun remoteInternetAllowsCallWhenConnected() {
        assertTrue(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.CONNECTED,
                PeerRoute.REMOTE_INTERNET
            )
        )
        assertFalse(
            CallRoutePolicy.preferTalk(
                PeerRoute.REMOTE_INTERNET
            )
        )
    }

    @Test
    fun unavailableRoutesPreferTalk() {
        assertTrue(
            CallRoutePolicy.preferTalk(
                PeerRoute.OFFLINE
            )
        )
        assertTrue(
            CallRoutePolicy.preferTalk(
                PeerRoute.RECONNECTING
            )
        )
        assertTrue(
            CallRoutePolicy.preferTalk(
                PeerRoute.UNKNOWN
            )
        )
    }

    @Test
    fun disconnectedNeverStartsCall() {
        assertFalse(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.DISCONNECTED,
                PeerRoute.NEARBY_DIRECT
            )
        )
        assertFalse(
            CallRoutePolicy.canStartCall(
                PeerConnectionState.DISCONNECTED,
                PeerRoute.REMOTE_CELLULAR
            )
        )
    }
}
