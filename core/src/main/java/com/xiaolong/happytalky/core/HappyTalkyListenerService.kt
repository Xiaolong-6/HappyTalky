package com.xiaolong.happytalky.core

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class HappyTalkyListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload =
            messageEvent.data.toString(Charsets.UTF_8)
        if (payload.isBlank()) return

        when (messageEvent.path) {
            Protocol.PROXIMITY_START ->
                receiveProximityStart(payload)

            Protocol.PROXIMITY_STOP ->
                receiveProximityStop(payload)

            Protocol.CALL_RING -> receiveRing(payload)
            Protocol.CALL_ANSWER -> receiveAnswer(payload)
            Protocol.CALL_DECLINE -> receiveDecline(payload)
            Protocol.CALL_CANCEL -> receiveCancel(payload)
            Protocol.CALL_BUSY -> receiveBusy(payload)
            Protocol.CALL_END ->
                receiveTerminal(
                    payload,
                    disconnected = false
                )
            Protocol.CALL_DISCONNECTED ->
                receiveTerminal(
                    payload,
                    disconnected = true
                )
            Protocol.CALL_PRIORITY ->
                receivePriority(payload)

            Protocol.CALL_PRIORITY_LOCKED ->
                receiveLockedPriority(
                    payload
                )
        }
    }

    override fun onPeerConnected(peer: Node) {
        DataLayerTransport(this)
            .also {
                it.publishDeviceInfo()
            }
            .refreshPeerConnection { state, _ ->
                if (
                    state == PeerConnectionState.CONNECTED &&
                    StateStore.activeCall(this) != null &&
                    !LiveCallAudio.isRunning()
                ) {
                    StateStore.setStatus(
                        this,
                        "Reconnecting live audio…"
                    )
                    StateStore.beginReconnectWindow(this)
                    LiveCallService.start(this)
                }

                EventBus.notifyStateChanged(this)
            }
    }

    override fun onPeerDisconnected(peer: Node) {
        // NodeClient reports every Android node on the Wear network, not
        // only the HappyTalky companion. Re-check the advertised
        // capability before changing product state.
        DataLayerTransport(this)
            .refreshPeerConnection { state, _ ->
                if (state == PeerConnectionState.CONNECTED) {
                    EventBus.notifyStateChanged(this)
                    return@refreshPeerConnection
                }

                if (StateStore.activeCall(this) != null) {
                    StateStore.setPeerConnection(
                        this,
                        PeerConnectionState.RECONNECTING
                    )
                    StateStore.setPeerRoute(
                        this,
                        PeerRoute.RECONNECTING
                    )
                    StateStore.setStatus(
                        this,
                        "Reconnecting…"
                    )
                    StateStore.beginReconnectWindow(this)
                    LiveCallAudio.stop(
                        this,
                        closeChannel = false
                    )
                    LiveCallService.start(this)
                } else {
                    StateStore.setPeerConnection(
                        this,
                        PeerConnectionState.DISCONNECTED
                    )
                    StateStore.setPeerRoute(
                        this,
                        PeerRoute.OFFLINE
                    )
                    StateStore.setStatus(
                        this,
                        "Peer offline · TALK recommended"
                    )
                }

                EventBus.notifyStateChanged(this)
            }
    }

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val path = channel.path
        if (!path.startsWith(Protocol.CALL_AUDIO_PREFIX)) return

        val callId = path.removePrefix(Protocol.CALL_AUDIO_PREFIX)
        if (callId.isBlank() || StateStore.activeCall(this) != callId) {
            Wearable.getChannelClient(this).close(channel)
            return
        }

        LiveCallAudio.attachIncoming(this, channel)
    }

    override fun onChannelClosed(
        channel: ChannelClient.Channel,
        closeReason: Int,
        appSpecificErrorCode: Int
    ) {
        val path = channel.path
        if (!path.startsWith(Protocol.CALL_AUDIO_PREFIX)) return
        val callId = path.removePrefix(Protocol.CALL_AUDIO_PREFIX)

        if (StateStore.activeCall(this) == callId) {
            LiveCallAudio.stop(this, closeChannel = false)
            StateStore.setPeerConnection(
                this,
                PeerConnectionState.RECONNECTING
            )
            StateStore.setPeerRoute(
                this,
                PeerRoute.RECONNECTING
            )
            StateStore.setStatus(this, "Reconnecting…")
            StateStore.beginReconnectWindow(this)
            EventBus.notifyStateChanged(this)
            LiveCallService.start(this)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val role =
            EndpointRole.fromContext(this)
        val localDeviceId =
            LocalDeviceIdentity.id(this)
        val dataClient =
            Wearable.getDataClient(this)

        dataEvents.forEach { event ->
            if (
                event.type !=
                    DataEvent.TYPE_CHANGED
            ) {
                return@forEach
            }

            val item =
                event.dataItem
            val path =
                item.uri.path
                    ?: return@forEach

            try {
                when {
                    path.startsWith(
                        Protocol.DEVICE_INFO_PREFIX
                    ) -> {
                        val map =
                            DataMapItem
                                .fromDataItem(item)
                                .dataMap
                        val deviceId =
                            map.getString(
                                Protocol.KEY_ID
                            )
                                ?: return@forEach

                        if (
                            deviceId ==
                                localDeviceId
                        ) {
                            return@forEach
                        }

                        val peerRole =
                            when (
                                map.getString(
                                    Protocol.KEY_ROLE
                                )
                            ) {
                                EndpointRole
                                    .WATCH
                                    .wireValue ->
                                    EndpointRole.WATCH

                                EndpointRole
                                    .PHONE
                                    .wireValue ->
                                    EndpointRole.PHONE

                                else ->
                                    return@forEach
                            }

                        if (peerRole == role) {
                            return@forEach
                        }

                        val info =
                            DeviceInfo(
                                deviceId =
                                    deviceId,
                                role =
                                    peerRole,
                                manufacturer =
                                    map.getString(
                                        Protocol
                                            .KEY_MANUFACTURER
                                    )
                                        .orEmpty(),
                                model =
                                    map.getString(
                                        Protocol
                                            .KEY_MODEL
                                    )
                                        .orEmpty(),
                                appVersion =
                                    map.getString(
                                        Protocol
                                            .KEY_APP_VERSION
                                    )
                                        .orEmpty(),
                                protocolVersion =
                                    map.getInt(
                                        Protocol
                                            .KEY_PROTOCOL_VERSION
                                    ),
                                capabilities =
                                    map.getStringArrayList(
                                        Protocol
                                            .KEY_CAPABILITIES
                                    )
                                        ?.toSet()
                                        .orEmpty(),
                                priorityAutoAnswerEnabled =
                                    map.getBoolean(
                                        Protocol
                                            .KEY_PRIORITY_AUTO_ANSWER
                                    ),
                                updatedAt =
                                    map.getLong(
                                        Protocol
                                            .KEY_DEVICE_INFO_UPDATED_AT
                                    )
                            )

                        PeerInfoStore.save(
                            this,
                            info
                        )
                        EventBus.notifyStateChanged(
                            this
                        )
                    }

                    path.startsWith(
                        Protocol.MESSAGE_PREFIX
                    ) -> {
                        val map =
                            DataMapItem
                                .fromDataItem(item)
                                .dataMap
                        val origin =
                            map.getString(
                                Protocol.KEY_ORIGIN
                            )
                        if (
                            origin ==
                                role.wireValue
                        ) {
                            return@forEach
                        }

                        val textId =
                            map.getString(
                                Protocol.KEY_ID
                            )
                                ?: path
                                    .substringAfterLast(
                                        '/'
                                    )
                        val createdAt =
                            map.getLong(
                                Protocol
                                    .KEY_CREATED_AT
                            )
                        val text =
                            map.getString(
                                Protocol.KEY_TEXT
                            )
                                ?: return@forEach

                        val inserted =
                            TextMessageStore
                                .saveIncoming(
                                    this,
                                    textId,
                                    createdAt,
                                    text
                                )
                                ?: return@forEach

                        Tasks.await(
                            dataClient
                                .deleteDataItems(
                                    item.uri
                                )
                        )

                        if (inserted) {
                            StateStore.setStatus(
                                this,
                                "New text message"
                            )
                            AlertController
                                .postTextNotification(
                                    this
                                )
                            EventBus.notifyStateChanged(
                                this
                            )
                        }
                    }

                    path.startsWith(
                        Protocol.VOICE_PREFIX
                    ) -> {
                        val map =
                            DataMapItem
                                .fromDataItem(item)
                                .dataMap
                        val origin =
                            map.getString(
                                Protocol.KEY_ORIGIN
                            )
                        if (
                            origin ==
                                role.wireValue
                        ) {
                            return@forEach
                        }

                        val asset =
                            map.getAsset(
                                Protocol.KEY_AUDIO
                            )
                                ?: return@forEach
                        val voiceId =
                            map.getString(
                                Protocol.KEY_ID
                            )
                                ?: path
                                    .substringAfterLast(
                                        '/'
                                    )
                        val createdAt =
                            map.getLong(
                                Protocol
                                    .KEY_CREATED_AT
                            )

                        val response =
                            Tasks.await(
                                dataClient
                                    .getFdForAsset(
                                        asset
                                    )
                            )
                        try {
                            val stream =
                                response.inputStream
                                    ?: return@forEach
                            stream.use {
                                VoiceMessageStore
                                    .saveIncoming(
                                        this,
                                        voiceId,
                                        createdAt,
                                        it
                                    )
                            }
                        } finally {
                            response.release()
                        }

                        Tasks.await(
                            dataClient
                                .deleteDataItems(
                                    item.uri
                                )
                        )
                        StateStore.setStatus(
                            this,
                            "New voice message"
                        )
                        AlertController
                            .postVoiceNotification(
                                this
                            )

                        // TALK messages are intentionally never
                        // auto-played.
                        EventBus.notifyStateChanged(
                            this
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Failed to receive Data Layer item",
                    e
                )
            }
        }
    }

    private fun receiveProximityStart(
        token: String
    ) {
        if (!ProximitySessionToken.isValid(token)) {
            return
        }

        if (
            StateStore.incomingCall(this) != null ||
            StateStore.outgoingCall(this) != null ||
            StateStore.activeCall(this) != null
        ) {
            DataLayerTransport(this)
                .sendSignal(
                    Protocol.PROXIMITY_ERROR,
                    token +
                        "|Device is busy with a call"
                ) { }
            return
        }

        BleProximityAdvertiser.start(
            this,
            token
        ) { result ->
            val path =
                if (result.started) {
                    Protocol.PROXIMITY_READY
                } else {
                    Protocol.PROXIMITY_ERROR
                }
            val payload =
                if (result.started) {
                    token
                } else {
                    token + "|" +
                        result.error
                            .orEmpty()
                            .take(96)
                }

            DataLayerTransport(this)
                .sendSignal(
                    path,
                    payload
                ) { }
        }
    }

    private fun receiveProximityStop(
        token: String
    ) {
        BleProximityAdvertiser.stop(token)
    }

    private fun receiveRing(callId: String) {
        if (StateStore.incomingCall(this) == callId) return

        val busy =
            StateStore.activeCall(this) != null ||
            StateStore.outgoingCall(this) != null ||
            StateStore.incomingCall(this) != null

        if (busy) {
            CallHistoryStore.append(
                this,
                callId,
                CallDirection.INCOMING,
                CallOutcome.BUSY
            )
            DataLayerTransport(this)
                .sendSignal(
                    Protocol.CALL_BUSY,
                    callId
                ) { }
            return
        }

        StateStore.setCallInitiator(this, false)
        StateStore.setCallMode(
            this,
            CallMode.NORMAL
        )
        StateStore.setPriorityLocked(
            this,
            false
        )
        StateStore.setIncomingCall(this, callId)
        StateStore.setPeerConnection(
            this,
            PeerConnectionState.CONNECTED
        )
        DataLayerTransport(this).refreshPeerConnection()
        StateStore.setStatus(this, "Incoming call")
        AlertController.startIncomingCall(this, callId)
        EventBus.notifyStateChanged(this)
    }

    private fun receivePriority(
        callId: String
    ) {
        if (
            EndpointRole.fromContext(this) !=
                EndpointRole.WATCH
        ) {
            return
        }

        if (
            !PriorityCallSettings.isEnabled(
                this
            )
        ) {
            return
        }

        val incoming =
            StateStore.incomingCall(
                this
            )
        val active =
            StateStore.activeCall(
                this
            )
        val outgoing =
            StateStore.outgoingCall(
                this
            )

        when (
            PriorityCallPolicy
                .requestDisposition(
                    requestedCallId = callId,
                    incomingCallId = incoming,
                    outgoingCallId = outgoing,
                    activeCallId = active
                )
        ) {
            PriorityRequestDisposition
                .IGNORE_ALREADY_ACTIVE ->
                return

            PriorityRequestDisposition
                .REJECT_BUSY -> {
                DataLayerTransport(this)
                    .sendSignal(
                        Protocol.CALL_BUSY,
                        callId
                    ) { }
                return
            }

            PriorityRequestDisposition.APPLY ->
                Unit
        }

        StateStore.setCallInitiator(
            this,
            false
        )
        StateStore.setCallMode(
            this,
            CallMode.PRIORITY
        )
        StateStore.setPriorityLocked(
            this,
            false
        )
        StateStore.setIncomingCall(
            this,
            callId
        )
        StateStore.setStatus(
            this,
            "Priority call"
        )
        AlertController.startIncomingCall(
            this,
            callId,
            priority = true
        )
        EventBus.notifyStateChanged(
            this
        )
    }

    private fun receiveLockedPriority(
        callId: String
    ) {
        if (
            EndpointRole.fromContext(this) !=
                EndpointRole.WATCH
        ) {
            return
        }

        val incoming =
            StateStore.incomingCall(
                this
            )
        val active =
            StateStore.activeCall(
                this
            )
        val outgoing =
            StateStore.outgoingCall(
                this
            )

        when (
            PriorityCallPolicy
                .requestDisposition(
                    requestedCallId =
                        callId,
                    incomingCallId =
                        incoming,
                    outgoingCallId =
                        outgoing,
                    activeCallId =
                        active
                )
        ) {
            PriorityRequestDisposition
                .IGNORE_ALREADY_ACTIVE ->
                return

            PriorityRequestDisposition
                .REJECT_BUSY -> {
                DataLayerTransport(this)
                    .sendSignal(
                        Protocol.CALL_BUSY,
                        callId
                    ) { }
                return
            }

            PriorityRequestDisposition.APPLY ->
                Unit
        }

        AudioPlayer.stop()

        StateStore.setCallInitiator(
            this,
            false
        )
        StateStore.setCallMode(
            this,
            CallMode.PRIORITY
        )
        StateStore.setPriorityLocked(
            this,
            true
        )
        StateStore.setIncomingCall(
            this,
            callId
        )
        StateStore.setPeerConnection(
            this,
            PeerConnectionState.CONNECTED
        )
        val telecomAutoAnswer =
            PriorityCallPolicy
                .canUseTelecomAutoAnswer(
                    localRole =
                        EndpointRole.fromContext(
                            this
                        ),
                    locked = true,
                    mode = CallMode.PRIORITY,
                    incomingCallPresent = true,
                    telecomAvailable =
                        PriorityTelecomController
                            .isSupported(this)
                )

        StateStore.setStatus(
            this,
            if (telecomAutoAnswer) {
                "Priority call · auto-connecting"
            } else {
                "Priority call · open watch to connect"
            }
        )
        AlertController.startIncomingCall(
            this,
            callId,
            priority = true,
            locked = true
        )

        if (telecomAutoAnswer) {
            PriorityTelecomController
                .startLockedIncoming(
                    this,
                    callId
                )
        }

        EventBus.notifyStateChanged(
            this
        )
    }

    private fun receiveAnswer(callId: String) {
        if (StateStore.outgoingCall(this) != callId) return

        StateStore.setIncomingCall(this, null)
        StateStore.setOutgoingCall(this, null)
        StateStore.setActiveCall(this, callId)
        StateStore.setActiveStartedAt(
            this,
            System.currentTimeMillis()
        )
        StateStore.clearReconnectWindow(this)
        StateStore.setPeerConnection(
            this,
            PeerConnectionState.CONNECTED
        )
        DataLayerTransport(this).refreshPeerConnection()
        StateStore.setStatus(this, "Connecting live audio…")
        AlertController.stop(this)
        LiveCallService.start(this)
        EventBus.notifyStateChanged(this)
    }

    private fun receiveDecline(callId: String) {
        if (StateStore.outgoingCall(this) != callId) return

        if (
            EndpointRole.fromContext(this) ==
                EndpointRole.PHONE &&
            StateStore.priorityLocked(this)
        ) {
            return
        }

        CallHistoryStore.append(
            this,
            callId,
            CallDirection.OUTGOING,
            CallOutcome.DECLINED_BY_PEER
        )
        StateStore.clearCallState(this)
        StateStore.setStatus(this, "Call declined")
        AlertController.stop(this)
        LiveCallAudio.stop(this)
        LiveCallService.stop(this)
        EventBus.notifyStateChanged(this)
    }

    private fun receiveCancel(callId: String) {
        val incomingMatches =
            StateStore.incomingCall(this) ==
                callId
        val lockedActiveMatches =
            EndpointRole.fromContext(this) ==
                EndpointRole.WATCH &&
                StateStore.priorityLocked(
                    this
                ) &&
                StateStore.activeCall(this) ==
                    callId

        if (
            !PriorityCallPolicy
                .acceptsPhoneCancelOnWatch(
                    localRole =
                        EndpointRole.fromContext(
                            this
                        ),
                    locked =
                        StateStore.priorityLocked(
                            this
                        ),
                    incomingMatches =
                        incomingMatches,
                    activeMatches =
                        StateStore.activeCall(
                            this
                        ) == callId
                )
        ) {
            return
        }

        CallHistoryStore.append(
            this,
            callId,
            CallDirection.INCOMING,
            CallOutcome.CANCELLED_BY_PEER,
            startedAt =
                if (lockedActiveMatches) {
                    StateStore.activeStartedAt(
                        this
                    )
                } else {
                    0L
                }
        )
        PriorityTelecomController
            .disconnectFromPhone(
                this,
                callId
            )
        StateStore.clearCallState(this)
        StateStore.setStatus(
            this,
            "Priority call cancelled by phone"
        )
        AlertController.stop(this)
        LiveCallAudio.stop(this)
        CallHaptics.ended(
            this,
            callId
        )
        LiveCallService.stop(this)
        EventBus.notifyStateChanged(this)
    }

    private fun receiveBusy(callId: String) {
        if (StateStore.outgoingCall(this) != callId) return

        CallHistoryStore.append(
            this,
            callId,
            CallDirection.OUTGOING,
            CallOutcome.BUSY
        )
        StateStore.clearCallState(this)
        StateStore.setStatus(this, "Peer is busy")
        AlertController.stop(this)
        LiveCallAudio.stop(this)
        LiveCallService.stop(this)
        EventBus.notifyStateChanged(this)
    }

    private fun receiveTerminal(
        callId: String,
        disconnected: Boolean
    ) {
        val relevant =
            StateStore.activeCall(this) == callId ||
                StateStore.incomingCall(this) ==
                    callId ||
                StateStore.outgoingCall(this) ==
                    callId

        if (!relevant) return

        if (
            EndpointRole.fromContext(this) ==
                EndpointRole.PHONE &&
            StateStore.priorityLocked(this) &&
            !disconnected
        ) {
            return
        }

        val wasActive =
            StateStore.activeCall(this) ==
                callId
        val direction =
            if (
                StateStore.callInitiator(
                    this
                )
            ) {
                CallDirection.OUTGOING
            } else {
                CallDirection.INCOMING
            }
        val outcome =
            CallSignalOutcomePolicy
                .remoteTerminalOutcome(
                    wasActive = wasActive,
                    disconnected =
                        disconnected
                )

        CallHistoryStore.append(
            this,
            callId,
            direction,
            outcome,
            startedAt =
                if (wasActive) {
                    StateStore.activeStartedAt(
                        this
                    )
                } else {
                    0L
                }
        )

        PriorityTelecomController
            .disconnectFromPhone(
                this,
                callId,
                disconnected = disconnected
            )
        StateStore.clearCallState(this)
        StateStore.setStatus(
            this,
            if (disconnected) {
                "Call disconnected · TALK recommended"
            } else {
                "Call ended"
            }
        )
        AlertController.stop(this)
        LiveCallAudio.stop(this)
        CallHaptics.ended(
            this,
            callId
        )
        LiveCallService.stop(this)
        EventBus.notifyStateChanged(this)
    }

    companion object {
        private const val TAG = "HappyTalkyListener"
    }
}
