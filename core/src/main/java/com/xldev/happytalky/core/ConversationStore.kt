package com.xldev.happytalky.core

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

enum class ConversationItemType {
    TEXT,
    VOICE,
    CALL
}

enum class ConversationDirection {
    INCOMING,
    OUTGOING
}

enum class DeliveryState {
    LOCAL,
    QUEUED,
    SENT,
    DELIVERED,
    READ
}

enum class CallMode {
    NORMAL,
    PRIORITY
}

data class ConversationItem(
    val id: String,
    val type: ConversationItemType,
    val direction: ConversationDirection,
    val createdAt: Long,
    val readAt: Long? = null,
    val deliveryState: DeliveryState = DeliveryState.LOCAL,
    val text: String? = null,
    val audioFileName: String? = null,
    val durationMs: Long = 0L,
    val callId: String? = null,
    val callOutcome: String? = null,
    val callMode: CallMode? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null
)

@Entity(
    tableName = "conversation_items",
    indices = [
        Index(
            value = [
                "type",
                "createdAt"
            ]
        ),
        Index(value = ["callId"])
    ]
)
internal data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val direction: String,
    val createdAt: Long,
    val readAt: Long?,
    val deliveryState: String,
    val text: String?,
    val audioFileName: String?,
    val durationMs: Long,
    val callId: String?,
    val callOutcome: String?,
    val callMode: String?,
    val startedAt: Long?,
    val endedAt: Long?
)

@Dao
internal interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(item: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(item: ConversationEntity): Long

    @Query(
        """
        SELECT * FROM conversation_items
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun timeline(limit: Int): List<ConversationEntity>

    @Query(
        """
        SELECT * FROM conversation_items
        WHERE type = 'VOICE'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun voices(limit: Int): List<ConversationEntity>

    @Query(
        """
        SELECT * FROM conversation_items
        WHERE type = 'VOICE'
          AND id = :id
        LIMIT 1
        """
    )
    fun voiceById(id: String): ConversationEntity?

    @Query(
        """
        SELECT COUNT(*) FROM conversation_items
        WHERE type = 'VOICE'
          AND direction = 'INCOMING'
          AND readAt IS NULL
        """
    )
    fun unreadVoiceCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM conversation_items
        WHERE type = 'TEXT'
          AND direction = 'INCOMING'
          AND readAt IS NULL
        """
    )
    fun unreadTextCount(): Int

    @Query(
        """
        UPDATE conversation_items
        SET readAt = :readAt,
            deliveryState = 'READ'
        WHERE type = 'TEXT'
          AND direction = 'INCOMING'
          AND readAt IS NULL
        """
    )
    fun markAllTextRead(readAt: Long): Int

    @Query(
        """
        UPDATE conversation_items
        SET readAt = :readAt,
            deliveryState = 'READ'
        WHERE id = :id
          AND type = 'VOICE'
        """
    )
    fun markVoiceRead(
        id: String,
        readAt: Long
    )

    @Query(
        """
        DELETE FROM conversation_items
        WHERE type = 'VOICE'
          AND id IN (:ids)
        """
    )
    fun deleteVoices(ids: Set<String>): Int

    @Query(
        """
        DELETE FROM conversation_items
        WHERE type = 'VOICE'
        """
    )
    fun clearVoices()

    @Query(
        """
        SELECT * FROM conversation_items
        WHERE type = 'CALL'
          AND callId = :callId
        LIMIT 1
        """
    )
    fun callByCallId(
        callId: String
    ): ConversationEntity?

    @Query(
        """
        SELECT * FROM conversation_items
        WHERE type = 'CALL'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun calls(limit: Int): List<ConversationEntity>

    @Query(
        """
        DELETE FROM conversation_items
        WHERE type = 'CALL'
        """
    )
    fun clearCalls()

    @Query(
        """
        DELETE FROM conversation_items
        WHERE id IN (:ids)
        """
    )
    fun deleteItems(ids: Set<String>): Int

    @Query(
        """
        UPDATE conversation_items
        SET deliveryState = :state
        WHERE id = :id
        """
    )
    fun updateDeliveryState(
        id: String,
        state: String
    )
}

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class ConversationDatabase :
    RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var instance:
            ConversationDatabase? = null

        fun get(
            context: Context
        ): ConversationDatabase =
            instance ?: synchronized(this) {
                instance ?:
                    Room.databaseBuilder(
                        context.applicationContext,
                        ConversationDatabase::class.java,
                        "happytalky-conversation.db"
                    )
                        // Existing Store APIs are synchronous and are
                        // currently read by the activity while rendering.
                        // Keep that contract for this migration; later UI
                        // work can move reads to Flow without changing the
                        // persisted schema.
                        .allowMainThreadQueries()
                        .build()
                        .also {
                            instance = it
                        }
            }
    }
}

object ConversationStore {
    private fun dao(
        context: Context
    ): ConversationDao =
        ConversationDatabase
            .get(context)
            .conversationDao()

    internal fun upsert(
        context: Context,
        item: ConversationItem
    ) {
        dao(context).upsert(
            item.toEntity()
        )
    }

    internal fun insertIfAbsent(
        context: Context,
        item: ConversationItem
    ): Boolean =
        dao(context).insertIfAbsent(
            item.toEntity()
        ) != -1L

    fun timeline(
        context: Context,
        limit: Int = 100
    ): List<ConversationItem> =
        dao(context)
            .timeline(limit)
            .mapNotNull {
                it.toModelOrNull()
            }

    internal fun voices(
        context: Context,
        limit: Int
    ): List<ConversationItem> =
        dao(context)
            .voices(limit)
            .mapNotNull {
                it.toModelOrNull()
            }

    internal fun voiceById(
        context: Context,
        id: String
    ): ConversationItem? =
        dao(context)
            .voiceById(id)
            ?.toModelOrNull()

    internal fun unreadVoiceCount(
        context: Context
    ): Int =
        dao(context).unreadVoiceCount()

    fun unreadTextCount(
        context: Context
    ): Int =
        dao(context).unreadTextCount()

    fun markAllTextRead(
        context: Context,
        readAt: Long =
            System.currentTimeMillis()
    ): Int =
        dao(context).markAllTextRead(
            readAt
        )

    internal fun markVoiceRead(
        context: Context,
        id: String,
        readAt: Long
    ) {
        dao(context).markVoiceRead(
            id,
            readAt
        )
    }

    internal fun deleteVoices(
        context: Context,
        ids: Set<String>
    ): Int =
        dao(context).deleteVoices(ids)

    internal fun clearVoices(
        context: Context
    ) {
        dao(context).clearVoices()
    }

    internal fun callByCallId(
        context: Context,
        callId: String
    ): ConversationItem? =
        dao(context)
            .callByCallId(callId)
            ?.toModelOrNull()

    internal fun calls(
        context: Context,
        limit: Int
    ): List<ConversationItem> =
        dao(context)
            .calls(limit)
            .mapNotNull {
                it.toModelOrNull()
            }

    internal fun clearCalls(
        context: Context
    ) {
        dao(context).clearCalls()
    }

    fun deleteItems(
        context: Context,
        ids: Set<String>
    ): Int =
        if (ids.isEmpty()) {
            0
        } else {
            dao(context).deleteItems(ids)
        }

    fun updateDeliveryState(
        context: Context,
        id: String,
        state: DeliveryState
    ) {
        dao(context).updateDeliveryState(
            id,
            state.name
        )
    }
}

internal fun ConversationItem.toEntity():
    ConversationEntity =
    ConversationEntity(
        id = id,
        type = type.name,
        direction = direction.name,
        createdAt = createdAt,
        readAt = readAt,
        deliveryState = deliveryState.name,
        text = text,
        audioFileName = audioFileName,
        durationMs = durationMs,
        callId = callId,
        callOutcome = callOutcome,
        callMode = callMode?.name,
        startedAt = startedAt,
        endedAt = endedAt
    )

internal fun ConversationEntity.toModelOrNull():
    ConversationItem? =
    runCatching {
        ConversationItem(
            id = id,
            type =
                ConversationItemType.valueOf(
                    type
                ),
            direction =
                ConversationDirection.valueOf(
                    direction
                ),
            createdAt = createdAt,
            readAt = readAt,
            deliveryState =
                DeliveryState.valueOf(
                    deliveryState
                ),
            text = text,
            audioFileName = audioFileName,
            durationMs = durationMs,
            callId = callId,
            callOutcome = callOutcome,
            callMode =
                callMode?.let {
                    CallMode.valueOf(it)
                },
            startedAt = startedAt,
            endedAt = endedAt
        )
    }.getOrNull()
