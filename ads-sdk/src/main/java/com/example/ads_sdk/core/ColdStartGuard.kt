package com.example.ads_sdk.core

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.ads_sdk.config.RemoteConfigManager
import com.example.ads_sdk.utils.AdsLogger
import java.util.concurrent.atomic.AtomicBoolean

/** Delays first ad work so app startup, first frame, and splash navigation stay clean. */
internal object ColdStartGuard {
    private val handler = Handler(Looper.getMainLooper())
    private val initializedAtMillis = java.util.concurrent.atomic.AtomicLong(0L)
    private val launchWorkScheduled = AtomicBoolean(false)
    private val pendingActions = mutableListOf<() -> Unit>()

    fun markInitialized() {
        initializedAtMillis.set(SystemClock.elapsedRealtime())
        launchWorkScheduled.set(false)
    }

    fun isReady(): Boolean {
        if (!SdkInfo.isFeatureEnabled(SdkFeature.COLD_START_PROTECTION)) return true
        val elapsed = SystemClock.elapsedRealtime() - initializedAtMillis.get()
        return elapsed >= RemoteConfigManager.getConfig().coldStartDelayMillis
    }

    fun runWhenReady(action: () -> Unit): Boolean {
        val config = RemoteConfigManager.getConfig()
        if (!SdkInfo.isFeatureEnabled(SdkFeature.COLD_START_PROTECTION)) {
            action()
            return true
        }

        val elapsed = SystemClock.elapsedRealtime() - initializedAtMillis.get()
        val remainingDelay = (config.coldStartDelayMillis - elapsed).coerceAtLeast(0L)
        if (remainingDelay == 0L) {
            action()
            return true
        }

        synchronized(pendingActions) {
            pendingActions.add(action)
        }
        if (!launchWorkScheduled.compareAndSet(false, true)) {
            AdsLogger.d("Cold start guard suppressed duplicate launch ad load.")
            return false
        }
        handler.postDelayed({
            launchWorkScheduled.set(false)
            val actions = synchronized(pendingActions) {
                pendingActions.toList().also { pendingActions.clear() }
            }
            actions.forEach { it() }
        }, remainingDelay)
        return false
    }
}
