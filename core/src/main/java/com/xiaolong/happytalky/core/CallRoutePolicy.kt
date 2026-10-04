package com.xiaolong.happytalky.core

object CallRoutePolicy {
    fun canStartCall(
        connection: PeerConnectionState,
        route: PeerRoute
    ): Boolean =
        connection == PeerConnectionState.CONNECTED &&
            (
                route == PeerRoute.NEARBY_DIRECT ||
                    route == PeerRoute.REMOTE_WIFI ||
                    route == PeerRoute.REMOTE_CELLULAR ||
                    route == PeerRoute.REMOTE_INTERNET
            )

    fun preferTalk(route: PeerRoute): Boolean =
        route == PeerRoute.OFFLINE ||
            route == PeerRoute.RECONNECTING ||
            route == PeerRoute.UNKNOWN
}
