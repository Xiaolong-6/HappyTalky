package com.xldev.happytalky.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoTest {
    @Test
    fun watchLabelIncludesModel() {
        val info =
            DeviceInfo(
                deviceId = "watch-1",
                role = EndpointRole.WATCH,
                manufacturer = "Google",
                model = "Pixel Watch 3",
                appVersion = "0.3.0",
                protocolVersion = 1,
                capabilities =
                    setOf(
                        Protocol.CAPABILITY_TALK_V1,
                        Protocol.CAPABILITY_CALL_V1
                    )
            )

        assertEquals(
            "Watch · Pixel Watch 3",
            info.displayLabel()
        )
    }

    @Test
    fun genericRoleNameIsNotRepeated() {
        val info =
            DeviceInfo(
                deviceId = "phone-1",
                role = EndpointRole.PHONE,
                manufacturer = "",
                model = "Phone",
                appVersion = "0.3.0",
                protocolVersion = 1,
                capabilities = emptySet()
            )

        assertEquals(
            "Phone",
            info.displayLabel()
        )
    }

    @Test
    fun staleLegacyPeerInfoCannotOverwriteFreshSnapshot() {
        assertEquals(
            false,
            PeerDeviceInfoPolicy.shouldReplace(
                existingUpdatedAt = 2_000L,
                incomingUpdatedAt = 0L
            )
        )
        assertEquals(
            false,
            PeerDeviceInfoPolicy.shouldReplace(
                existingUpdatedAt = 2_000L,
                incomingUpdatedAt = 1_000L
            )
        )
        assertEquals(
            true,
            PeerDeviceInfoPolicy.shouldReplace(
                existingUpdatedAt = 2_000L,
                incomingUpdatedAt = 3_000L
            )
        )
    }

    @Test
    fun reachablePeerDoesNotBlockTextWhileMetadataRefreshes() {
        val legacyPeer =
            DeviceInfo(
                deviceId = "watch-old",
                role = EndpointRole.WATCH,
                manufacturer = "Google",
                model = "Pixel Watch",
                appVersion = "0.2.0",
                protocolVersion = 1,
                capabilities =
                    setOf(
                        Protocol.CAPABILITY_TALK_V1
                    ),
                updatedAt = 1_000L
            )

        assertEquals(
            true,
            TextCapabilityPolicy.canSend(
                connection =
                    PeerConnectionState.CONNECTED,
                peerInfo = legacyPeer
            )
        )
        assertEquals(
            false,
            TextCapabilityPolicy.canSend(
                connection =
                    PeerConnectionState.DISCONNECTED,
                peerInfo = legacyPeer
            )
        )

        val currentPeer =
            legacyPeer.copy(
                capabilities =
                    legacyPeer.capabilities +
                        Protocol.CAPABILITY_TEXT_V1
            )

        assertEquals(
            true,
            TextCapabilityPolicy.canSend(
                connection =
                    PeerConnectionState.DISCONNECTED,
                peerInfo = currentPeer
            )
        )
    }

}
