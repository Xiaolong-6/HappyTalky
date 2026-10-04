package com.xiaolong.happytalky.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.Executors

class DataLayerTransport(context: Context) {
    private val appContext = context.applicationContext
    private val role = EndpointRole.fromContext(appContext)
    private val capabilityClient =
        Wearable.getCapabilityClient(appContext)
    private val messageClient =
        Wearable.getMessageClient(appContext)
    private val dataClient =
        Wearable.getDataClient(appContext)
    private val mainHandler =
        Handler(Looper.getMainLooper())
    private val io =
        Executors.newSingleThreadExecutor()

    private val targetRole: EndpointRole
        get() =
            if (role == EndpointRole.PHONE) {
                EndpointRole.WATCH
            } else {
                EndpointRole.PHONE
            }

    private val targetCapability: String
        get() =
            if (targetRole == EndpointRole.WATCH) {
                Protocol.CAPABILITY_WATCH
            } else {
                Protocol.CAPABILITY_PHONE
            }

    fun findReachablePeer(
        callback: (Node?) -> Unit
    ) {
        capabilityClient
            .getCapability(
                targetCapability,
                CapabilityClient.FILTER_REACHABLE
            )
            .addOnSuccessListener { info ->
                val node =
                    info.nodes
                        .firstOrNull { it.isNearby }
                        ?: info.nodes
                            .sortedBy { it.id }
                            .firstOrNull()

                if (node != null) {
                    PeerInfoStore.saveNodeFallback(
                        appContext,
                        targetRole,
                        node.displayName
                    )
                }

                mainHandler.post {
                    callback(node)
                }
            }
            .addOnFailureListener {
                mainHandler.post {
                    callback(null)
                }
            }
    }

    fun refreshPeerConnection(
        callback: (
            PeerConnectionState,
            PeerRoute
        ) -> Unit = { _, _ -> }
    ) {
        findReachablePeer { node ->
            if (node == null) {
                updateConnection(
                    PeerConnectionState.DISCONNECTED,
                    PeerRoute.OFFLINE
                )
                callback(
                    PeerConnectionState.DISCONNECTED,
                    PeerRoute.OFFLINE
                )
                return@findReachablePeer
            }

            val route =
                if (node.isNearby) {
                    PeerRoute.NEARBY_DIRECT
                } else {
                    remoteRoute()
                }

            updateConnection(
                PeerConnectionState.CONNECTED,
                route
            )
            callback(
                PeerConnectionState.CONNECTED,
                route
            )
        }
    }

    fun sendSignal(
        path: String,
        callId: String,
        callback: (Boolean) -> Unit
    ) {
        findReachablePeer { node ->
            if (node == null) {
                updateConnection(
                    PeerConnectionState.DISCONNECTED,
                    PeerRoute.OFFLINE
                )
                callback(false)
                return@findReachablePeer
            }

            val route =
                if (node.isNearby) {
                    PeerRoute.NEARBY_DIRECT
                } else {
                    remoteRoute()
                }

            messageClient
                .sendMessage(
                    node.id,
                    path,
                    callId.toByteArray(Charsets.UTF_8)
                )
                .addOnSuccessListener {
                    updateConnection(
                        PeerConnectionState.CONNECTED,
                        route
                    )
                    mainHandler.post {
                        callback(true)
                    }
                }
                .addOnFailureListener {
                    refreshPeerConnection { _, _ ->
                        mainHandler.post {
                            callback(false)
                        }
                    }
                }
        }
    }

    fun requestPeerDeviceInfo(
        callback: (Boolean) -> Unit = {}
    ) {
        sendSignal(
            Protocol.DEVICE_INFO_REQUEST,
            LocalDeviceIdentity.id(
                appContext
            ),
            callback
        )
    }

    fun publishDeviceInfo(
        callback: (Boolean) -> Unit = {}
    ) {
        val info =
            LocalDeviceIdentity.info(
                appContext
            )
        val request =
            PutDataMapRequest.create(
                Protocol.DEVICE_INFO_PREFIX +
                    info.deviceId
            )

        request.dataMap.putString(
            Protocol.KEY_ID,
            info.deviceId
        )
        request.dataMap.putString(
            Protocol.KEY_ROLE,
            info.role.wireValue
        )
        request.dataMap.putString(
            Protocol.KEY_MANUFACTURER,
            info.manufacturer
        )
        request.dataMap.putString(
            Protocol.KEY_MODEL,
            info.model
        )
        request.dataMap.putString(
            Protocol.KEY_APP_VERSION,
            info.appVersion
        )
        request.dataMap.putInt(
            Protocol.KEY_PROTOCOL_VERSION,
            info.protocolVersion
        )
        request.dataMap.putStringArrayList(
            Protocol.KEY_CAPABILITIES,
            ArrayList(
                info.capabilities.sorted()
            )
        )
        request.dataMap.putBoolean(
            Protocol.KEY_PRIORITY_AUTO_ANSWER,
            info.priorityAutoAnswerEnabled
        )
        request.dataMap.putLong(
            Protocol.KEY_DEVICE_INFO_UPDATED_AT,
            info.updatedAt
        )

        dataClient.putDataItem(
            request.asPutDataRequest()
                .setUrgent()
        )
            .addOnSuccessListener {
                mainHandler.post {
                    callback(true)
                }
            }
            .addOnFailureListener {
                mainHandler.post {
                    callback(false)
                }
            }
    }

    fun queueText(
        item: ConversationItem,
        role: EndpointRole,
        callback: (Boolean) -> Unit
    ) {
        val text =
            item.text
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: run {
                    callback(false)
                    return
                }

        val mapRequest =
            PutDataMapRequest.create(
                Protocol.MESSAGE_PREFIX +
                    item.id
            )

        mapRequest.dataMap.putString(
            Protocol.KEY_ID,
            item.id
        )
        mapRequest.dataMap.putString(
            Protocol.KEY_ORIGIN,
            role.wireValue
        )
        mapRequest.dataMap.putLong(
            Protocol.KEY_CREATED_AT,
            item.createdAt
        )
        mapRequest.dataMap.putString(
            Protocol.KEY_TEXT,
            text
        )

        dataClient.putDataItem(
            mapRequest
                .asPutDataRequest()
                .setUrgent()
        )
            .addOnSuccessListener {
                mainHandler.post {
                    callback(true)
                }
            }
            .addOnFailureListener {
                mainHandler.post {
                    callback(false)
                }
            }
    }

    fun queueVoice(
        message: VoiceMessage,
        role: EndpointRole,
        callback: (Boolean) -> Unit
    ) {
        io.execute {
            try {
                val bytes =
                    message.file.readBytes()
                val mapRequest =
                    PutDataMapRequest.create(
                        Protocol.VOICE_PREFIX +
                            message.id
                    )

                mapRequest.dataMap.putString(
                    Protocol.KEY_ID,
                    message.id
                )
                mapRequest.dataMap.putString(
                    Protocol.KEY_ORIGIN,
                    role.wireValue
                )
                mapRequest.dataMap.putLong(
                    Protocol.KEY_CREATED_AT,
                    message.createdAt
                )
                mapRequest.dataMap.putAsset(
                    Protocol.KEY_AUDIO,
                    Asset.createFromBytes(bytes)
                )

                dataClient
                    .putDataItem(
                        mapRequest
                            .asPutDataRequest()
                            .setUrgent()
                    )
                    .addOnSuccessListener {
                        mainHandler.post {
                            callback(true)
                        }
                    }
                    .addOnFailureListener {
                        mainHandler.post {
                            callback(false)
                        }
                    }
            } catch (_: Exception) {
                mainHandler.post {
                    callback(false)
                }
            }
        }
    }

    private fun remoteRoute(): PeerRoute {
        val manager =
            appContext.getSystemService(
                ConnectivityManager::class.java
            ) ?: return PeerRoute.REMOTE_INTERNET

        val network =
            manager.activeNetwork
                ?: return PeerRoute.REMOTE_INTERNET
        val capabilities =
            manager.getNetworkCapabilities(network)
                ?: return PeerRoute.REMOTE_INTERNET

        return when {
            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) ->
                PeerRoute.REMOTE_WIFI

            capabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) ->
                PeerRoute.REMOTE_CELLULAR

            else ->
                PeerRoute.REMOTE_INTERNET
        }
    }

    private fun updateConnection(
        state: PeerConnectionState,
        route: PeerRoute
    ) {
        StateStore.setPeerConnection(
            appContext,
            state
        )
        StateStore.setPeerRoute(
            appContext,
            route
        )
        EventBus.notifyStateChanged(
            appContext
        )
    }
}
