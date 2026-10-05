package com.xldev.happytalky.core

import android.content.Context
import android.content.pm.PackageManager

enum class EndpointRole(val wireValue: String) {
    PHONE("phone"),
    WATCH("watch");

    companion object {
        fun fromContext(context: Context): EndpointRole {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            return when (info.metaData?.getString(Protocol.META_ROLE)) {
                WATCH.wireValue -> WATCH
                else -> PHONE
            }
        }
    }
}
