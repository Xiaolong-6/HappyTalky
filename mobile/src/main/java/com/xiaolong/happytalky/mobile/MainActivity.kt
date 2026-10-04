package com.xiaolong.happytalky.mobile

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.delay
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NetworkCell
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaolong.happytalky.core.AudioPlayer
import com.xiaolong.happytalky.core.CallHistoryEntry
import com.xiaolong.happytalky.core.CallMode
import com.xiaolong.happytalky.core.CallOutcome
import com.xiaolong.happytalky.core.CallVisualState
import com.xiaolong.happytalky.core.ConversationDirection
import com.xiaolong.happytalky.core.ConversationItem
import com.xiaolong.happytalky.core.ConversationItemType
import com.xiaolong.happytalky.core.DeliveryState
import com.xiaolong.happytalky.core.HappyTalkyActivity
import com.xiaolong.happytalky.core.HappyTalkyUiState
import com.xiaolong.happytalky.core.PeerConnectionState
import com.xiaolong.happytalky.core.PeerRoute
import com.xiaolong.happytalky.core.Protocol
import com.xiaolong.happytalky.core.TextMessageStore
import com.xiaolong.happytalky.core.VoiceDirection
import com.xiaolong.happytalky.core.VoiceMessage

class MainActivity : HappyTalkyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HappyTalkyPhoneTheme {
                HappyTalkyPhoneScreen(
                    state = uiState,
                    onCall = ::handleCallAction,
                    onDecline = ::declineIncomingCall,
                    onSpeakerToggle = ::toggleSpeaker,
                    onPriorityCall = ::requestPriorityCall,
                    onFindWatch = {
                        startActivity(
                            Intent(
                                this,
                                FindWatchActivity::class.java
                            )
                        )
                    },
                    onSendText = ::sendText,
                    onTextVisible =
                        ::markTextMessagesRead,
                    onTalkStart = ::beginTalk,
                    onTalkFinish = ::finishTalk,
                    onTalkCancel = ::cancelTalk,
                    onPlay = ::playMessage,
                    onDelete = ::deleteMessages,
                    onClear = ::clearMessages,
                )
            }
        }
    }
}

private val BrandBlue = Color(0xFF1976F3)
private val CallRed = Color(0xFFE5485D)
private val CallGreen = Color(0xFF1FA66C)

private val PhoneLightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FF),
    onPrimaryContainer = Color(0xFF002E68),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF15171B),
    surface = Color.White,
    onSurface = Color(0xFF15171B),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF626975),
    outline = Color(0xFFD8DEE8),
    error = Color(0xFFB3261E),
)

private val PhoneDarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF164A86),
    onPrimaryContainer = Color(0xFFD7E6FF),
    background = Color(0xFF0A0B0E),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF14161B),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF202329),
    onSurfaceVariant = Color(0xFFB7BEC9),
    outline = Color(0xFF343943),
    error = Color(0xFFFFB4AB),
)

@Composable
fun HappyTalkyPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme =
            if (darkTheme) {
                PhoneDarkColors
            } else {
                PhoneLightColors
            },
        typography = Typography(),
        content = content,
    )
}

@Composable
fun HappyTalkyPhoneScreen(
    state: HappyTalkyUiState,
    onCall: () -> Unit,
    onDecline: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onPriorityCall: () -> Unit = {},
    onFindWatch: () -> Unit = {},
    onSendText: (String) -> Unit = {},
    onTextVisible: () -> Unit = {},
    onTalkStart: () -> Unit,
    onTalkFinish: () -> Unit,
    onTalkCancel: () -> Unit,
    onPlay: (VoiceMessage) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onClear: () -> Unit,
) {
    var showPriorityOptions by rememberSaveable { mutableStateOf(false) }
    if (showPriorityOptions) {
        PriorityCallOptions(
            state = state,
            onDismiss = {
                showPriorityOptions = false
            },
        )
    }
    var selectedIds by remember {
        mutableStateOf(emptySet<String>())
    }

    LaunchedEffect(
        state.unreadTextCount
    ) {
        if (state.unreadTextCount > 0) {
            onTextVisible()
        }
    }

    val messages =
        remember(state.messages) {
            state.messages.sortedBy {
                it.createdAt
            }
        }
    val voiceById =
        remember(messages) {
            messages.associateBy {
                it.id
            }
        }
    val callById =
        remember(state.callHistory) {
            state.callHistory.associateBy {
                it.id
            }
        }
    val timeline =
        remember(
            state.timeline,
            messages
        ) {
            if (state.timeline.isNotEmpty()) {
                state.timeline.sortedBy {
                    it.createdAt
                }
            } else {
                messages.map {
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
                        createdAt = it.createdAt,
                        readAt = it.readAt,
                        deliveryState =
                            it.deliveryState,
                        audioFileName =
                            it.file.name,
                        durationMs =
                            it.durationMs
                    )
                }
            }
        }

    val listState =
        rememberLazyListState()

    val hasCallEvent =
        state.callState !=
            CallVisualState.READY

    LaunchedEffect(
        timeline.size,
        state.callState
    ) {
        val itemCount =
            timeline.size +
                if (hasCallEvent) 1 else 0

        if (itemCount > 0) {
            listState.scrollToItem(
                itemCount - 1
            )
        }
    }

    LaunchedEffect(
        state.unreadTextCount
    ) {
        if (state.unreadTextCount > 0) {
            onTextVisible()
        }
    }

    Scaffold(
        containerColor =
            MaterialTheme.colorScheme.background,
        contentWindowInsets =
            WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selectedIds.isEmpty()) {
                ConversationHeader(
                    state = state,
                    onPriorityAction = {
                        if (
                            state.priorityCallAvailable
                        ) {
                            onPriorityCall()
                        } else {
                            showPriorityOptions =
                                true
                        }
                    },
                    onFindWatch =
                        onFindWatch,
                )
            } else {
                SelectionHeader(
                    selectedCount =
                        selectedIds.size,
                    totalCount =
                        messages.size,
                    onClose = {
                        selectedIds =
                            emptySet()
                    },
                    onSelectAll = {
                        selectedIds =
                            messages
                                .map { it.id }
                                .toSet()
                    },
                    onDelete = {
                        val allSelected =
                            selectedIds.size ==
                                messages.size &&
                                messages.isNotEmpty()

                        if (allSelected) {
                            onClear()
                        } else {
                            onDelete(
                                selectedIds
                            )
                        }

                        selectedIds =
                            emptySet()
                    },
                )
            }
        },
        bottomBar = {
            ConversationActions(
                state = state,
                onCall = onCall,
                onDecline = onDecline,
                onSpeakerToggle =
                    onSpeakerToggle,
                onPriorityCall =
                    onPriorityCall,
                onSendText =
                    onSendText,
                onTalkStart =
                    onTalkStart,
                onTalkFinish =
                    onTalkFinish,
                onTalkCancel =
                    onTalkCancel,
            )
        },
    ) { innerPadding ->
        ConversationTimeline(
            timeline = timeline,
            voiceById = voiceById,
            callById = callById,
            peerName = displayPeerName(state.peerName),
            state = state,
            selectedIds = selectedIds,
            listState = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            onMessageTap = { message ->
                if (
                    selectedIds.isEmpty()
                ) {
                    onPlay(message)
                } else {
                    selectedIds =
                        toggleSelection(
                            selectedIds,
                            message.id
                        )
                }
            },
            onMessageLongPress = {
                    message ->
                selectedIds =
                    toggleSelection(
                        selectedIds,
                        message.id
                    )
            },
        )
    }
}

@Composable
private fun ConversationHeader(
    state: HappyTalkyUiState,
    onPriorityAction: () -> Unit,
    onFindWatch: () -> Unit,
) {
    Surface(
        color =
            MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp,
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape =
                    RoundedCornerShape(13.dp),
                color = BrandBlue,
            ) {
                Image(
                    painter = painterResource(
                        com.xiaolong.happytalky.core.R.drawable.ic_happytalky_brand
                    ),
                    contentDescription =
                        "HappyTalky",
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {
                Text(
                    text = displayPeerName(state.peerName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onBackground,
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                routeStatusColor(
                                    state
                                ),
                                CircleShape,
                            )
                    )
                    Spacer(
                        Modifier.width(6.dp)
                    )
                    Text(
                        text =
                            headerStatusText(
                                state
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant,
                        maxLines = 1,
                        overflow =
                            TextOverflow
                                .Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onFindWatch,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.LocationSearching,
                    contentDescription =
                        "Find Watch nearby",
                    tint =
                        MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = onPriorityAction
            ) {
                Text(
                    "Priority\ncall",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = if (state.priorityCallAvailable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color =
            MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(62.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onClose,
                contentPadding =
                    PaddingValues(8.dp),
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Close,
                    contentDescription =
                        "Close selection",
                )
            }

            Text(
                text =
                    "$selectedCount selected",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold,
                modifier =
                    Modifier.weight(1f),
            )

            if (
                selectedCount <
                    totalCount
            ) {
                TextButton(
                    onClick =
                        onSelectAll,
                ) {
                    Text("All")
                }
            }

            TextButton(
                onClick = onDelete,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Delete,
                    contentDescription =
                        "Delete selected",
                    tint =
                        MaterialTheme
                            .colorScheme
                            .error,
                )
            }
        }
    }
}

@Composable
private fun ConversationTimeline(
    timeline: List<ConversationItem>,
    voiceById: Map<String, VoiceMessage>,
    callById: Map<String, CallHistoryEntry>,
    peerName: String,
    state: HappyTalkyUiState,
    selectedIds: Set<String>,
    listState:
        androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onMessageTap: (
        VoiceMessage
    ) -> Unit,
    onMessageLongPress: (
        VoiceMessage
    ) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding =
            PaddingValues(
                horizontal = 14.dp,
                vertical = 16.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(5.dp),
    ) {
        if (timeline.isEmpty()) {
            item {
                EmptyConversation(
                    peerName = peerName
                )
            }
        }

        itemsIndexed(
            items = timeline,
            key = { _, item -> item.id },
        ) { index, item ->
            val previous = timeline.getOrNull(index - 1)
            val showSender = item.direction == ConversationDirection.INCOMING &&
                (previous == null || previous.type == ConversationItemType.CALL ||
                    previous.direction != item.direction ||
                    item.createdAt - previous.createdAt > 5 * 60_000L)
            when (item.type) {
                ConversationItemType.VOICE -> {
                    voiceById[item.id]
                        ?.let { message ->
                            VoiceBubble(
                                message = message,
                                peerName = peerName,
                                showSender = showSender,
                                selected =
                                    message.id in
                                        selectedIds,
                                onTap = {
                                    onMessageTap(
                                        message
                                    )
                                },
                                onLongPress = {
                                    onMessageLongPress(
                                        message
                                    )
                                },
                            )
                        }
                }

                ConversationItemType.TEXT -> {
                    TextBubble(
                        item = item,
                        peerName = peerName,
                        showSender = showSender,
                    )
                }

                ConversationItemType.CALL -> {
                    callById[item.id]
                        ?.let {
                            HistoricalCallEvent(
                                entry = it
                            )
                        }
                }
            }
        }

        if (
            state.callState !=
                CallVisualState.READY
        ) {
            item(
                key = "active-call-state"
            ) {
                ActiveCallEvent(state)
            }
        }
    }
}

@Composable
private fun EmptyConversation(
    peerName: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 90.dp,
                bottom = 70.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.Mic,
                contentDescription = null,
                modifier = Modifier
                    .padding(16.dp)
                    .size(26.dp),
                tint =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text =
                "No messages yet",
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.SemiBold,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text =
                "Hold TALK or send a message to $peerName.",
            style =
                MaterialTheme
                    .typography
                    .bodyMedium,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant,
            textAlign =
                TextAlign.Center,
            modifier =
                Modifier.widthIn(
                    max = 280.dp
                ),
        )
    }
}

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
private fun VoiceBubble(
    message: VoiceMessage,
    peerName: String,
    showSender: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptic =
        LocalHapticFeedback.current
    val outgoing =
        message.direction ==
            VoiceDirection.OUTGOING

    var progress by remember(message.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(message.file) {
        while (true) {
            progress = AudioPlayer.progressFor(message.file)
            delay(150)
        }
    }

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (outgoing) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        Column(
            horizontalAlignment =
                if (outgoing) {
                    Alignment.End
                } else {
                    Alignment.Start
                },
            modifier =
                Modifier.widthIn(
                    max = 300.dp
                ),
        ) {
            if (showSender) {
                Text(
                    text = peerName,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = 8.dp,
                            bottom = 3.dp,
                        ),
                )
            }

            Surface(
                modifier = Modifier
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = {
                            haptic.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )
                            onLongPress()
                        },
                    ),
                shape =
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart =
                            if (outgoing) {
                                20.dp
                            } else {
                                6.dp
                            },
                        bottomEnd =
                            if (outgoing) {
                                6.dp
                            } else {
                                20.dp
                            },
                    ),
                color =
                    when {
                        selected ->
                            MaterialTheme
                                .colorScheme
                                .primaryContainer

                        outgoing ->
                            BrandBlue

                        else ->
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                    },
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 10.dp,
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            when {
                                selected ->
                                    MaterialTheme
                                        .colorScheme
                                        .primary

                                outgoing ->
                                    Color.White
                                        .copy(
                                            alpha =
                                                0.18f
                                        )

                                else ->
                                    MaterialTheme
                                        .colorScheme
                                        .surface
                            },
                    ) {
                        Icon(
                            imageVector =
                                if (selected) {
                                    Icons.Rounded.Check
                                } else {
                                    if (progress != null) Icons.Rounded.VolumeUp
                                    else Icons.Rounded.PlayArrow
                                },
                            contentDescription =
                                if (selected) {
                                    "Selected"
                                } else {
                                    if (progress != null) "Playing TALK; tap to restart" else "Play TALK"
                                },
                            modifier =
                                Modifier
                                    .padding(
                                        9.dp
                                    )
                                    .size(
                                        21.dp
                                    ),
                            tint =
                                if (
                                    outgoing &&
                                    !selected
                                ) {
                                    Color.White
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                },
                        )
                    }

                    Spacer(
                        Modifier.width(10.dp)
                    )

                    Column(modifier = Modifier.widthIn(min = 106.dp)) {
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.width(106.dp).height(3.dp),
                            color = bubbleTextColor(outgoing, selected),
                            trackColor = bubbleTextColor(outgoing, selected).copy(alpha = 0.2f),
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                if (
                                    message
                                        .durationMs >
                                    0L
                                ) {
                                    message
                                        .displayDuration()
                                } else {
                                    "Voice message"
                                },
                            style =
                                MaterialTheme
                                    .typography
                                    .titleSmall,
                            fontWeight =
                                FontWeight
                                    .SemiBold,
                            color =
                                bubbleTextColor(
                                    outgoing,
                                    selected
                                ),
                        )

                        Text(
                            text =
                                message
                                    .displayTime(),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            color =
                                bubbleTextColor(
                                    outgoing,
                                    selected
                                ).copy(
                                    alpha =
                                        0.72f
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun bubbleTextColor(
    outgoing: Boolean,
    selected: Boolean,
): Color =
    when {
        selected ->
            MaterialTheme
                .colorScheme
                .onPrimaryContainer

        outgoing ->
            Color.White

        else ->
            MaterialTheme
                .colorScheme
                .onSurface
    }

@Composable
private fun TextBubble(
    item: ConversationItem,
    peerName: String,
    showSender: Boolean,
) {
    val outgoing =
        item.direction ==
            ConversationDirection.OUTGOING
    val text =
        item.text
            ?: return

    val emojiOnly = isEmojiReply(text)

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            if (outgoing) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
    ) {
        Column(
            horizontalAlignment =
                if (outgoing) {
                    Alignment.End
                } else {
                    Alignment.Start
                },
            modifier =
                Modifier.widthIn(
                    max = 300.dp
                ),
        ) {
            if (showSender) {
                Text(
                    text = peerName,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = 8.dp,
                            bottom = 3.dp,
                        ),
                )
            }

            Surface(
                shape =
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart =
                            if (outgoing) {
                                20.dp
                            } else {
                                6.dp
                            },
                        bottomEnd =
                            if (outgoing) {
                                6.dp
                            } else {
                                20.dp
                            },
                    ),
                color =
                    if (emojiOnly) {
                        Color.Transparent
                    } else if (outgoing) {
                        BrandBlue
                    } else {
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    },
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 9.dp,
                        ),
                ) {
                    Text(
                        text = text,
                        fontSize = if (emojiOnly) 32.sp else 16.sp,
                        lineHeight = if (emojiOnly) 40.sp else 24.sp,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        color =
                            if (outgoing && !emojiOnly) {
                                Color.White
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                            },
                    )

                    Spacer(
                        Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            textMeta(item),
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                        color =
                            if (outgoing && !emojiOnly) {
                                Color.White.copy(
                                    alpha = 0.7f
                                )
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoricalCallEvent(
    entry: CallHistoryEntry
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color =
                MaterialTheme
                    .colorScheme
                    .background,
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 2.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        Icons.Rounded.Call,
                    contentDescription = null,
                    modifier =
                        Modifier.size(15.dp),
                    tint =
                        if (
                            entry.mode ==
                                CallMode.PRIORITY
                        ) {
                            Color(0xFFFFA000)
                        } else {
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                        },
                )
                Spacer(
                    Modifier.width(6.dp)
                )
                Text(
                    text =
                        phoneCallLabel(entry) +
                            " · " +
                            entry.displayTime(),
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

private fun textMeta(
    item: ConversationItem
): String {
    val time =
        java.text.SimpleDateFormat(
            "HH:mm",
            java.util.Locale.getDefault()
        ).format(
            java.util.Date(
                item.createdAt
            )
        )

    if (
        item.direction !=
            ConversationDirection.OUTGOING
    ) {
        return time
    }

    val state =
        when (item.deliveryState) {
            DeliveryState.LOCAL ->
                "Saved"

            DeliveryState.QUEUED ->
                "Queued"

            DeliveryState.SENT ->
                "Sent"

            DeliveryState.DELIVERED ->
                "Delivered"

            DeliveryState.READ ->
                "Read"
        }

    return "$state · $time"
}

@Composable
private fun ActiveCallEvent(
    state: HappyTalkyUiState
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color =
                MaterialTheme
                    .colorScheme
                    .surfaceVariant,
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 7.dp,
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector =
                        currentCallIcon(
                            state
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier.size(16.dp),
                    tint =
                        callStateColor(
                            state
                        ),
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                Text(
                    text =
                        currentCallText(
                            state
                        ),
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConversationActions(
    state: HappyTalkyUiState,
    onCall: () -> Unit,
    onDecline: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onPriorityCall: () -> Unit,
    onSendText: (String) -> Unit,
    onTalkStart: () -> Unit,
    onTalkFinish: () -> Unit,
    onTalkCancel: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.navigationBarsPadding(),
        color =
            MaterialTheme
                .colorScheme
                .surface,
        shadowElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp,
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                when (
                    state.callState
                ) {
                    CallVisualState.INCOMING -> {
                        ActionButton(
                            text = "DECLINE",
                            icon =
                                Icons.Rounded
                                    .CallEnd,
                            enabled = true,
                            container =
                                CallRed,
                            onClick =
                                onDecline,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                        )
    
                        ActionButton(
                            text = "ANSWER",
                            icon =
                                Icons.Rounded
                                    .Call,
                            enabled = true,
                            container =
                                CallGreen,
                            onClick =
                                onCall,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                        )
                    }
    
                    CallVisualState.LIVE -> {
                        ActionButton(
                            text = "END",
                            icon =
                                Icons.Rounded
                                    .CallEnd,
                            enabled = true,
                            container =
                                CallRed,
                            onClick =
                                onCall,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                        )
    
                        ActionButton(
                            text =
                                if (
                                    state
                                        .speakerOn
                                ) {
                                    "SPEAKER ON"
                                } else {
                                    "SPEAKER"
                                },
                            icon =
                                if (
                                    state
                                        .speakerOn
                                ) {
                                    Icons.Rounded
                                        .VolumeUp
                                } else {
                                    Icons.Rounded
                                        .VolumeOff
                                },
                            enabled = true,
                            container =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant,
                            content =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface,
                            onClick =
                                onSpeakerToggle,
                            modifier =
                                Modifier.weight(
                                    1f
                                ),
                        )
                    }
    
                    else -> {
                        ActionButton(
                            text =
                                callActionLabel(
                                    state
                                ),
                            icon =
                                if (
                                    state.callState ==
                                        CallVisualState
                                            .READY
                                ) {
                                    Icons.Rounded
                                        .Call
                                } else {
                                    Icons.Rounded
                                        .CallEnd
                                },
                            enabled =
                                state
                                    .callEnabled,
                            container =
                                if (
                                    state.callState ==
                                        CallVisualState
                                            .READY
                                ) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .error
                                },
                            content = if (state.callState == CallVisualState.READY)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onError,
                            outlined = state.callState == CallVisualState.READY,
                            onClick = onCall,
                            modifier = Modifier.weight(1f),
                        )

                        HoldTalkAction(
                            state = state,
                            onStart =
                                onTalkStart,
                            onFinish =
                                onTalkFinish,
                            onCancel =
                                onTalkCancel,
                            modifier =
                                Modifier.weight(
                                    1.35f
                                ),
                        )
                    }
                }
            }
            PhoneTextComposer(
                enabled = state.textEnabled,
                onSend = onSendText,
                unavailableHint = if (state.peerConnection == PeerConnectionState.DISCONNECTED)
                    "Connect your watch to enable messages" else "Messages unavailable right now",
            )
        }
    }
}

@Composable
private fun PhoneTextComposer(
    enabled: Boolean,
    onSend: (String) -> Unit,
    unavailableHint: String,
) {
    var draft by rememberSaveable {
        mutableStateOf("")
    }

    fun submit() {
        val value =
            draft.trim()
        if (
            !enabled ||
            value.isEmpty()
        ) {
            return
        }

        onSend(value)
        draft = ""
    }

    if (!enabled) {
        Text(
            text = unavailableHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        return
    }

    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft =
                TextMessageStore.limit(
                    it
                )
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                bottom = 10.dp,
            ),
        singleLine = true,
        shape =
            RoundedCornerShape(
                18.dp
            ),
        placeholder = {
            Text(
                if (enabled) {
                    "Message…"
                } else {
                    "Text unavailable"
                }
            )
        },
        keyboardOptions =
            KeyboardOptions(
                imeAction =
                    ImeAction.Send
            ),
        keyboardActions =
            KeyboardActions(
                onSend = {
                    submit()
                }
            ),
        trailingIcon = {
            if (draft.isNotBlank()) {
                IconButton(
                    onClick = {
                        submit()
                    },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector =
                            Icons.Rounded.Send,
                        contentDescription =
                            "Send message",
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        if (enabled) {
                            draft = "😊"
                        }
                    },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector =
                            Icons.Rounded
                                .SentimentSatisfiedAlt,
                        contentDescription =
                            "Add emoji",
                    )
                }
            }
        },
    )
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    container: Color,
    modifier: Modifier = Modifier,
    content: Color = Color.White,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.height(54.dp),
        shape =
            RoundedCornerShape(
                18.dp
            ),
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        container,
                    contentColor =
                        content,
                    disabledContainerColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant,
                    disabledContentColor =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                            .copy(
                                alpha =
                                    0.55f
                            ),
                ),
        contentPadding =
            PaddingValues(
                horizontal = 10.dp
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier.size(19.dp),
        )

        Spacer(
            Modifier.width(7.dp)
        )

        Text(
            text = text,
            style =
                MaterialTheme
                    .typography
                    .labelLarge,
            fontWeight =
                FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HoldTalkAction(
    state: HappyTalkyUiState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled =
        state.talkEnabled
    val recording =
        state.recording

    val container =
        when {
            !enabled ->
                MaterialTheme
                    .colorScheme
                    .surfaceVariant

            recording ->
                CallRed

            else ->
                MaterialTheme
                    .colorScheme
                    .primary
        }

    val content =
        if (enabled) {
            Color.White
        } else {
            MaterialTheme
                .colorScheme
                .onSurfaceVariant
                .copy(alpha = 0.55f)
        }

    Surface(
        modifier = modifier
            .height(54.dp)
            .semantics {
                role = Role.Button
                contentDescription =
                    if (recording) {
                        "Release to send TALK"
                    } else {
                        "Hold to record TALK"
                    }
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            onStart()
                            val released =
                                tryAwaitRelease()

                            if (released) {
                                onFinish()
                            } else {
                                onCancel()
                            }
                        }
                    }
                )
            },
        shape =
            RoundedCornerShape(
                18.dp
            ),
        color = container,
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize(),
            horizontalArrangement =
                Arrangement.Center,
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                imageVector =
                    Icons.Rounded.Mic,
                contentDescription = null,
                modifier =
                    Modifier.size(20.dp),
                tint = content,
            )

            Spacer(
                Modifier.width(7.dp)
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
                        .labelLarge,
                fontWeight =
                    FontWeight.Bold,
                color = content,
                maxLines = 1,
            )
        }
    }
}

private fun toggleSelection(
    current: Set<String>,
    id: String,
): Set<String> =
    if (id in current) {
        current - id
    } else {
        current + id
    }

@Composable
private fun routeStatusColor(
    state: HappyTalkyUiState
): Color =
    when {
        state.callState ==
            CallVisualState.RECONNECTING ->
            Color(0xFFFFB300)

        state.peerConnection ==
            PeerConnectionState.CONNECTED ->
            Color(0xFF28B779)

        state.peerConnection ==
            PeerConnectionState.RECONNECTING ->
            Color(0xFFFFB300)

        state.peerConnection ==
            PeerConnectionState.DISCONNECTED ->
            MaterialTheme
                .colorScheme
                .error

        else ->
            MaterialTheme
                .colorScheme
                .onSurfaceVariant
    }

private fun headerStatusText(
    state: HappyTalkyUiState
): String {
    val route =
        when (state.peerRoute) {
            PeerRoute.NEARBY_DIRECT ->
                "Nearby"

            PeerRoute.REMOTE_WIFI ->
                "Remote Wi-Fi"

            PeerRoute.REMOTE_CELLULAR ->
                "Remote · Cellular"

            PeerRoute.REMOTE_INTERNET ->
                "Remote"

            PeerRoute.RECONNECTING ->
                "Reconnecting"

            PeerRoute.OFFLINE ->
                "Offline"

            PeerRoute.UNKNOWN ->
                "Checking"
        }

    val capability =
        when {
            state.callState ==
                CallVisualState.RECONNECTING ->
                "Restoring call"

            state.callState == CallVisualState.OUTGOING ->
                if (state.callMode == CallMode.PRIORITY) "Priority requested" else "Calling…"

            state.callState == CallVisualState.INCOMING -> "Incoming call"
            state.callState == CallVisualState.CONNECTING -> "Connecting…"
            state.callState == CallVisualState.LIVE -> "In call"
            state.recording -> "Recording voice message"

            state.callEnabled ->
                "Ready to call"

            state.talkEnabled ->
                "TALK available"

            else ->
                "busy"
        }

    return "$route · $capability"
}

private fun callActionLabel(
    state: HappyTalkyUiState
): String =
    when (
        state.callState
    ) {
        CallVisualState.READY ->
            "CALL"

        CallVisualState.OUTGOING ->
            "CANCEL"

        CallVisualState.CONNECTING,
        CallVisualState.RECONNECTING ->
            "END"

        CallVisualState.INCOMING ->
            "ANSWER"

        CallVisualState.LIVE ->
            "END"
    }

private fun currentCallText(
    state: HappyTalkyUiState
): String =
    when (
        state.callState
    ) {
        CallVisualState.INCOMING ->
            "${state.peerName} is calling"

        CallVisualState.OUTGOING ->
            if (
                state.callMode ==
                    CallMode.PRIORITY
            ) {
                "Priority call to ${state.peerName}"
            } else {
                "Calling ${state.peerName}"
            }

        CallVisualState.CONNECTING ->
            if (
                state.callMode ==
                    CallMode.PRIORITY
            ) {
                "Connecting priority call"
            } else {
                "Connecting live audio"
            }

        CallVisualState.RECONNECTING ->
            "Reconnecting call"

        CallVisualState.LIVE ->
            if (
                state.callMode ==
                    CallMode.PRIORITY
            ) {
                "Priority call with ${state.peerName}"
            } else {
                "Live call with ${state.peerName}"
            }

        CallVisualState.READY ->
            ""
    }

private fun currentCallIcon(
    state: HappyTalkyUiState
): ImageVector =
    if (
        state.callState ==
            CallVisualState.LIVE ||
        state.callState ==
            CallVisualState.OUTGOING ||
        state.callState ==
            CallVisualState.CONNECTING ||
        state.callState ==
            CallVisualState.RECONNECTING
    ) {
        Icons.Rounded.CallEnd
    } else {
        Icons.Rounded.Call
    }

@Composable
private fun callStateColor(
    state: HappyTalkyUiState
): Color =
    when (
        state.callState
    ) {
        CallVisualState.LIVE ->
            Color(0xFF28B779)

        CallVisualState.INCOMING ->
            Color(0xFF28B779)

        CallVisualState.OUTGOING,
        CallVisualState.CONNECTING,
        CallVisualState.RECONNECTING ->
            Color(0xFFFFB300)

        CallVisualState.READY ->
            MaterialTheme
                .colorScheme
                .primary
    }

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
)
@Composable
private fun PhoneMessengerPreview() {
    HappyTalkyPhoneTheme(
        darkTheme = false
    ) {
        HappyTalkyPhoneScreen(
            state =
                HappyTalkyUiState(
                    callEnabled = true,
                    peerConnection =
                        PeerConnectionState
                            .CONNECTED,
                    peerRoute =
                        PeerRoute
                            .NEARBY_DIRECT,
                ),
            onCall = {},
            onDecline = {},
            onSpeakerToggle = {},
            onPriorityCall = {},
            onTalkStart = {},
            onTalkFinish = {},
            onTalkCancel = {},
            onPlay = {},
            onDelete = {},
            onClear = {},
        )
    }
}

// Keep protocol/device metadata intact; presentation removes only the known role prefix.
private fun displayPeerName(name: String): String =
    name.removePrefix("Watch · ").removePrefix("Phone · ")

private fun isEmojiReply(text: String): Boolean {
    val points = text.trim().codePoints().toArray()
    return points.isNotEmpty() && points.size <= 16 &&
        points.any { it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF } &&
        points.all {
            it in 0x1F000..0x1FAFF || it in 0x2600..0x27BF ||
                it == 0xFE0F || it == 0x200D || Character.isWhitespace(it)
        }
}

private fun phoneCallLabel(entry: CallHistoryEntry): String {
    val prefix = if (entry.mode == CallMode.PRIORITY) "Priority call · " else ""
    return when (entry.outcome) {
        CallOutcome.CANCELLED_BY_ME -> prefix + "You cancelled"
        CallOutcome.CANCELLED_BY_PEER -> prefix + "Peer cancelled"
        CallOutcome.DECLINED_BY_ME -> prefix + "You declined"
        CallOutcome.DECLINED_BY_PEER -> prefix + "Peer declined"
        else -> entry.shortLabel()
    }
}

@Composable
internal fun PriorityCallOptions(
    state: HappyTalkyUiState,
    onDismiss: () -> Unit,
) {
    val supported =
        Protocol
            .CAPABILITY_PRIORITY_LOCKED_CALL_V1 in
            state.peerCapabilities
    val explanation =
        when {
            state.priorityLocked &&
                state.callState !=
                    CallVisualState.READY ->
                "Priority call is active. Only the phone can end it normally."

            state.peerConnection !=
                PeerConnectionState.CONNECTED ->
                "Connect the watch before starting a priority call."

            !supported ->
                "This watch does not support locked priority calls yet. Update both apps and reconnect."

            state.callState !=
                CallVisualState.READY ||
                state.recording ->
                "Finish the current call or TALK recording first."

            else ->
                "Starts immediately without a normal ringing phase. The watch cannot decline or end the call."
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Priority call")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Text(explanation)
                Text(
                    "If the watch app is already visible, it auto-connects immediately. Android does not allow background microphone capture until the watch app becomes foreground.",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Got it")
            }
        },
    )
}
