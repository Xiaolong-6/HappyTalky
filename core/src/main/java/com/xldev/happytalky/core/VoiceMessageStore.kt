package com.xldev.happytalky.core

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class VoiceDirection {
    INCOMING,
    OUTGOING
}

data class VoiceMessage(
    val id: String,
    val createdAt: Long,
    val direction: VoiceDirection,
    val file: File,
    val durationMs: Long = 0L,
    val readAt: Long? = null,
    val deliveryState: DeliveryState = DeliveryState.LOCAL
) {
    val isRead: Boolean
        get() =
            direction == VoiceDirection.OUTGOING ||
                readAt != null

    fun displayTime(): String =
        SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(Date(createdAt))

    fun displayDuration(): String {
        val totalSeconds =
            (durationMs.coerceAtLeast(0L) + 500L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(
            Locale.getDefault(),
            minutes,
            seconds
        )
    }
}

object VoiceMessageStore {
    private const val DIRECTORY = "voice-history"
    private const val READ_PREFS =
        "happytalky_voice_read_state"
    private const val MIGRATION_PREFS =
        "happytalky_conversation_migration"
    private const val KEY_VOICE_MIGRATED =
        "voice_metadata_v1"

    @Synchronized
    fun saveOutgoing(
        context: Context,
        source: File
    ): VoiceMessage {
        ensureLegacyMigrated(context)

        val createdAt =
            System.currentTimeMillis()
        val id =
            UUID.randomUUID().toString()
        val target =
            messageFile(
                context,
                createdAt,
                VoiceDirection.OUTGOING,
                id
            )

        source.copyTo(
            target,
            overwrite = true
        )
        source.delete()

        val message =
            VoiceMessage(
                id = id,
                createdAt = createdAt,
                direction = VoiceDirection.OUTGOING,
                file = target,
                durationMs = durationMs(target),
                readAt = createdAt,
                deliveryState = DeliveryState.LOCAL
            )

        ConversationStore.upsert(
            context,
            message.toConversationItem()
        )

        return message
    }

    @Synchronized
    fun saveIncoming(
        context: Context,
        id: String,
        createdAt: Long,
        input: InputStream
    ): VoiceMessage {
        ensureLegacyMigrated(context)

        val safeCreatedAt =
            if (createdAt > 0L) {
                createdAt
            } else {
                System.currentTimeMillis()
            }

        val target =
            messageFile(
                context,
                safeCreatedAt,
                VoiceDirection.INCOMING,
                id
            )

        val existing =
            ConversationStore.voiceById(
                context,
                id
            )

        if (
            !target.exists() ||
            target.length() == 0L
        ) {
            target.outputStream().use { output ->
                input.copyTo(output)
            }
            clearLegacyReadState(
                context,
                id
            )
        }

        val readAt =
            existing?.readAt ?:
                legacyReadAt(
                    context,
                    id
                )

        val message =
            VoiceMessage(
                id = id,
                createdAt = safeCreatedAt,
                direction = VoiceDirection.INCOMING,
                file = target,
                durationMs = durationMs(target),
                readAt = readAt,
                deliveryState =
                    if (readAt != null) {
                        DeliveryState.READ
                    } else {
                        DeliveryState.DELIVERED
                    }
            )

        ConversationStore.upsert(
            context,
            message.toConversationItem()
        )

        return message
    }

    @Synchronized
    fun list(
        context: Context,
        limit: Int = 50
    ): List<VoiceMessage> {
        ensureLegacyMigrated(context)

        return ConversationStore
            .voices(
                context,
                limit
            )
            .mapNotNull {
                it.toVoiceMessageOrNull(
                    context
                )
            }
    }

    @Synchronized
    fun unreadCount(
        context: Context
    ): Int {
        ensureLegacyMigrated(context)
        return ConversationStore
            .unreadVoiceCount(context)
    }

    @Synchronized
    fun markRead(
        context: Context,
        id: String,
        at: Long = System.currentTimeMillis()
    ) {
        ensureLegacyMigrated(context)

        ConversationStore.markVoiceRead(
            context,
            id,
            at
        )

        // Keep the legacy marker while this schema migration is young so
        // downgrading a debug build does not resurrect already-read TALK.
        readPrefs(context)
            .edit()
            .putLong(id, at)
            .apply()
    }

    @Synchronized
    fun delete(
        context: Context,
        ids: Set<String>
    ): Int {
        if (ids.isEmpty()) return 0
        ensureLegacyMigrated(context)

        val existing =
            ConversationStore
                .voices(
                    context,
                    Int.MAX_VALUE
                )
                .filter {
                    it.id in ids
                }

        existing.forEach {
            it.audioFileName
                ?.let { name ->
                    File(
                        directory(context),
                        name
                    ).delete()
                }
        }

        val deleted =
            ConversationStore.deleteVoices(
                context,
                ids
            )

        ids.forEach {
            clearLegacyReadState(
                context,
                it
            )
        }

        return deleted
    }

    @Synchronized
    fun clear(
        context: Context
    ): Int {
        ensureLegacyMigrated(context)

        var deleted = 0

        directory(context)
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.extension.lowercase() ==
                        "m4a"
            }
            .forEach { file ->
                if (file.delete()) {
                    deleted += 1
                }
            }

        ConversationStore.clearVoices(
            context
        )

        readPrefs(context)
            .edit()
            .clear()
            .apply()

        return deleted
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
                KEY_VOICE_MIGRATED,
                false
            )
        ) {
            return
        }

        directory(context)
            .listFiles()
            .orEmpty()
            .mapNotNull {
                parseLegacy(
                    context,
                    it
                )
            }
            .forEach { message ->
                ConversationStore.insertIfAbsent(
                    context,
                    message.toConversationItem()
                )
            }

        prefs.edit()
            .putBoolean(
                KEY_VOICE_MIGRATED,
                true
            )
            .apply()
    }

    private fun VoiceMessage.toConversationItem():
        ConversationItem =
        ConversationItem(
            id = id,
            type = ConversationItemType.VOICE,
            direction =
                direction.toConversationDirection(),
            createdAt = createdAt,
            readAt = readAt,
            deliveryState = deliveryState,
            audioFileName = file.name,
            durationMs = durationMs
        )

    private fun ConversationItem.toVoiceMessageOrNull(
        context: Context
    ): VoiceMessage? {
        if (
            type !=
                ConversationItemType.VOICE
        ) {
            return null
        }

        val fileName =
            audioFileName
                ?: return null
        val file =
            File(
                directory(context),
                fileName
            )

        if (!file.exists()) {
            return null
        }

        return VoiceMessage(
            id = id,
            createdAt = createdAt,
            direction =
                direction.toVoiceDirection(),
            file = file,
            durationMs = durationMs,
            readAt = readAt,
            deliveryState = deliveryState
        )
    }

    private fun VoiceDirection.toConversationDirection():
        ConversationDirection =
        if (this == VoiceDirection.INCOMING) {
            ConversationDirection.INCOMING
        } else {
            ConversationDirection.OUTGOING
        }

    private fun ConversationDirection.toVoiceDirection():
        VoiceDirection =
        if (
            this ==
                ConversationDirection.INCOMING
        ) {
            VoiceDirection.INCOMING
        } else {
            VoiceDirection.OUTGOING
        }

    private fun messageFile(
        context: Context,
        createdAt: Long,
        direction: VoiceDirection,
        id: String
    ): File {
        val token =
            if (
                direction ==
                    VoiceDirection.INCOMING
            ) {
                "in"
            } else {
                "out"
            }

        return File(
            directory(context),
            "$createdAt-$token-$id.m4a"
        )
    }

    private fun directory(
        context: Context
    ): File =
        File(
            context.filesDir,
            DIRECTORY
        ).apply {
            mkdirs()
        }

    private fun readPrefs(
        context: Context
    ) =
        context.getSharedPreferences(
            READ_PREFS,
            Context.MODE_PRIVATE
        )

    private fun legacyReadAt(
        context: Context,
        id: String
    ): Long? {
        val value =
            readPrefs(context)
                .getLong(
                    id,
                    0L
                )

        return value
            .takeIf {
                it > 0L
            }
    }

    private fun clearLegacyReadState(
        context: Context,
        id: String
    ) {
        readPrefs(context)
            .edit()
            .remove(id)
            .apply()
    }

    private data class ParsedName(
        val createdAt: Long,
        val direction: VoiceDirection,
        val id: String
    )

    private fun parseName(
        file: File
    ): ParsedName? {
        if (
            !file.isFile ||
            file.extension.lowercase() !=
                "m4a"
        ) {
            return null
        }

        val stem =
            file.name.removeSuffix(".m4a")
        val first =
            stem.indexOf('-')

        if (first <= 0) return null

        val second =
            stem.indexOf(
                '-',
                first + 1
            )

        if (
            second <= first + 1
        ) {
            return null
        }

        val createdAt =
            stem.substring(
                0,
                first
            ).toLongOrNull()
                ?: return null

        val direction =
            when (
                stem.substring(
                    first + 1,
                    second
                )
            ) {
                "in" ->
                    VoiceDirection.INCOMING

                "out" ->
                    VoiceDirection.OUTGOING

                else ->
                    return null
            }

        val id =
            stem.substring(
                second + 1
            )

        if (id.isBlank()) {
            return null
        }

        return ParsedName(
            createdAt = createdAt,
            direction = direction,
            id = id
        )
    }

    private fun parseLegacy(
        context: Context,
        file: File
    ): VoiceMessage? {
        val parsed =
            parseName(file)
                ?: return null
        val readAt =
            if (
                parsed.direction ==
                    VoiceDirection.OUTGOING
            ) {
                parsed.createdAt
            } else {
                legacyReadAt(
                    context,
                    parsed.id
                )
            }

        return VoiceMessage(
            id = parsed.id,
            createdAt = parsed.createdAt,
            direction = parsed.direction,
            file = file,
            durationMs = durationMs(file),
            readAt = readAt,
            deliveryState =
                when {
                    parsed.direction ==
                        VoiceDirection.OUTGOING ->
                        DeliveryState.SENT

                    readAt != null ->
                        DeliveryState.READ

                    else ->
                        DeliveryState.DELIVERED
                }
        )
    }

    private fun durationMs(
        file: File
    ): Long {
        val retriever =
            MediaMetadataRetriever()

        return try {
            retriever.setDataSource(
                file.absolutePath
            )
            retriever
                .extractMetadata(
                    MediaMetadataRetriever
                        .METADATA_KEY_DURATION
                )
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching {
                retriever.release()
            }
        }
    }
}
