package com.xldev.happytalky.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class CallDirection {
    INCOMING,
    OUTGOING
}

enum class CallOutcome {
    COMPLETED,
    DECLINED_BY_ME,
    DECLINED_BY_PEER,
    NO_ANSWER,
    MISSED,
    CANCELLED_BY_ME,
    CANCELLED_BY_PEER,
    BUSY,
    FAILED,
    DISCONNECTED
}

data class CallHistoryEntry(
    val id: String,
    val callId: String,
    val occurredAt: Long,
    val direction: CallDirection,
    val outcome: CallOutcome,
    val durationMs: Long = 0L,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val mode: CallMode = CallMode.NORMAL
) {
    fun displayTime(): String =
        SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date(occurredAt))

    fun displayDuration(): String {
        val totalSeconds =
            (durationMs.coerceAtLeast(0L) + 500L) /
                1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(
            Locale.getDefault(),
            minutes,
            seconds
        )
    }

    fun shortLabel(): String =
        when (outcome) {
            CallOutcome.COMPLETED ->
                if (durationMs > 0L) {
                    if (mode == CallMode.PRIORITY) {
                        "Priority call · ${displayDuration()}"
                    } else {
                        "Call · ${displayDuration()}"
                    }
                } else if (
                    mode == CallMode.PRIORITY
                ) {
                    "Priority call ended"
                } else {
                    "Call ended"
                }

            CallOutcome.DECLINED_BY_ME ->
                if (mode == CallMode.PRIORITY) {
                    "Declined priority call"
                } else {
                    "Declined call"
                }

            CallOutcome.DECLINED_BY_PEER ->
                if (mode == CallMode.PRIORITY) {
                    "Priority call declined"
                } else {
                    "Call declined"
                }

            CallOutcome.NO_ANSWER ->
                if (mode == CallMode.PRIORITY) {
                    "Priority call · no answer"
                } else {
                    "No answer"
                }

            CallOutcome.MISSED ->
                if (mode == CallMode.PRIORITY) {
                    "Missed priority call"
                } else {
                    "Missed call"
                }

            CallOutcome.CANCELLED_BY_ME ->
                if (mode == CallMode.PRIORITY) {
                    "Cancelled priority call"
                } else {
                    "Cancelled call"
                }

            CallOutcome.CANCELLED_BY_PEER ->
                if (mode == CallMode.PRIORITY) {
                    "Priority caller cancelled"
                } else {
                    "Caller cancelled"
                }

            CallOutcome.BUSY ->
                "Busy"

            CallOutcome.FAILED ->
                if (mode == CallMode.PRIORITY) {
                    "Priority call failed"
                } else {
                    "Call failed"
                }

            CallOutcome.DISCONNECTED ->
                if (mode == CallMode.PRIORITY) {
                    "Priority call disconnected"
                } else {
                    "Call disconnected"
                }
        }
}

object CallHistoryStore {
    private const val FILE_NAME =
        "call-history.jsonl"
    private const val MIGRATION_PREFS =
        "happytalky_conversation_migration"
    private const val KEY_CALLS_MIGRATED =
        "call_metadata_v1"

    @Synchronized
    fun append(
        context: Context,
        callId: String,
        direction: CallDirection,
        outcome: CallOutcome,
        startedAt: Long = 0L,
        endedAt: Long =
            System.currentTimeMillis(),
        mode: CallMode =
            StateStore.callMode(context)
    ): CallHistoryEntry {
        ensureLegacyMigrated(context)

        ConversationStore.callByCallId(
            context,
            callId
        )?.toCallHistoryEntryOrNull()
            ?.let {
                return it
            }

        val duration =
            if (
                startedAt > 0L &&
                endedAt >= startedAt
            ) {
                endedAt - startedAt
            } else {
                0L
            }

        val entry =
            CallHistoryEntry(
                id =
                    UUID.randomUUID()
                        .toString(),
                callId = callId,
                occurredAt =
                    if (startedAt > 0L) {
                        startedAt
                    } else {
                        endedAt
                    },
                direction = direction,
                outcome = outcome,
                durationMs = duration,
                startedAt =
                    startedAt
                        .takeIf {
                            it > 0L
                        },
                endedAt = endedAt,
                mode = mode
            )

        ConversationStore.insertIfAbsent(
            context,
            entry.toConversationItem()
        )

        return entry
    }

    @Synchronized
    fun list(
        context: Context,
        limit: Int = 50
    ): List<CallHistoryEntry> {
        ensureLegacyMigrated(context)

        return ConversationStore
            .calls(
                context,
                limit
            )
            .mapNotNull {
                it.toCallHistoryEntryOrNull()
            }
    }

    @Synchronized
    fun clear(context: Context) {
        ensureLegacyMigrated(context)
        ConversationStore.clearCalls(
            context
        )
    }

    private fun ensureLegacyMigrated(
        context: Context
    ) {
        val prefs =
            context.getSharedPreferences(
                MIGRATION_PREFS,
                Context.MODE_PRIVATE
            )

        if (
            prefs.getBoolean(
                KEY_CALLS_MIGRATED,
                false
            )
        ) {
            return
        }

        val file =
            historyFile(context)

        if (file.exists()) {
            file.readLines()
                .mapNotNull(::decodeLegacy)
                .forEach { entry ->
                    ConversationStore
                        .insertIfAbsent(
                            context,
                            entry
                                .toConversationItem()
                        )
                }
        }

        prefs.edit()
            .putBoolean(
                KEY_CALLS_MIGRATED,
                true
            )
            .apply()
    }

    private fun CallHistoryEntry.toConversationItem():
        ConversationItem =
        ConversationItem(
            id = id,
            type = ConversationItemType.CALL,
            direction =
                if (
                    direction ==
                        CallDirection.INCOMING
                ) {
                    ConversationDirection.INCOMING
                } else {
                    ConversationDirection.OUTGOING
                },
            createdAt = occurredAt,
            readAt = occurredAt,
            deliveryState = DeliveryState.READ,
            durationMs = durationMs,
            callId = callId,
            callOutcome = outcome.name,
            callMode = mode,
            startedAt = startedAt,
            endedAt = endedAt
        )

    private fun ConversationItem.toCallHistoryEntryOrNull():
        CallHistoryEntry? {
        if (
            type !=
                ConversationItemType.CALL
        ) {
            return null
        }

        val id =
            callId
                ?: return null
        val parsedOutcome =
            runCatching {
                CallOutcome.valueOf(
                    callOutcome ?: return null
                )
            }.getOrNull()
                ?: return null

        return CallHistoryEntry(
            id = this.id,
            callId = id,
            occurredAt = createdAt,
            direction =
                if (
                    direction ==
                        ConversationDirection
                            .INCOMING
                ) {
                    CallDirection.INCOMING
                } else {
                    CallDirection.OUTGOING
                },
            outcome = parsedOutcome,
            durationMs = durationMs,
            startedAt = startedAt,
            endedAt = endedAt,
            mode =
                callMode
                    ?: CallMode.NORMAL
        )
    }

    private fun historyFile(
        context: Context
    ): File =
        File(
            context.filesDir,
            FILE_NAME
        )

    private fun decodeLegacy(
        raw: String
    ): CallHistoryEntry? =
        runCatching {
            val obj =
                JSONObject(raw)
            val occurredAt =
                obj.getLong(
                    "occurredAt"
                )
            val durationMs =
                obj.optLong(
                    "durationMs",
                    0L
                )
            val startedAt =
                if (durationMs > 0L) {
                    occurredAt
                } else {
                    null
                }
            val endedAt =
                if (durationMs > 0L) {
                    occurredAt +
                        durationMs
                } else {
                    occurredAt
                }

            CallHistoryEntry(
                id =
                    obj.getString("id"),
                callId =
                    obj.getString(
                        "callId"
                    ),
                occurredAt = occurredAt,
                direction =
                    CallDirection.valueOf(
                        obj.getString(
                            "direction"
                        )
                    ),
                outcome =
                    CallOutcome.valueOf(
                        obj.getString(
                            "outcome"
                        )
                    ),
                durationMs = durationMs,
                startedAt = startedAt,
                endedAt = endedAt,
                mode = CallMode.NORMAL
            )
        }.getOrNull()
}
