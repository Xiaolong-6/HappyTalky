package com.xiaolong.happytalky.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

object AlertController {
    // v4 intentionally recreates the call channel after the Wear incoming
    // presentation change. Android preserves old channel alert settings
    // across app updates, so v3's silent/no-vibration configuration could
    // leave a Watch with no glanceable incoming-call cue once unsupported
    // full-screen intents were removed.
    private const val CALL_CHANNEL = "happytalky_calls_v4"
    // v2 intentionally recreates the message channel. Android keeps channel
    // alert behavior across app updates, so installs that already have v1
    // configured quietly would otherwise stay quiet after this fix.
    private const val MESSAGE_CHANNEL = "happytalky_messages_v2"
    private const val CALL_NOTIFICATION_ID = 1001
    private const val MESSAGE_NOTIFICATION_ID = 1002

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var timeoutRunnable: Runnable? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun startIncomingCall(
        context: Context,
        callId: String,
        priority: Boolean = false,
        locked: Boolean = false
    ) {
        val appContext = context.applicationContext
        stop(appContext)
        ensureChannels(appContext)
        postCallNotification(
            appContext,
            priority,
            locked
        )
        acquireWakeLock(appContext)
        startRinging(appContext)

        if (!locked) {
            timeoutRunnable = Runnable {
                if (
                    StateStore.incomingCall(
                        appContext
                    ) == callId
                ) {
                    CallHistoryStore.append(
                        appContext,
                        callId,
                        CallDirection.INCOMING,
                        CallOutcome.MISSED,
                        mode =
                            StateStore.callMode(
                                appContext
                            )
                    )
                    StateStore.setIncomingCall(
                        appContext,
                        null
                    )
                    StateStore.setStatus(
                        appContext,
                        "Missed call — hold TALK to reply"
                    )
                    EventBus.notifyStateChanged(
                        appContext
                    )
                }
                stop(appContext)
            }.also {
                handler.postDelayed(
                    it,
                    Protocol.CALL_TIMEOUT_MS
                )
            }
        }
    }

    fun stop(context: Context) {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        val manager =
            context.getSystemService(NotificationManager::class.java)
        manager?.cancel(CALL_NOTIFICATION_ID)
    }

    fun postVoiceNotification(
        context: Context
    ) {
        refreshMessageNotification(
            context
        )
    }

    fun postTextNotification(
        context: Context
    ) {
        refreshMessageNotification(
            context
        )
    }

    fun refreshVoiceNotification(
        context: Context
    ) {
        refreshMessageNotification(
            context
        )
    }

    fun refreshMessageNotification(
        context: Context
    ) {
        ensureChannels(context)

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            ) ?: return
        val voiceUnread =
            VoiceMessageStore.unreadCount(
                context
            )
        val textUnread =
            TextMessageStore.unreadCount(
                context
            )
        val unread =
            voiceUnread + textUnread

        if (unread <= 0) {
            manager.cancel(
                MESSAGE_NOTIFICATION_ID
            )
            return
        }

        if (!canNotify(context)) {
            return
        }

        val title =
            when {
                voiceUnread > 0 &&
                    textUnread == 0 ->
                    if (voiceUnread == 1) {
                        "New TALK"
                    } else {
                        "$voiceUnread new TALK messages"
                    }

                textUnread > 0 &&
                    voiceUnread == 0 ->
                    if (textUnread == 1) {
                        "New text message"
                    } else {
                        "$textUnread new text messages"
                    }

                else ->
                    "$unread new messages"
            }

        manager.notify(
            MESSAGE_NOTIFICATION_ID,
            baseBuilder(
                context,
                MESSAGE_CHANNEL
            )
                .setContentTitle(title)
                .setContentText(
                    "$unread unread " +
                        if (unread == 1) {
                            "message"
                        } else {
                            "messages"
                        }
                )
                .setContentIntent(
                    launcherPendingIntent(
                        context,
                        openInbox = true
                    )
                )
                .setCategory(
                    Notification.CATEGORY_MESSAGE
                )
                .setPriority(
                    Notification.PRIORITY_HIGH
                )
                .setNumber(unread)
                .setAutoCancel(false)
                .build()
        )
    }

    private fun postCallNotification(
        context: Context,
        priority: Boolean,
        locked: Boolean
    ) {
        if (!canNotify(context)) return

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            ) ?: return

        val open =
            launcherPendingIntent(context)
        val answer =
            actionPendingIntent(
                context,
                CallActionReceiver.ACTION_ANSWER,
                21
            )
        val decline =
            actionPendingIntent(
                context,
                CallActionReceiver.ACTION_DECLINE,
                22
            )
        val title =
            if (priority) {
                "PRIORITY CALL"
            } else {
                "Incoming call"
            }
        val text =
            if (priority) {
                "Open HappyTalky for priority call"
            } else {
                "HappyTalky"
            }
        val isWatch =
            context.packageManager
                .hasSystemFeature(
                    PackageManager.FEATURE_WATCH
                )

        if (isWatch) {
            val builder =
                NotificationCompat.Builder(
                    context,
                    CALL_CHANNEL
                )
                    .setSmallIcon(
                        android.R.drawable
                            .sym_call_incoming
                    )
                    .setContentTitle(title)
                    .setContentText(
                        if (locked) {
                            "Auto-connecting · tap to open"
                        } else {
                            text
                        }
                    )
                    .setContentIntent(open)
                    .setCategory(
                        NotificationCompat
                            .CATEGORY_CALL
                    )
                    .setPriority(
                        NotificationCompat
                            .PRIORITY_MAX
                    )
                    .setVisibility(
                        NotificationCompat
                            .VISIBILITY_PUBLIC
                    )
                    .setOngoing(true)
                    .setAutoCancel(false)

            if (!locked) {
                builder
                    .setTimeoutAfter(
                        Protocol.CALL_TIMEOUT_MS
                    )
                    .addAction(
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable
                                    .sym_action_call,
                                "Answer",
                                answer
                            )
                            .build()
                    )
                    .addAction(
                        NotificationCompat.Action
                            .Builder(
                                android.R.drawable
                                    .ic_menu_close_clear_cancel,
                                "Decline",
                                decline
                            )
                            .build()
                    )
            } else {
                builder.addAction(
                    NotificationCompat.Action
                        .Builder(
                            android.R.drawable
                                .sym_action_call,
                            "Open",
                            open
                        )
                        .build()
                )
            }

            manager.notify(
                CALL_NOTIFICATION_ID,
                builder.build()
            )
            return
        }

        val builder =
            baseBuilder(
                context,
                CALL_CHANNEL
            )
                .setContentTitle(
                    if (priority) {
                        "Priority call"
                    } else {
                        "HappyTalky"
                    }
                )
                .setContentText(
                    if (priority) {
                        "Open HappyTalky for priority call"
                    } else {
                        "Incoming call"
                    }
                )
                .setCategory(
                    Notification.CATEGORY_CALL
                )
                .setPriority(
                    Notification.PRIORITY_MAX
                )
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(
                    open,
                    true
                )

        if (!locked) {
            builder.setTimeoutAfter(
                Protocol.CALL_TIMEOUT_MS
            )
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val caller =
                Person.Builder()
                    .setName(
                        if (priority) {
                            "Priority call"
                        } else {
                            "HappyTalky"
                        }
                    )
                    .setImportant(true)
                    .build()
            builder.setStyle(
                Notification.CallStyle
                    .forIncomingCall(
                        caller,
                        decline,
                        answer
                    )
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable
                        .sym_action_call,
                    "Answer",
                    answer
                ).build()
            )
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable
                        .ic_menu_close_clear_cancel,
                    "Decline",
                    decline
                ).build()
            )
        }

        manager.notify(
            CALL_NOTIFICATION_ID,
            builder.build()
        )
    }

    private fun baseBuilder(
        context: Context,
        channelId: String
    ): Notification.Builder =
        Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(launcherPendingIntent(context))
            .setVisibility(Notification.VISIBILITY_PUBLIC)

    private fun launcherPendingIntent(
        context: Context,
        openInbox: Boolean = false
    ): PendingIntent {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            ?: Intent(Intent.ACTION_MAIN)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (openInbox) {
            intent.putExtra(
                Protocol.EXTRA_OPEN_INBOX,
                true
            )
        }

        return PendingIntent.getActivity(
            context,
            if (openInbox) 23 else 20,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(action)

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return

        val manager =
            context.getSystemService(NotificationManager::class.java)
                ?: return

        if (manager.getNotificationChannel(CALL_CHANNEL) == null) {
            val channel = NotificationChannel(
                CALL_CHANNEL,
                "HappyTalky calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description =
                "Incoming HappyTalky calls"
            channel.setSound(null, null)
            channel.enableVibration(true)
            channel.vibrationPattern =
                longArrayOf(
                    0L,
                    500L,
                    250L,
                    500L
                )
            manager.createNotificationChannel(channel)
        }

        if (manager.getNotificationChannel(MESSAGE_CHANNEL) == null) {
            val channel = NotificationChannel(
                MESSAGE_CHANNEL,
                "HappyTalky messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description =
                "New HappyTalky TALK and text messages"
            val notificationSound =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )
            val audioAttributes =
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()
            channel.setSound(
                notificationSound,
                audioAttributes
            )
            channel.enableVibration(true)
            channel.vibrationPattern =
                longArrayOf(
                    0L,
                    250L,
                    120L,
                    350L
                )
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock(context: Context) {
        val manager =
            context.getSystemService(PowerManager::class.java)
                ?: return

        val lock = manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HappyTalky:IncomingCall"
        )
        lock.setReferenceCounted(false)
        runCatching {
            lock.acquire(Protocol.CALL_TIMEOUT_MS + 5_000L)
        }
        wakeLock = lock
    }

    private fun startRinging(context: Context) {
        val uri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )

        val currentRingtone =
            RingtoneManager.getRingtone(context, uri)

        if (Build.VERSION.SDK_INT >= 28) {
            currentRingtone?.isLooping = true
        }

        runCatching { currentRingtone?.play() }
        ringtone = currentRingtone

        val currentVibrator =
            if (Build.VERSION.SDK_INT >= 31) {
                context
                    .getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator
            }

        val pattern = longArrayOf(0, 600, 300, 600, 500)
        if (currentVibrator != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                currentVibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, 0)
                )
            } else {
                @Suppress("DEPRECATION")
                currentVibrator.vibrate(pattern, 0)
            }
        }
        vibrator = currentVibrator
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
