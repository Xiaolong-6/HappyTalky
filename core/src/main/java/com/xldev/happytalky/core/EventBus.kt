package com.xldev.happytalky.core

import android.content.Context
import android.content.Intent

object EventBus {
    fun notifyStateChanged(context: Context) {
        context.sendBroadcast(
            Intent(Protocol.ACTION_STATE_CHANGED).setPackage(context.packageName)
        )
    }
}
