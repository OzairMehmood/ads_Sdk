package com.example.ads_sdk.utils

import android.util.Log
import com.example.ads_sdk.config.RemoteConfigManager

/** Logging levels supported by the SDK. */
enum class LogLevel {
    NONE,
    ERROR,
    DEBUG
}

/** Small logger wrapper so host apps can toggle SDK verbosity from AdsConfig. */
internal object AdsLogger {
    private const val TAG = "AdsSdk"

    fun d(message: String) {
        val config = RemoteConfigManager.getConfig()
        if (config.debugLogs && config.logLevel == LogLevel.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        val config = RemoteConfigManager.getConfig()
        if (config.debugLogs && config.logLevel != LogLevel.NONE) {
            Log.e(TAG, message, throwable)
        }
    }
}
