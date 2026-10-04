package com.xiaolong.happytalky.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.MaterialTheme
import com.android.tools.screenshot.PreviewTest
import com.xiaolong.happytalky.core.CallDirection
import com.xiaolong.happytalky.core.CallHistoryEntry
import com.xiaolong.happytalky.core.CallOutcome
import com.xiaolong.happytalky.core.CallMode
import com.xiaolong.happytalky.core.CallVisualState
import com.xiaolong.happytalky.core.ConversationDirection
import com.xiaolong.happytalky.core.ConversationItem
import com.xiaolong.happytalky.core.ConversationItemType
import com.xiaolong.happytalky.core.DeliveryState
import com.xiaolong.happytalky.core.HappyTalkyUiState
import com.xiaolong.happytalky.core.PeerConnectionState
import com.xiaolong.happytalky.core.PeerRoute
import com.xiaolong.happytalky.core.ProximityBand
import com.xiaolong.happytalky.core.ProximityReading
import com.xiaolong.happytalky.core.ProximityTrend
import com.xiaolong.happytalky.core.VoiceDirection
import com.xiaolong.happytalky.core.VoiceMessage
import java.io.File

private const val WATCH_DEVICE =
    "spec:width=192dp,height=192dp,dpi=320,isRound=true"
private const val WATCH_BACKGROUND = 0xFF000000

private val watchCallHistory =
    listOf(
        CallHistoryEntry(
            id = "call-1",
            callId = "call-id-1",
            occurredAt = 1_760_000_180_000L,
            direction = CallDirection.INCOMING,
            outcome = CallOutcome.DECLINED_BY_ME,
        ),
        CallHistoryEntry(
            id = "call-2",
            callId = "call-id-2",
            occurredAt = 1_760_000_240_000L,
            direction = CallDirection.OUTGOING,
            outcome = CallOutcome.COMPLETED,
            durationMs = 74_000L,
        ),
    )


private val watchConversation =
    listOf(
        ConversationItem(
            id = "watch-text-in",
            type = ConversationItemType.TEXT,
            direction = ConversationDirection.INCOMING,
            createdAt = 1_760_000_280_000L,
            deliveryState = DeliveryState.DELIVERED,
            text = "Dinner is ready ❤️",
        ),
        ConversationItem(
            id = "watch-in-1",
            type = ConversationItemType.VOICE,
            direction = ConversationDirection.INCOMING,
            createdAt = 1_760_000_000_000L,
            deliveryState = DeliveryState.DELIVERED,
            audioFileName = "watch-in-1.m4a",
            durationMs = 8_000L,
        ),
        ConversationItem(
            id = "watch-text-out",
            type = ConversationItemType.TEXT,
            direction = ConversationDirection.OUTGOING,
            createdAt = 1_760_000_300_000L,
            deliveryState = DeliveryState.QUEUED,
            text = "Coming! 👍",
        ),
        ConversationItem(
            id = "call-2",
            type = ConversationItemType.CALL,
            direction = ConversationDirection.OUTGOING,
            createdAt = 1_760_000_240_000L,
            deliveryState = DeliveryState.READ,
            callId = "call-id-2",
            callOutcome = CallOutcome.COMPLETED.name,
            durationMs = 74_000L,
        ),
    )

private val watchMessages =
    listOf(
        VoiceMessage(
            id = "watch-in-1",
            createdAt = 1_760_000_000_000L,
            direction = VoiceDirection.INCOMING,
            file = File("/tmp/watch-in-1.m4a"),
            durationMs = 8_000L,
        ),
        VoiceMessage(
            id = "watch-out-1",
            createdAt = 1_760_000_060_000L,
            direction = VoiceDirection.OUTGOING,
            file = File("/tmp/watch-out-1.m4a"),
            durationMs = 5_000L,
        ),
    )

@PreviewTest
@Preview(
    name = "Watch ready",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchReadyScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Ready",
                callState = CallVisualState.READY,
                callEnabled = true,
                peerName = "Phone",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
                textEnabled = true,
                messages = watchMessages,
                unreadVoiceCount = 1,
                callHistory = watchCallHistory,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}


@PreviewTest
@Preview(
    name = "Watch Find Phone close",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchFindPhoneScreenshot() {
    MaterialTheme {
        FindPhoneScreen(
            state =
                FindPhoneUiState(
                    phoneReady = true,
                    searching = false,
                    reading =
                        ProximityReading(
                            rawRssi = -58,
                            filteredRssi = -59.2,
                            signalScore = 76,
                            band =
                                ProximityBand.CLOSE,
                            trend =
                                ProximityTrend.GETTING_CLOSER,
                            timestampMs = 1_000L,
                        ),
                ),
            onClose = {},
            onRetry = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch incoming",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchIncomingScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Phone is calling",
                callState = CallVisualState.INCOMING,
                peerName = "Phone",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}


@PreviewTest
@Preview(
    name = "Watch priority incoming",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchPriorityIncomingScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Priority call",
                callState = CallVisualState.INCOMING,
                callMode = CallMode.PRIORITY,
                priorityLocked = true,
                peerName = "Phone · Pixel 10 Pro",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch locked priority live",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchLockedPriorityLiveScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Priority call with Phone",
                callState = CallVisualState.LIVE,
                callMode = CallMode.PRIORITY,
                priorityLocked = true,
                callEnabled = false,
                talkEnabled = false,
                peerName = "Phone",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
            openInbox = true,
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch recording",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchRecordingScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Recording TALK…",
                callState = CallVisualState.READY,
                recording = true,
                callEnabled = true,
                peerName = "Phone",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}


@PreviewTest
@Preview(
    name = "Watch offline",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchOfflineScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Phone offline · TALK recommended",
                callState = CallVisualState.READY,
                callEnabled = false,
                talkEnabled = true,
                peerName = "Phone",
                peerConnection = PeerConnectionState.DISCONNECTED,
                peerRoute = PeerRoute.OFFLINE,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch live",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchLiveScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Live with Phone",
                callState = CallVisualState.LIVE,
                callEnabled = true,
                talkEnabled = false,
                peerName = "Phone",
                peerConnection = PeerConnectionState.CONNECTED,
                peerRoute = PeerRoute.NEARBY_DIRECT,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}


@PreviewTest
@Preview(
    name = "Watch reconnecting",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchReconnectingScreenshot() {
    MaterialTheme {
        WearHome(
            state = HappyTalkyUiState(
                status = "Reconnecting…",
                callState = CallVisualState.RECONNECTING,
                callEnabled = true,
                talkEnabled = false,
                peerName = "Phone",
                peerConnection = PeerConnectionState.RECONNECTING,
                peerRoute = PeerRoute.RECONNECTING,
            ),
            onCall = {},
            onDecline = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
        )
    }
}


@PreviewTest
@Preview(
    name = "Watch TALK inbox",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchTalkInboxScreenshot() {
    MaterialTheme {
        WearInbox(
            messages = watchMessages,
            callHistory = watchCallHistory,
            timeline = watchConversation,
            unreadCount = 2,
            peerName = "Phone",
            textEnabled = true,
            onBack = {},
            onPlay = {},
        )
    }
}



@PreviewTest
@Preview(
    name = "Watch mixed inbox",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchMixedInboxScreenshot() {
    MaterialTheme {
        WearInbox(
            messages = watchMessages,
            callHistory = watchCallHistory,
            timeline = watchConversation,
            unreadCount = 2,
            peerName = "Phone · Pixel 10 Pro",
            textEnabled = true,
            onBack = {},
            onPlay = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch text read aloud",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchTextReadAloudScreenshot() {
    MaterialTheme {
        WearInbox(
            messages = watchMessages,
            callHistory = watchCallHistory,
            timeline = watchConversation,
            unreadCount = 2,
            peerName = "Phone · Pixel 10 Pro",
            textEnabled = true,
            onBack = {},
            onPlay = {},
            speakingTextId =
                "watch-text-out",
        )
    }
}

@PreviewTest
@Preview(
    name = "Watch TALK deleted confirmation",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchTalkDeletedConfirmationScreenshot() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            WearInbox(
                messages = watchMessages,
                callHistory = watchCallHistory,
                timeline = watchConversation,
                unreadCount = 1,
                peerName = "Phone",
                textEnabled = true,
                    onBack = {},
                onPlay = {},
            )

            WearDeleteConfirmation(
                modifier =
                    Modifier.align(
                        Alignment.Center
                    )
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Watch message deleted confirmation",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchMessageDeletedConfirmationScreenshot() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            WearInbox(
                messages = watchMessages,
                callHistory = watchCallHistory,
                timeline = watchConversation,
                unreadCount = 1,
                peerName = "Phone",
                textEnabled = true,
                    onBack = {},
                onPlay = {},
            )

            WearDeleteConfirmation(
                label = "Message deleted ✓",
                modifier =
                    Modifier.align(
                        Alignment.Center
                    )
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Watch call deleted confirmation",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchCallDeletedConfirmationScreenshot() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            WearInbox(
                messages = watchMessages,
                callHistory = watchCallHistory,
                timeline = watchConversation,
                unreadCount = 1,
                peerName = "Phone",
                textEnabled = true,
                    onBack = {},
                onPlay = {},
            )

            WearDeleteConfirmation(
                label = "Call deleted ✓",
                modifier =
                    Modifier.align(
                        Alignment.Center
                    )
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "Watch CALL history",
    device = WATCH_DEVICE,
    showBackground = true,
    backgroundColor = WATCH_BACKGROUND,
)
@Composable
fun WatchCallHistoryScreenshot() {
    MaterialTheme {
        WearInbox(
            messages = emptyList(),
            callHistory = watchCallHistory,
            unreadCount = 0,
            peerName = "Phone",
            onBack = {},
            onPlay = {},
        )
    }
}
