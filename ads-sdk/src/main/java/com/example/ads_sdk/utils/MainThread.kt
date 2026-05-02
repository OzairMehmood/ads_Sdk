package com.example.ads_sdk.utils

import android.os.Handler
import android.os.Looper

/** Ensures AdMob view and show operations are always executed on the main thread. */
internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun run(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            handler.post(action)
        }
    }
}
