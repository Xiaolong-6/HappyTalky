package com.xldev.happytalky.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.xldev.happytalky.core.CallMode
import com.xldev.happytalky.core.CallVisualState
import com.xldev.happytalky.core.ConversationDirection
import com.xldev.happytalky.core.ConversationItem
import com.xldev.happytalky.core.ConversationItemType
import com.xldev.happytalky.core.DeliveryState
import com.xldev.happytalky.core.HappyTalkyUiState
import com.xldev.happytalky.core.PeerConnectionState
import com.xldev.happytalky.core.PeerRoute
import com.xldev.happytalky.core.VoiceDirection
import com.xldev.happytalky.core.VoiceMessage
import java.io.File

private val sampleMessages =
    listOf(
        VoiceMessage(
            id = "in-1",
            createdAt = 1_760_000_000_000L,
            direction = VoiceDirection.INCOMING,
            file = File("/tmp/in-1.m4a"),
            durationMs = 8_000L,
        ),
        VoiceMessage(
            id = "out-1",
            createdAt = 1_760_000_060_000L,
            direction = VoiceDirection.OUTGOING,
            file = File("/tmp/out-1.m4a"),
            durationMs = 12_000L,
        ),
        VoiceMessage(
            id = "in-2",
            createdAt = 1_760_000_120_000L,
            direction = VoiceDirection.INCOMING,
            file = File("/tmp/in-2.m4a"),
            durationMs = 5_000L,
        ),
    )


private val sampleConversation =
    listOf(
        ConversationItem(
            id = "in-1",
            type = ConversationItemType.VOICE,
            direction = ConversationDirection.INCOMING,
            createdAt = 1_760_000_000_000L,
            deliveryState = DeliveryState.DELIVERED,
            audioFileName = "in-1.m4a",
            durationMs = 8_000L,
        ),
        ConversationItem(
            id = "text-in",
            type = ConversationItemType.TEXT,
            direction = ConversationDirection.INCOMING,
            createdAt = 1_760_000_030_000L,
            deliveryState = DeliveryState.DELIVERED,
            text = "Dinner is ready ❤️",
        ),
        ConversationItem(
            id = "out-1",
            type = ConversationItemType.VOICE,
            direction = ConversationDirection.OUTGOING,
            createdAt = 1_760_000_060_000L,
            deliveryState = DeliveryState.QUEUED,
            audioFileName = "out-1.m4a",
            durationMs = 12_000L,
        ),
        ConversationItem(
            id = "text-out",
            type = ConversationItemType.TEXT,
            direction = ConversationDirection.OUTGOING,
            createdAt = 1_760_000_090_000L,
            deliveryState = DeliveryState.QUEUED,
            text = "Coming! 👍",
        ),
        ConversationItem(
            id = "in-2",
            type = ConversationItemType.VOICE,
            direction = ConversationDirection.INCOMING,
            createdAt = 1_760_000_120_000L,
            deliveryState = DeliveryState.DELIVERED,
            audioFileName = "in-2.m4a",
            durationMs = 5_000L,
        ),
    )

private fun readyState(
    messages: List<VoiceMessage> = sampleMessages,
) =
    HappyTalkyUiState(
        status = "Ready",
        callState = CallVisualState.READY,
        callEnabled = true,
        talkEnabled = true,
        peerName = "Watch",
        peerConnection = PeerConnectionState.CONNECTED,
        peerRoute = PeerRoute.NEARBY_DIRECT,
        peerCapabilities =
            setOf(
                com.xldev.happytalky.core
                    .Protocol
                    .CAPABILITY_PRIORITY_LOCKED_CALL_V1
            ),
        priorityCallAvailable = true,
        textEnabled = true,
        messages = messages,
    )

@Composable
private fun PhoneShot(
    state: HappyTalkyUiState,
    dark: Boolean,
) {
    HappyTalkyPhoneTheme(
        darkTheme = dark
    ) {
        HappyTalkyPhoneScreen(
            state = state,
            onCall = {},
            onDecline = {},
            onSpeakerToggle = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
            onDelete = {},
            onClear = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Phone light conversation",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneLightConversationScreenshot() {
    PhoneShot(
        state = readyState(),
        dark = false,
    )
}

@PreviewTest
@Preview(
    name = "Phone dark conversation",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneDarkConversationScreenshot() {
    PhoneShot(
        state = readyState(),
        dark = true,
    )
}


@PreviewTest
@Preview(
    name = "Phone mixed conversation",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneMixedConversationScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                timeline =
                    sampleConversation
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone outgoing call",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneCallingScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Calling Watch…",
                callState = CallVisualState.OUTGOING,
                talkEnabled = false,
            ),
        dark = true,
    )
}


@PreviewTest
@Preview(
    name = "Phone priority ready",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhonePriorityOfferScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Ready",
                callState =
                    CallVisualState.READY,
                priorityCallAvailable =
                    true,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone priority requested",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhonePriorityRequestedScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status =
                    "Priority call sent · waiting for Watch",
                callState =
                    CallVisualState.OUTGOING,
                talkEnabled = false,
                priorityCallAvailable = false,
                callMode = CallMode.PRIORITY,
                priorityLocked = true,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone incoming call",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneIncomingScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Watch is calling",
                callState = CallVisualState.INCOMING,
                talkEnabled = false,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone live speaker",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneLiveScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Live with Watch",
                callState = CallVisualState.LIVE,
                talkEnabled = false,
                speakerOn = true,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone recording",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneRecordingScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Recording TALK…",
                recording = true,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone offline",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneOfflineScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Watch offline · TALK recommended",
                callEnabled = false,
                peerConnection = PeerConnectionState.DISCONNECTED,
                peerRoute = PeerRoute.OFFLINE,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone reconnecting",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
fun PhoneReconnectingScreenshot() {
    PhoneShot(
        state =
            readyState().copy(
                status = "Reconnecting…",
                callState = CallVisualState.RECONNECTING,
                talkEnabled = false,
                peerConnection = PeerConnectionState.RECONNECTING,
                peerRoute = PeerRoute.RECONNECTING,
            ),
        dark = true,
    )
}

@PreviewTest
@Preview(
    name = "Phone compact 360x800",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
)
@Composable
fun PhoneCompactScreenshot() {
    PhoneShot(
        state = readyState(),
        dark = false,
    )
}

private fun denseState(): HappyTalkyUiState {
    val time = 1_760_000_000_000L
    val calls = (0..3).map { index ->
        com.xldev.happytalky.core.CallHistoryEntry(
            id = "call-$index", callId = "session-$index", occurredAt = time + index * 1_000L,
            direction = com.xldev.happytalky.core.CallDirection.OUTGOING,
            outcome = if (index == 2) com.xldev.happytalky.core.CallOutcome.CANCELLED_BY_PEER
                else com.xldev.happytalky.core.CallOutcome.CANCELLED_BY_ME,
        )
    }
    return readyState().copy(
        peerName = "Watch · Pixel Watch 4",
        callHistory = calls,
        timeline = calls.map { call ->
            ConversationItem(id = call.id, type = ConversationItemType.CALL,
                direction = ConversationDirection.OUTGOING, createdAt = call.occurredAt,
                deliveryState = DeliveryState.DELIVERED)
        } + listOf(
            sampleConversation[2],
            sampleConversation[1].copy(id = "emoji-1", text = "👍", createdAt = time + 120_000L),
            sampleConversation[1].copy(id = "emoji-2", text = "👍", createdAt = time + 180_000L),
        ),
    )
}

@PreviewTest
@Preview(name = "Phone dense history light", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun PhoneDenseHistoryScreenshot() { PhoneShot(denseState(), dark = false) }

@PreviewTest
@Preview(name = "Phone dense history dark compact", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun PhoneDenseHistoryDarkScreenshot() { PhoneShot(denseState(), dark = true) }

@PreviewTest
@Preview(name = "Phone large text", widthDp = 360, heightDp = 800, fontScale = 1.5f, showBackground = true)
@Composable
fun PhoneLargeTextScreenshot() { PhoneShot(denseState(), dark = false) }

@PreviewTest
@Preview(name = "Phone text unavailable", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun PhoneTextUnavailableScreenshot() {
    PhoneShot(denseState().copy(textEnabled = false, callEnabled = false,
        peerConnection = PeerConnectionState.DISCONNECTED, peerRoute = PeerRoute.OFFLINE), dark = false)
}

@PreviewTest
@Preview(name = "Phone priority unsupported help", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun PhonePriorityHelpScreenshot() {
    HappyTalkyPhoneTheme(darkTheme = false) {
        PriorityCallOptions(
            state =
                readyState().copy(
                    peerCapabilities =
                        emptySet(),
                    priorityCallAvailable =
                        false,
                ),
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(name = "Phone priority busy help", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun PhonePriorityEscalationScreenshot() {
    HappyTalkyPhoneTheme(darkTheme = true) {
        PriorityCallOptions(
            state = readyState().copy(
                peerCapabilities = setOf(com.xldev.happytalky.core.Protocol.CAPABILITY_PRIORITY_LOCKED_CALL_V1),
                priorityCallAvailable = false,
                callState = CallVisualState.LIVE,
            ),
            onDismiss = {},
        )
    }
}
