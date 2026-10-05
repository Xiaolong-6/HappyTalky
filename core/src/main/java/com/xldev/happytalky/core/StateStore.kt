package com.xldev.happytalky.core

import android.content.Context

enum class PeerConnectionState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING
}

enum class PeerRoute {
    UNKNOWN,
    NEARBY_DIRECT,
    REMOTE_WIFI,
    REMOTE_CELLULAR,
    REMOTE_INTERNET,
    OFFLINE,
    RECONNECTING
}

object StateStore {
    private const val PREFS = "happytalky_state"
    private const val KEY_STATUS = "status"
    private const val KEY_INCOMING = "incoming_call"
    private const val KEY_OUTGOING = "outgoing_call"
    private const val KEY_ACTIVE = "active_call"
    private const val KEY_CALL_INITIATOR = "call_initiator"
    private const val KEY_ACTIVE_STARTED_AT = "active_started_at"
    private const val KEY_OUTGOING_STARTED_AT = "outgoing_started_at"
    private const val KEY_CALL_MODE = "call_mode"
    private const val KEY_PRIORITY_LOCKED = "priority_locked"
    private const val KEY_PEER_CONNECTION = "peer_connection"
    private const val KEY_PEER_ROUTE = "peer_route"
    private const val KEY_RECONNECT_UNTIL = "reconnect_until"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun status(context: Context): String =
        prefs(context).getString(KEY_STATUS, null) ?: "Ready"

    fun setStatus(context: Context, value: String) {
        prefs(context).edit().putString(KEY_STATUS, value).apply()
    }

    fun incomingCall(context: Context): String? =
        prefs(context).getString(KEY_INCOMING, null)

    fun setIncomingCall(context: Context, callId: String?) {
        writeNullable(context, KEY_INCOMING, callId)
    }

    fun outgoingCall(context: Context): String? =
        prefs(context).getString(KEY_OUTGOING, null)

    fun setOutgoingCall(context: Context, callId: String?) {
        writeNullable(context, KEY_OUTGOING, callId)
    }

    fun activeCall(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE, null)

    fun setActiveCall(context: Context, callId: String?) {
        writeNullable(context, KEY_ACTIVE, callId)
    }

    fun callInitiator(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CALL_INITIATOR, false)

    fun setCallInitiator(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_CALL_INITIATOR, value).apply()
    }

    fun activeStartedAt(context: Context): Long =
        prefs(context).getLong(
            KEY_ACTIVE_STARTED_AT,
            0L
        )

    fun setActiveStartedAt(
        context: Context,
        value: Long
    ) {
        prefs(context)
            .edit()
            .putLong(
                KEY_ACTIVE_STARTED_AT,
                value
            )
            .apply()
    }

    fun outgoingStartedAt(context: Context): Long =
        prefs(context).getLong(
            KEY_OUTGOING_STARTED_AT,
            0L
        )

    fun setOutgoingStartedAt(
        context: Context,
        value: Long
    ) {
        prefs(context)
            .edit()
            .putLong(
                KEY_OUTGOING_STARTED_AT,
                value
            )
            .apply()
    }

    fun callMode(context: Context): CallMode {
        val raw =
            prefs(context).getString(
                KEY_CALL_MODE,
                null
            )

        return runCatching {
            raw?.let {
                CallMode.valueOf(it)
            }
        }.getOrNull()
            ?: CallMode.NORMAL
    }

    fun setCallMode(
        context: Context,
        mode: CallMode
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_CALL_MODE,
                mode.name
            )
            .apply()
    }

    fun priorityLocked(context: Context): Boolean =
        prefs(context).getBoolean(
            KEY_PRIORITY_LOCKED,
            false
        )

    fun setPriorityLocked(
        context: Context,
        locked: Boolean
    ) {
        prefs(context)
            .edit()
            .putBoolean(
                KEY_PRIORITY_LOCKED,
                locked
            )
            .apply()
    }

    fun peerConnection(context: Context): PeerConnectionState {
        val raw = prefs(context).getString(KEY_PEER_CONNECTION, null)
        return runCatching {
            if (raw == null) PeerConnectionState.UNKNOWN
            else PeerConnectionState.valueOf(raw)
        }.getOrDefault(PeerConnectionState.UNKNOWN)
    }

    fun setPeerConnection(context: Context, state: PeerConnectionState) {
        prefs(context).edit()
            .putString(KEY_PEER_CONNECTION, state.name)
            .apply()
    }

    fun peerRoute(context: Context): PeerRoute {
        val raw = prefs(context).getString(KEY_PEER_ROUTE, null)
        return runCatching {
            if (raw == null) PeerRoute.UNKNOWN
            else PeerRoute.valueOf(raw)
        }.getOrDefault(PeerRoute.UNKNOWN)
    }

    fun setPeerRoute(context: Context, route: PeerRoute) {
        prefs(context).edit()
            .putString(KEY_PEER_ROUTE, route.name)
            .apply()
    }

    fun reconnectUntil(context: Context): Long =
        prefs(context).getLong(KEY_RECONNECT_UNTIL, 0L)

    fun beginReconnectWindow(context: Context) {
        val existing = reconnectUntil(context)
        val now = System.currentTimeMillis()
        if (existing > now) return

        prefs(context).edit()
            .putLong(KEY_RECONNECT_UNTIL, now + Protocol.RECONNECT_GRACE_MS)
            .apply()
    }

    fun clearReconnectWindow(context: Context) {
        prefs(context).edit().remove(KEY_RECONNECT_UNTIL).apply()
    }

    fun clearCallState(context: Context) {
        prefs(context).edit()
            .remove(KEY_INCOMING)
            .remove(KEY_OUTGOING)
            .remove(KEY_ACTIVE)
            .remove(KEY_CALL_INITIATOR)
            .remove(KEY_ACTIVE_STARTED_AT)
            .remove(KEY_OUTGOING_STARTED_AT)
            .remove(KEY_CALL_MODE)
            .remove(KEY_PRIORITY_LOCKED)
            .remove(KEY_RECONNECT_UNTIL)
            .apply()
    }

    private fun writeNullable(context: Context, key: String, value: String?) {
        val editor = prefs(context).edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }
}
