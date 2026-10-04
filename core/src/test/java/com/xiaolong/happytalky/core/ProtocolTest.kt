package com.xiaolong.happytalky.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun allDataLayerPathsStayInsideHappyTalkyNamespace() {
        assertTrue(Protocol.CALL_RING.startsWith(Protocol.BASE))
        assertTrue(Protocol.CALL_ANSWER.startsWith(Protocol.BASE))
        assertTrue(Protocol.CALL_DECLINE.startsWith(Protocol.BASE))
        assertTrue(Protocol.CALL_CANCEL.startsWith(Protocol.BASE))
        assertTrue(Protocol.CALL_BUSY.startsWith(Protocol.BASE))
        assertTrue(Protocol.CALL_END.startsWith(Protocol.BASE))
        assertTrue(
            Protocol.CALL_PRIORITY_LOCKED
                .startsWith(
                    Protocol.BASE
                )
        )
        assertTrue(
            Protocol.CAPABILITY_PRIORITY_LOCKED_CALL_V1
                .isNotBlank()
        )
        assertTrue(
            Protocol.DEVICE_INFO_REQUEST
                .startsWith(
                    Protocol.BASE
                )
        )
        assertTrue(Protocol.CAPABILITY_PHONE.isNotBlank())
        assertTrue(Protocol.CAPABILITY_WATCH.isNotBlank())
        assertTrue(Protocol.VOICE_PREFIX.startsWith(Protocol.BASE))
    }
}
