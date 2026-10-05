package com.xldev.happytalky.core

import android.content.Context
import java.util.UUID

object TextMessageStore {
    fun saveOutgoing(
        context: Context,
        rawText: String
    ): ConversationItem {
        val text =
            normalize(rawText)
                ?: error("Text message is empty")
        val item =
            ConversationItem(
                id =
                    UUID.randomUUID()
                        .toString(),
                type =
                    ConversationItemType.TEXT,
                direction =
                    ConversationDirection.OUTGOING,
                createdAt =
                    System.currentTimeMillis(),
                readAt = null,
                deliveryState =
                    DeliveryState.LOCAL,
                text = text
            )

        ConversationStore.upsert(
            context,
            item
        )
        return item
    }

    fun saveIncoming(
        context: Context,
        id: String,
        createdAt: Long,
        rawText: String
    ): Boolean? {
        val text =
            normalize(rawText)
                ?: return null
        val item =
            ConversationItem(
                id = id,
                type =
                    ConversationItemType.TEXT,
                direction =
                    ConversationDirection.INCOMING,
                createdAt =
                    createdAt
                        .takeIf {
                            it > 0L
                        }
                        ?: System.currentTimeMillis(),
                readAt = null,
                deliveryState =
                    DeliveryState.DELIVERED,
                text = text
            )

        return ConversationStore
            .insertIfAbsent(
                context,
                item
            )
    }

    fun unreadCount(
        context: Context
    ): Int =
        ConversationStore.unreadTextCount(
            context
        )

    fun markAllRead(
        context: Context
    ): Int =
        ConversationStore.markAllTextRead(
            context
        )

    fun normalize(
        rawText: String
    ): String? =
        rawText
            .trim()
            .takeIf {
                it.isNotEmpty()
            }
            ?.let(::limit)

    fun limit(
        rawText: String
    ): String {
        val count =
            rawText.codePointCount(
                0,
                rawText.length
            )

        if (
            count <=
                Protocol.MAX_TEXT_LENGTH
        ) {
            return rawText
        }

        val end =
            rawText.offsetByCodePoints(
                0,
                Protocol.MAX_TEXT_LENGTH
            )

        return rawText.substring(
            0,
            end
        )
    }
}
