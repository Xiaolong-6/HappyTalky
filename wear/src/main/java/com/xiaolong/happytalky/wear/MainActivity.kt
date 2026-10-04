package com.xiaolong.happytalky.wear

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.xiaolong.happytalky.core.CallDirection
import com.xiaolong.happytalky.core.CallMode
import com.xiaolong.happytalky.core.CallHistoryEntry
import com.xiaolong.happytalky.core.CallOutcome
import com.xiaolong.happytalky.core.CallVisualState
import com.xiaolong.happytalky.core.ConversationDirection
import com.xiaolong.happytalky.core.ConversationItem
import com.xiaolong.happytalky.core.ConversationItemType
import com.xiaolong.happytalky.core.HappyTalkyActivity
import com.xiaolong.happytalky.core.HappyTalkyUiState
import com.xiaolong.happytalky.core.PeerConnectionState
import com.xiaolong.happytalky.core.PeerRoute
import com.xiaolong.happytalky.core.Protocol
import com.xiaolong.happytalky.core.VoiceDirection
import com.xiaolong.happytalky.core.VoiceMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : HappyTalkyActivity() {
    private var openInboxRequested by mutableStateOf(false)

    private val textInputLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .StartActivityForResult()
        ) { result ->
            if (
                result.resultCode !=
                    Activity.RESULT_OK
            ) {
                return@registerForActivityResult
            }

            val data =
                result.data
                    ?: return@registerForActivityResult
            val reply =
                RemoteInput
                    .getResultsFromIntent(data)
                    ?.getCharSequence(
                        REMOTE_TEXT_KEY
                    )
                    ?.toString()
                    .orEmpty()

            if (reply.isNotBlank()) {
                sendText(reply)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openInboxRequested =
            intent?.getBooleanExtra(
                Protocol.EXTRA_OPEN_INBOX,
                false
            ) == true


        setContent {
            MaterialTheme {
                WearHome(
                    state = uiState,
                    onCall = ::handleCallAction,
                    onDecline = ::declineIncomingCall,
                    onTalkStart = ::beginTalk,
                    onTalkFinish = ::finishTalk,
                    onTalkCancel = ::cancelTalk,
                    onPlay = ::playMessage,
                    onDelete =
                        ::deleteConversationItems,
                    onComposeText =
                        ::launchTextInput,
                    onFindPhone = {
                        startActivity(
                            Intent(
                                this,
                                FindPhoneActivity::class.java
                            )
                        )
                    },
                    openInbox = openInboxRequested,
                    onInboxOpened = {
                        openInboxRequested = false
                        markTextMessagesRead()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openInboxRequested =
            intent.getBooleanExtra(
                Protocol.EXTRA_OPEN_INBOX,
                false
            )
    }

    private fun launchTextInput() {
        if (!uiState.textEnabled) {
            return
        }

        val remoteInput =
            RemoteInput.Builder(
                REMOTE_TEXT_KEY
            )
                .setLabel("Message")
                .setAllowFreeFormInput(true)
                .setChoices(
                    arrayOf<CharSequence>(
                        "👍",
                        "❤️",
                        "😂",
                        "👌"
                    )
                )
                .wearableExtender {
                    setEmojisAllowed(true)
                    setInputActionType(
                        EditorInfo.IME_ACTION_SEND
                    )
                }
                .build()

        val intent =
            RemoteInputIntentHelper
                .createActionRemoteInputIntent()

        RemoteInputIntentHelper
            .putRemoteInputsExtra(
                intent,
                listOf(remoteInput)
            )
        RemoteInputIntentHelper
            .putTitleExtra(
                intent,
                "Message ${uiState.peerName}"
            )
        RemoteInputIntentHelper
            .putConfirmLabelExtra(
                intent,
                "Send"
            )
        RemoteInputIntentHelper
            .putCancelLabelExtra(
                intent,
                "Cancel"
            )

        val context =
            uiState.timeline
                .filter {
                    it.type ==
                        ConversationItemType.TEXT &&
                        it.direction ==
                            ConversationDirection
                                .INCOMING
                }
                .sortedBy {
                    it.createdAt
                }
                .mapNotNull {
                    it.text
                }
                .takeLast(4)

        if (context.isNotEmpty()) {
            RemoteInputIntentHelper
                .putSmartReplyContextExtra(
                    intent,
                    context
                )
        }

        textInputLauncher.launch(
            intent
        )
    }


    companion object {
        private const val REMOTE_TEXT_KEY =
            "happytalky_text_reply"
    }
}

@Composable
fun WearHome(
    state: HappyTalkyUiState,
    onCall: () -> Unit,
    onDecline: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkFinish: () -> Unit,
    onTalkCancel: () -> Unit,
    onPlay: (VoiceMessage) -> Unit,
    onDelete: (Set<String>) -> Unit = {},
    onComposeText: () -> Unit = {},
    onFindPhone: () -> Unit = {},
    openInbox: Boolean = false,
    onInboxOpened: () -> Unit = {},
) {
    var showInbox by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(openInbox) {
        if (openInbox) {
            showInbox = true
        }
    }

    LaunchedEffect(
        showInbox,
        state.unreadTextCount
    ) {
        if (
            showInbox &&
            state.unreadTextCount > 0
        ) {
            onInboxOpened()
        }
    }

    if (
        state.callState ==
            CallVisualState.INCOMING
    ) {
        WearIncomingCallScreen(
            peerName = state.peerName,
            priority =
                state.callMode ==
                    CallMode.PRIORITY,
            locked =
                state.priorityLocked,
            onAnswer = onCall,
            onDecline = onDecline,
        )
        return
    }

    if (showInbox) {
        WearInbox(
            messages = state.messages,
            callHistory = state.callHistory,
            timeline = state.timeline,
            unreadCount =
                state.unreadVoiceCount +
                    state.unreadTextCount,
            peerName = state.peerName,
            textEnabled =
                state.textEnabled,
            onComposeText =
                onComposeText,
            onBack = {
                showInbox = false
            },
            onPlay = onPlay,
            onDelete = onDelete,
        )
        return
    }

    WearHomePage(
        state = state,
        onCall = onCall,
        onTalkStart = onTalkStart,
        onTalkFinish = onTalkFinish,
        onTalkCancel = onTalkCancel,
        onOpenInbox = {
            showInbox = true
        },
        onFindPhone = onFindPhone,
    )
}

@Composable
private fun WearHomePage(
    state: HappyTalkyUiState,
    onCall: () -> Unit,
    onTalkStart: () -> Unit,
    onTalkFinish: () -> Unit,
    onTalkCancel: () -> Unit,
    onOpenInbox: () -> Unit,
    onFindPhone: () -> Unit,
) {
    var horizontalDrag by remember {
        mutableFloatStateOf(0f)
    }
    val thresholdPx =
        with(LocalDensity.current) {
            42.dp.toPx()
        }

    AppScaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .align(
                        Alignment.TopCenter
                    )
                    .width(154.dp)
                    .padding(top = 18.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                horizontalDrag =
                                    0f
                            },
                            onHorizontalDrag = {
                                    change,
                                    amount ->
                                change.consume()
                                horizontalDrag +=
                                    amount
                            },
                            onDragEnd = {
                                if (
                                    horizontalDrag <
                                        -thresholdPx
                                ) {
                                    onOpenInbox()
                                }
                                horizontalDrag = 0f
                            },
                            onDragCancel = {
                                horizontalDrag = 0f
                            }
                        )
                    },
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(
                        5.dp
                    ),
            ) {
                RouteStatus(state)

                PrimaryCallAction(
                    state = state,
                    onCall = onCall,
                )

                QuickActionsRow(
                    unread =
                        state.unreadVoiceCount +
                            state.unreadTextCount,
                    onInbox =
                        onOpenInbox,
                    onFindPhone =
                        onFindPhone,
                    findPhoneEnabled =
                        state.callState ==
                            CallVisualState.READY &&
                            !state.recording &&
                            state.peerConnection ==
                                PeerConnectionState.CONNECTED,
                )

            }

            TalkHoldButton(
                state = state,
                onStart = onTalkStart,
                onFinish = onTalkFinish,
                onCancel = onTalkCancel,
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .width(142.dp)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun WearIncomingCallScreen(
    peerName: String,
    priority: Boolean = false,
    locked: Boolean = false,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    if (locked) {
        WearLockedPriorityIncomingScreen(
            peerName = peerName
        )
        return
    }

    AppScaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = 18.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        if (priority) {
                            "PRIORITY CALL"
                        } else {
                            "INCOMING CALL"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    color =
                        if (priority) {
                            Color(0xFFFFD35A)
                        } else {
                            Color(0xFF8CC0FF)
                        },
                    fontWeight =
                        FontWeight.Bold,
                )
                Spacer(
                    Modifier.height(6.dp)
                )
                Text(
                    text = peerName,
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center,
                )
            }

            Icon(
                imageVector =
                    Icons.Rounded.Call,
                contentDescription = null,
                modifier =
                    Modifier.size(42.dp),
                tint =
                    Color(0xFF76DCA5),
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    ),
            ) {
                CallCircleButton(
                    text = "NO",
                    icon =
                        Icons.Rounded
                            .CallEnd,
                    color =
                        Color(0xFFD9485E),
                    onClick = onDecline,
                )

                CallCircleButton(
                    text = "YES",
                    icon =
                        Icons.Rounded.Call,
                    color =
                        Color(0xFF1DAA6B),
                    onClick = onAnswer,
                )
            }
        }
    }
}

@Composable
private fun WearLockedPriorityIncomingScreen(
    peerName: String,
) {
    AppScaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 26.dp,
                    bottom = 18.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    5.dp,
                    Alignment.CenterVertically
                ),
        ) {
            Text(
                text = "PRIORITY CALL",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                color =
                    Color(0xFFFFD35A),
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
            )

            Text(
                text = peerName,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
            )

            Icon(
                imageVector =
                    Icons.Rounded.Call,
                contentDescription = null,
                modifier =
                    Modifier.size(30.dp),
                tint =
                    Color(0xFF76DCA5),
            )

            Text(
                text = "AUTO CONNECT",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                color =
                    Color(0xFFFFD35A),
                fontWeight =
                    FontWeight.Bold,
                maxLines = 1,
            )

            Text(
                text = "Phone ends call",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    Color(0xFF8E9AAF),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CallCircleButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        label = {
            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier =
                        Modifier.size(19.dp),
                )
                Text(
                    text = text,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    fontWeight =
                        FontWeight.Bold,
                )
            }
        },
        colors =
            ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White,
            ),
        modifier =
            Modifier.size(62.dp),
    )
}

@Composable
private fun RouteStatus(state: HappyTalkyUiState) {
    val routeColor =
        when (state.peerRoute) {
            PeerRoute.NEARBY_DIRECT -> Color(0xFF8CC0FF)
            PeerRoute.REMOTE_WIFI -> Color(0xFF71D7FF)
            PeerRoute.REMOTE_CELLULAR -> Color(0xFF8EE0A8)
            PeerRoute.REMOTE_INTERNET -> Color(0xFFC0CCE0)
            PeerRoute.RECONNECTING -> Color(0xFFFFD35A)
            PeerRoute.OFFLINE -> Color(0xFFFF7D8D)
            PeerRoute.UNKNOWN -> Color(0xFF8E9AAF)
        }

    Row(
        modifier = Modifier.width(136.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = wearRouteIcon(state.peerRoute),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = routeColor,
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = wearRouteLabel(state),
            style = MaterialTheme.typography.labelSmall,
            color = routeColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PrimaryCallAction(
    state: HappyTalkyUiState,
    onCall: () -> Unit,
) {
    val lockedCall =
        state.priorityLocked &&
            state.callState !=
                CallVisualState.READY

    val container =
        if (lockedCall) {
            Color(0xFFFFA000)
        } else {
            when (state.callState) {
                CallVisualState.LIVE,
                CallVisualState.OUTGOING,
                CallVisualState.CONNECTING,
                CallVisualState.RECONNECTING ->
                    Color(0xFFD9485E)

                CallVisualState.INCOMING ->
                    Color(0xFF1DAA6B)

                CallVisualState.READY ->
                    Color(0xFF176DFF)
            }
        }

    Button(
        onClick = onCall,
        enabled =
            state.callEnabled &&
                !lockedCall,
        label = {
            if (lockedCall) {
                Icon(
                    imageVector =
                        Icons.Rounded.Lock,
                    contentDescription =
                        "Locked priority call · phone ends call",
                    modifier =
                        Modifier.size(26.dp),
                )
            } else {
                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector =
                            when (state.callState) {
                                CallVisualState.LIVE,
                                CallVisualState.OUTGOING,
                                CallVisualState.CONNECTING,
                                CallVisualState.RECONNECTING ->
                                    Icons.Rounded.CallEnd

                                CallVisualState.INCOMING,
                                CallVisualState.READY ->
                                    Icons.Rounded.Call
                            },
                        contentDescription = null,
                        modifier =
                            Modifier.size(20.dp),
                    )
                    Text(
                        text =
                            wearCallLabel(state),
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium,
                        fontWeight =
                            FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor =
                if (lockedCall) {
                    Color(0xFFFFA000)
                } else {
                    Color(0xFF1C293C)
                },
            disabledContentColor =
                if (lockedCall) {
                    Color(0xFF221500)
                } else {
                    Color(0xFF7D8A9F)
                },
        ),
        modifier =
            if (lockedCall) {
                Modifier
                    .width(92.dp)
                    .height(66.dp)
            } else {
                Modifier.size(66.dp)
            },
    )
}

@Composable
private fun QuickActionsRow(
    unread: Int,
    onInbox: () -> Unit,
    onFindPhone: () -> Unit,
    findPhoneEnabled: Boolean,
) {
    Row(
        modifier =
            Modifier
                .width(154.dp)
                .height(28.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onInbox,
            modifier =
                Modifier
                    .width(76.dp)
                    .height(28.dp),
        ) {
            Text(
                text =
                    if (unread > 0) {
                        "Inbox " +
                            unread.toString()
                    } else {
                        "Inbox"
                    },
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                fontWeight =
                    if (unread > 0) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                color =
                    if (unread > 0) {
                        Color(0xFFFFD35A)
                    } else {
                        Color(0xFF8E9AAF)
                    },
                maxLines = 1,
            )
        }

        TextButton(
            onClick = onFindPhone,
            enabled = findPhoneEnabled,
            modifier =
                Modifier
                    .width(76.dp)
                    .height(28.dp)
                    .semantics {
                        contentDescription =
                            "Find phone"
                    },
        ) {
            Text(
                text = "Find phone",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    if (findPhoneEnabled) {
                        Color(0xFF8CC0FF)
                    } else {
                        Color(0xFF536176)
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LastCallSummary(
    entry: CallHistoryEntry
) {
    val detail =
        when (entry.outcome) {
            CallOutcome.COMPLETED ->
                if (entry.durationMs > 0L) {
                    "Last " +
                        entry.displayTime() +
                        " · " +
                        entry.displayDuration()
                } else {
                    "Last call · " +
                        entry.displayTime()
                }

            CallOutcome.DECLINED_BY_ME ->
                "Declined · " +
                    entry.displayTime()

            CallOutcome.DECLINED_BY_PEER ->
                "Rejected · " +
                    entry.displayTime()

            CallOutcome.NO_ANSWER ->
                "No answer · " +
                    entry.displayTime()

            CallOutcome.MISSED ->
                "Missed · " +
                    entry.displayTime()

            CallOutcome.CANCELLED_BY_ME ->
                "Cancelled · " +
                    entry.displayTime()

            CallOutcome.CANCELLED_BY_PEER ->
                "Peer cancel · " +
                    entry.displayTime()

            CallOutcome.BUSY ->
                "Busy · " +
                    entry.displayTime()

            CallOutcome.FAILED ->
                "Failed · " +
                    entry.displayTime()

            CallOutcome.DISCONNECTED ->
                "Dropped · " +
                    entry.displayTime()
        }

    Text(
        text = detail,
        style =
            MaterialTheme
                .typography
                .labelSmall,
        color = Color(0xFF7F8DA2),
        maxLines = 1,
        overflow =
            TextOverflow.Ellipsis,
        modifier =
            Modifier.width(142.dp),
        textAlign =
            TextAlign.Center,
    )
}

@Composable
private fun TalkHoldButton(
    state: HappyTalkyUiState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.talkEnabled
    val recording = state.recording

    val container =
        when {
            !enabled ->
                Color(0xFF182638)

            recording ->
                Color(0xFFE4475E)

            else ->
                Color(0xFF0F89FF)
        }

    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                color = container,
                shape =
                    RoundedCornerShape(
                        24.dp
                    )
            )
            .semantics {
                role = Role.Button
                contentDescription =
                    if (recording) {
                        "Release to send TALK"
                    } else {
                        "Hold to record TALK"
                    }
            }
            .pointerInput(
                enabled
            ) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) {
                            return@detectTapGestures
                        }

                        onStart()

                        val released =
                            tryAwaitRelease()

                        if (released) {
                            onFinish()
                        } else {
                            onCancel()
                        }
                    }
                )
            },
        contentAlignment =
            Alignment.Center,
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.Center,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.Mic,
                contentDescription = null,
                modifier =
                    Modifier.size(18.dp),
                tint =
                    if (enabled) {
                        Color.White
                    } else {
                        Color(0xFF75849A)
                    },
            )
            Spacer(
                Modifier.width(5.dp)
            )
            Text(
                text =
                    when {
                        !enabled ->
                            "TALK"

                        recording ->
                            "RELEASE"

                        else ->
                            "HOLD TALK"
                    },
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.Bold,
                color =
                    if (enabled) {
                        Color.White
                    } else {
                        Color(0xFF75849A)
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
fun WearInbox(
    messages: List<VoiceMessage>,
    callHistory: List<CallHistoryEntry> =
        emptyList(),
    timeline: List<ConversationItem> =
        emptyList(),
    unreadCount: Int = 0,
    peerName: String,
    textEnabled: Boolean = false,
    onComposeText: () -> Unit = {},
    onBack: () -> Unit,
    onPlay: (VoiceMessage) -> Unit,
    onDelete: (Set<String>) -> Unit = {},
) {
    val voiceById =
        remember(messages) {
            messages.associateBy {
                it.id
            }
        }
    val callById =
        remember(callHistory) {
            callHistory.associateBy {
                it.id
            }
        }
    val displayTimeline =
        remember(
            timeline,
            messages,
            callHistory
        ) {
            if (timeline.isNotEmpty()) {
                timeline.sortedByDescending {
                    it.createdAt
                }
            } else {
                buildList {
                    messages.forEach {
                        add(
                            ConversationItem(
                                id = it.id,
                                type =
                                    ConversationItemType.VOICE,
                                direction =
                                    if (
                                        it.direction ==
                                            VoiceDirection.INCOMING
                                    ) {
                                        ConversationDirection.INCOMING
                                    } else {
                                        ConversationDirection.OUTGOING
                                    },
                                createdAt =
                                    it.createdAt,
                                readAt =
                                    it.readAt,
                                deliveryState =
                                    it.deliveryState,
                                audioFileName =
                                    it.file.name,
                                durationMs =
                                    it.durationMs
                            )
                        )
                    }

                    callHistory.forEach {
                        add(
                            ConversationItem(
                                id = it.id,
                                type =
                                    ConversationItemType.CALL,
                                direction =
                                    if (
                                        it.direction ==
                                            CallDirection.INCOMING
                                    ) {
                                        ConversationDirection.INCOMING
                                    } else {
                                        ConversationDirection.OUTGOING
                                    },
                                createdAt =
                                    it.occurredAt,
                                readAt =
                                    it.occurredAt,
                                callId =
                                    it.callId,
                                callOutcome =
                                    it.outcome.name,
                                callMode =
                                    it.mode,
                                durationMs =
                                    it.durationMs,
                                startedAt =
                                    it.startedAt,
                                endedAt =
                                    it.endedAt
                            )
                        )
                    }
                }.sortedByDescending {
                    it.createdAt
                }
            }
        }

    var deleteConfirmation by
        remember {
            mutableStateOf<String?>(null)
        }

    LaunchedEffect(
        deleteConfirmation
    ) {
        if (deleteConfirmation != null) {
            delay(1_500L)
            deleteConfirmation = null
        }
    }

    val listState =
        rememberLazyListState()
    val focusRequester =
        remember {
            FocusRequester()
        }
    val scope =
        rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AppScaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = 64.dp
                    )
                    .focusRequester(
                        focusRequester
                    )
                    .onRotaryScrollEvent {
                            event ->
                        scope.launch {
                            listState.scrollBy(
                                event.verticalScrollPixels
                            )
                        }
                        true
                    }
                    .focusable(),
                contentPadding =
                    PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 18.dp,
                        bottom = 8.dp,
                    ),
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                item(
                    key = "inbox-header"
                ) {
                    Row(
                        modifier = Modifier
                            .width(126.dp)
                            .height(24.dp),
                        verticalAlignment =
                            Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .semantics {
                                    role =
                                        Role.Button
                                    contentDescription =
                                        "Back"
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            onBack()
                                        }
                                    )
                                },
                            contentAlignment =
                                Alignment.CenterStart,
                        ) {
                            Text(
                                text =
                                    if (
                                        unreadCount > 0
                                    ) {
                                        "‹ Inbox"
                                    } else {
                                        "‹ Inbox"
                                    },
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium,
                                fontWeight =
                                    if (
                                        unreadCount > 0
                                    ) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.SemiBold
                                    },
                                color =
                                    if (
                                        unreadCount > 0
                                    ) {
                                        Color(
                                            0xFFFFD35A
                                        )
                                    } else {
                                        Color.White
                                    },
                                maxLines = 1,
                                overflow =
                                    TextOverflow.Ellipsis,
                            )
                        }

                    }
                }



                if (
                    displayTimeline.isEmpty()
                ) {
                    item {
                        Text(
                            text =
                                "No messages yet",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium,
                            color =
                                Color(0xFF8C9AAF),
                            textAlign =
                                TextAlign.Center,
                        )
                    }
                } else {
                    items(
                        items =
                            displayTimeline,
                        key = {
                            it.id
                        },
                    ) { item ->
                        when (item.type) {
                            ConversationItemType.VOICE -> {
                                voiceById[item.id]
                                    ?.let { message ->
                                        SwipeDeleteRow(
                                            itemId =
                                                item.id,
                                            deleteDescription =
                                                "Delete TALK",
                                            rowHeight =
                                                54.dp,
                                            onDelete = {
                                                onDelete(
                                                    setOf(
                                                        item.id
                                                    )
                                                )
                                                deleteConfirmation =
                                                    "TALK deleted ✓"
                                            },
                                        ) {
                                            WearTalkMessage(
                                                message =
                                                    message,
                                                peerName =
                                                    peerName,
                                                onPlay =
                                                    onPlay,
                                            )
                                        }
                                    }
                            }

                            ConversationItemType.TEXT -> {
                                SwipeDeleteRow(
                                    itemId =
                                        item.id,
                                    deleteDescription =
                                        "Delete message",
                                    rowHeight =
                                        54.dp,
                                    onDelete = {
                                        onDelete(
                                            setOf(
                                                item.id
                                            )
                                        )
                                        deleteConfirmation =
                                            "Message deleted ✓"
                                    },
                                ) {
                                    WearTextMessage(
                                        item = item,
                                        peerName =
                                            peerName,
                                    )
                                }
                            }

                            ConversationItemType.CALL -> {
                                callById[item.id]
                                    ?.let { entry ->
                                        SwipeDeleteRow(
                                            itemId =
                                                item.id,
                                            deleteDescription =
                                                "Delete call",
                                            rowHeight =
                                                48.dp,
                                            onDelete = {
                                                onDelete(
                                                    setOf(
                                                        item.id
                                                    )
                                                )
                                                deleteConfirmation =
                                                    "Call deleted ✓"
                                            },
                                        ) {
                                            WearCallHistoryCard(
                                                entry =
                                                    entry
                                            )
                                        }
                                    }
                            }
                        }
                    }
                }

            }

            WearTextComposer(
                enabled = textEnabled,
                onCompose = onComposeText,
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter
                    )
                    .padding(
                        bottom = 28.dp
                    ),
            )

            deleteConfirmation
                ?.let { label ->
                    WearDeleteConfirmation(
                        label = label,
                        modifier =
                            Modifier.align(
                                Alignment.Center
                            )
                    )
                }
        }
    }
}

@Composable
internal fun WearDeleteConfirmation(
    label: String = "TALK deleted ✓",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .zIndex(5f)
            .background(
                Color(0xFF173B2A),
                RoundedCornerShape(14.dp)
            )
            .padding(
                horizontal = 10.dp,
                vertical = 6.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            color =
                Color(0xFFB9F6CA),
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun WearTextMessage(
    item: ConversationItem,
    peerName: String,
) {
    val outgoing =
        item.direction ==
            ConversationDirection.OUTGOING
    val text =
        item.text
            ?: return

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(54.dp)
            .background(
                if (outgoing) {
                    Color(0xFF173B63)
                } else {
                    Color(0xFF121D2E)
                },
                RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp,
            ),
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize(),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        if (outgoing) {
                            "Me"
                        } else {
                            peerName
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        if (outgoing) {
                            Color(0xFF8CC0FF)
                        } else {
                            Color(0xFF98A7BC)
                        },
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis,
                    modifier =
                        Modifier.weight(1f),
                )

                Spacer(
                    Modifier.width(5.dp)
                )

                Text(
                    text =
                        wearMessageTime(
                            item.createdAt
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        Color(0xFF7F8DA2),
                    maxLines = 1,
                )
            }

            Text(
                text = text,
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                color = Color.White,
                maxLines = 2,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WearTextComposer(
    enabled: Boolean,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (enabled) {
            Color(0xFF173B63)
        } else {
            Color(0xFF111A29)
        }

    Box(
        modifier = modifier
            .zIndex(3f)
            .width(126.dp)
            .height(32.dp)
            .background(
                container,
                RoundedCornerShape(18.dp)
            )
            .semantics {
                role = Role.Button
                contentDescription =
                    if (enabled) {
                        "Compose message"
                    } else {
                        "Text unavailable"
                    }
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = {
                        if (enabled) {
                            onCompose()
                        }
                    }
                )
            }
            .padding(
                horizontal = 11.dp
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (enabled) {
                        "Message…"
                    } else {
                        "Text off"
                    },
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    if (enabled) {
                        Color.White
                    } else {
                        Color(0xFF75849A)
                    },
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )

            if (enabled) {
                Text(
                    text = "😊",
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                )
            }
        }
    }
}

private fun wearMessageTime(
    createdAt: Long
): String =
    java.text.SimpleDateFormat(
        "HH:mm",
        java.util.Locale.getDefault()
    ).format(
        java.util.Date(
            createdAt
        )
    )

@Composable
private fun SwipeDeleteRow(
    itemId: String,
    deleteDescription: String,
    rowHeight: Dp,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val revealPx =
        with(LocalDensity.current) {
            58.dp.toPx()
        }

    var offsetX by
        remember(itemId) {
            mutableFloatStateOf(0f)
        }

    Box(
        modifier = Modifier
            .width(146.dp)
            .height(rowHeight),
        contentAlignment =
            Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (offsetX < -1f) {
                        Color(0xFF8D2634)
                    } else {
                        Color.Transparent
                    },
                    RoundedCornerShape(
                        22.dp
                    )
                ),
            contentAlignment =
                Alignment.CenterEnd,
        ) {
            TextButton(
                onClick = {
                    onDelete()
                    offsetX = 0f
                },
                modifier =
                    Modifier.width(58.dp),
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Delete,
                    contentDescription =
                        deleteDescription,
                    tint = Color.White,
                    modifier =
                        Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x =
                            offsetX
                                .roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(
                    itemId
                ) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = {
                                change,
                                amount ->
                            change.consume()
                            offsetX =
                                (
                                    offsetX +
                                        amount
                                    ).coerceIn(
                                    -revealPx,
                                    0f
                                )
                        },
                        onDragEnd = {
                            offsetX =
                                if (
                                    offsetX <
                                        -revealPx /
                                            2f
                                ) {
                                    -revealPx
                                } else {
                                    0f
                                }
                        },
                        onDragCancel = {
                            offsetX = 0f
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun WearTalkMessage(
    message: VoiceMessage,
    peerName: String,
    onPlay: (VoiceMessage) -> Unit,
) {
    val sender =
        if (
            message.direction ==
                VoiceDirection.OUTGOING
        ) {
            "Me"
        } else {
            peerName
        }

    val unread =
        message.direction ==
            VoiceDirection.INCOMING &&
            !message.isRead

    Card(
        onClick = {
            onPlay(message)
        },
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (unread) {
                        Color(0xFF173B63)
                    } else {
                        Color(0xFF121D2E)
                    },
                contentColor =
                    Color.White,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(20.dp),
                contentAlignment =
                    Alignment.Center,
            ) {
                if (unread) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Color(
                                        0xFF2A8CFF
                                    ),
                                    CircleShape
                                )
                    )
                }

                Icon(
                    imageVector =
                        Icons.Rounded
                            .PlayArrow,
                    contentDescription =
                        "Play TALK",
                    modifier =
                        Modifier.size(17.dp),
                    tint =
                        if (unread) {
                            Color.White
                        } else {
                            Color(
                                0xFF70C8FF
                            )
                        },
                )
            }

            Spacer(
                Modifier.size(6.dp)
            )

            Column(
                modifier =
                    Modifier.width(96.dp)
            ) {
                Text(
                    text =
                        if (unread) {
                            sender + " · NEW"
                        } else {
                            sender
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    fontWeight =
                        if (unread) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    maxLines = 1,
                )
                Text(
                    text =
                        if (
                            message.durationMs >
                                0L
                        ) {
                            message
                                .displayDuration() +
                                " · " +
                                message
                                    .displayTime()
                        } else {
                            message.displayTime()
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        if (unread) {
                            Color(
                                0xFFCEE7FF
                            )
                        } else {
                            Color(
                                0xFF98A7BC
                            )
                        },
                    fontWeight =
                        if (unread) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WearCallHistoryCard(
    entry: CallHistoryEntry
) {
    val accent =
        when (entry.outcome) {
            CallOutcome.COMPLETED ->
                Color(0xFF71DFA4)

            else ->
                Color(0xFFFF8C9B)
        }

    Card(
        onClick = {},
        modifier = Modifier
            .width(146.dp)
            .height(48.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF111A29),
                contentColor =
                    Color.White,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 8.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.Call,
                contentDescription = null,
                tint = accent,
                modifier =
                    Modifier.size(18.dp),
            )
            Spacer(
                Modifier.width(7.dp)
            )
            Column {
                Text(
                    text =
                        if (
                            entry.direction ==
                                CallDirection
                                    .OUTGOING
                        ) {
                            "Outgoing"
                        } else {
                            "Incoming"
                        },
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    fontWeight =
                        FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text =
                        entry.shortLabel() +
                            " · " +
                            entry.displayTime(),
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        Color(0xFF98A7BC),
                    maxLines = 1,
                    overflow =
                        TextOverflow
                            .Ellipsis,
                )
            }
        }
    }
}

private fun wearRouteIcon(route: PeerRoute): ImageVector =
    when (route) {
        PeerRoute.NEARBY_DIRECT -> Icons.Rounded.Link
        PeerRoute.REMOTE_WIFI -> Icons.Rounded.Wifi
        PeerRoute.REMOTE_CELLULAR -> Icons.Rounded.NetworkCell
        PeerRoute.REMOTE_INTERNET -> Icons.Rounded.Cloud
        PeerRoute.RECONNECTING -> Icons.Rounded.Cloud
        PeerRoute.OFFLINE -> Icons.Rounded.CloudOff
        PeerRoute.UNKNOWN -> Icons.Rounded.Cloud
    }

private fun wearRouteLabel(state: HappyTalkyUiState): String =
    when {
        state.callState == CallVisualState.RECONNECTING ->
            "Reconnecting"

        state.peerConnection == PeerConnectionState.DISCONNECTED ->
            "Offline · TALK"

        state.peerRoute == PeerRoute.NEARBY_DIRECT ->
            "Nearby · CALL"

        state.peerRoute == PeerRoute.REMOTE_WIFI ->
            "Wi-Fi · CALL"

        state.peerRoute == PeerRoute.REMOTE_CELLULAR ->
            "Cell · CALL"

        state.peerRoute == PeerRoute.REMOTE_INTERNET ->
            "Remote · CALL"

        else ->
            "Checking"
    }

private fun wearCallLabel(
    state: HappyTalkyUiState
): String =
    if (
        state.priorityLocked &&
        state.callState !=
            CallVisualState.READY
    ) {
        "LOCKED"
    } else {
        when (state.callState) {
            CallVisualState.LIVE -> "END"
            CallVisualState.INCOMING -> "ANSWER"
            CallVisualState.OUTGOING -> "CANCEL"
            CallVisualState.CONNECTING -> "END"
            CallVisualState.RECONNECTING -> "END"
            CallVisualState.READY -> "CALL"
        }
    }
