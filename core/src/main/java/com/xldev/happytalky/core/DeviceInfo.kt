package com.xldev.happytalky.core

import android.content.Context
import android.os.Build
import java.util.UUID

data class DeviceInfo(
    val deviceId: String,
    val role: EndpointRole,
    val manufacturer: String,
    val model: String,
    val appVersion: String,
    val protocolVersion: Int,
    val capabilities: Set<String>,
    val priorityAutoAnswerEnabled: Boolean = false,
    val updatedAt: Long = 0L
) {
    fun displayLabel(): String {
        val roleLabel =
            if (role == EndpointRole.WATCH) {
                "Watch"
            } else {
                "Phone"
            }

        val deviceLabel =
            model.trim()
                .takeIf {
                    it.isNotBlank() &&
                        !it.equals(
                            roleLabel,
                            ignoreCase = true
                        )
                }

        return if (deviceLabel != null) {
            "$roleLabel · $deviceLabel"
        } else {
            roleLabel
        }
    }
}

object LocalDeviceIdentity {
    private const val PREFS =
        "happytalky_device_identity"
    private const val KEY_ID =
        "stable_device_id"
    private const val PROTOCOL_VERSION = 1

    fun id(context: Context): String {
        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
        val existing =
            prefs.getString(
                KEY_ID,
                null
            )

        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created =
            UUID.randomUUID().toString()
        prefs.edit()
            .putString(
                KEY_ID,
                created
            )
            .apply()
        return created
    }

    fun info(
        context: Context
    ): DeviceInfo {
        val role =
            EndpointRole.fromContext(
                context
            )
        val packageInfo =
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    0
                )

        return DeviceInfo(
            deviceId = id(context),
            role = role,
            manufacturer =
                Build.MANUFACTURER
                    .orEmpty()
                    .trim(),
            model =
                Build.MODEL
                    .orEmpty()
                    .trim(),
            appVersion =
                packageInfo.versionName
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: "unknown",
            protocolVersion =
                PROTOCOL_VERSION,
            capabilities =
                buildSet {
                    add(
                        Protocol
                            .CAPABILITY_TALK_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_CALL_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_DEVICE_INFO_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_CONVERSATION_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_PRIORITY_CALL_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_PRIORITY_LOCKED_CALL_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_TEXT_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_BLE_PROXIMITY_V1
                    )
                    add(
                        Protocol
                            .CAPABILITY_BLE_PROXIMITY_V2
                    )
                },
            priorityAutoAnswerEnabled =
                role == EndpointRole.WATCH &&
                    PriorityCallSettings.isEnabled(
                        context
                    ),
            updatedAt =
                System.currentTimeMillis()
        )
    }
}

object PeerInfoStore {
    private const val PREFS =
        "happytalky_peer_info"

    fun save(
        context: Context,
        info: DeviceInfo
    ) {
        val prefix =
            info.role.wireValue
        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
        val existingUpdatedAt =
            prefs.getLong(
                "$prefix.updatedAt",
                0L
            )

        if (
            !PeerDeviceInfoPolicy.shouldReplace(
                existingUpdatedAt =
                    existingUpdatedAt,
                incomingUpdatedAt =
                    info.updatedAt
            )
        ) {
            return
        }

        prefs
            .edit()
            .putString(
                "$prefix.deviceId",
                info.deviceId
            )
            .putString(
                "$prefix.manufacturer",
                info.manufacturer
            )
            .putString(
                "$prefix.model",
                info.model
            )
            .putString(
                "$prefix.appVersion",
                info.appVersion
            )
            .putInt(
                "$prefix.protocolVersion",
                info.protocolVersion
            )
            .putStringSet(
                "$prefix.capabilities",
                info.capabilities
            )
            .putBoolean(
                "$prefix.priorityAutoAnswer",
                info.priorityAutoAnswerEnabled
            )
            .putLong(
                "$prefix.updatedAt",
                info.updatedAt
            )
            .apply()
    }

    fun saveNodeFallback(
        context: Context,
        role: EndpointRole,
        displayName: String?
    ) {
        val value =
            displayName
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return

        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "${role.wireValue}.nodeDisplayName",
                value
            )
            .apply()
    }

    fun get(
        context: Context,
        role: EndpointRole
    ): DeviceInfo? {
        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
        val prefix =
            role.wireValue
        val deviceId =
            prefs.getString(
                "$prefix.deviceId",
                null
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: return null

        return DeviceInfo(
            deviceId = deviceId,
            role = role,
            manufacturer =
                prefs.getString(
                    "$prefix.manufacturer",
                    ""
                ).orEmpty(),
            model =
                prefs.getString(
                    "$prefix.model",
                    ""
                ).orEmpty(),
            appVersion =
                prefs.getString(
                    "$prefix.appVersion",
                    "unknown"
                ).orEmpty(),
            protocolVersion =
                prefs.getInt(
                    "$prefix.protocolVersion",
                    0
                ),
            capabilities =
                prefs.getStringSet(
                    "$prefix.capabilities",
                    emptySet()
                )
                    ?.toSet()
                    .orEmpty(),
            priorityAutoAnswerEnabled =
                prefs.getBoolean(
                    "$prefix.priorityAutoAnswer",
                    false
                ),
            updatedAt =
                prefs.getLong(
                    "$prefix.updatedAt",
                    0L
                )
        )
    }

    fun peerLabel(
        context: Context,
        localRole: EndpointRole
    ): String {
        val peerRole =
            if (
                localRole ==
                    EndpointRole.PHONE
            ) {
                EndpointRole.WATCH
            } else {
                EndpointRole.PHONE
            }

        get(
            context,
            peerRole
        )?.let {
            return it.displayLabel()
        }

        val fallback =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
                .getString(
                    "${peerRole.wireValue}.nodeDisplayName",
                    null
                )
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val roleLabel =
            if (
                peerRole ==
                    EndpointRole.WATCH
            ) {
                "Watch"
            } else {
                "Phone"
            }

        return if (
            fallback != null &&
            !fallback.equals(
                roleLabel,
                ignoreCase = true
            )
        ) {
            "$roleLabel · $fallback"
        } else {
            roleLabel
        }
    }

    fun peerSupports(
        context: Context,
        localRole: EndpointRole,
        capability: String
    ): Boolean {
        val peerRole =
            if (
                localRole ==
                    EndpointRole.PHONE
            ) {
                EndpointRole.WATCH
            } else {
                EndpointRole.PHONE
            }

        return get(
            context,
            peerRole
        )?.capabilities
            ?.contains(
                capability
            ) == true
    }
}

object PeerDeviceInfoPolicy {
    fun shouldReplace(
        existingUpdatedAt: Long,
        incomingUpdatedAt: Long
    ): Boolean =
        when {
            existingUpdatedAt <= 0L ->
                true

            incomingUpdatedAt <= 0L ->
                false

            else ->
                incomingUpdatedAt >=
                    existingUpdatedAt
        }
}

object TextCapabilityPolicy {
    fun canSend(
        connection: PeerConnectionState,
        peerInfo: DeviceInfo?
    ): Boolean =
        connection ==
            PeerConnectionState.CONNECTED ||
            peerInfo?.capabilities
                ?.contains(
                    Protocol
                        .CAPABILITY_TEXT_V1
                ) == true
}

object PriorityCallSettings {
    private const val PREFS =
        "happytalky_priority_call"
    private const val KEY_ENABLED =
        "auto_answer_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getBoolean(
            KEY_ENABLED,
            false
        )

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_ENABLED,
                enabled
            )
            .apply()
    }
}
