package com.example.ads_sdk.utils

import android.os.Handler
import android.os.Looper
import com.example.ads_sdk.config.RemoteConfigManager
import com.example.ads_sdk.domain.model.AdType
import java.util.EnumMap
import kotlin.math.min

/**
 * Per-format retry scheduler with bounded exponential backoff. It keeps retry
 * policy consistent and avoids tight reload loops after network or no-fill errors.
 */
internal class RetryController {
    private val handler = Handler(Looper.getMainLooper())
    private val attempts = EnumMap<AdType, Int>(AdType::class.java)
    private val pending = EnumMap<AdType, Runnable>(AdType::class.java)

    fun reset(type: AdType) {
        attempts[type] = 0
        pending.remove(type)?.let(handler::removeCallbacks)
    }

    fun schedule(type: AdType, action: () -> Unit) {
        val config = RemoteConfigManager.getConfig()
        if (!config.retryEnabled) return

        pending.remove(type)?.let(handler::removeCallbacks)
        val nextAttempt = (attempts[type] ?: 0) + 1
        attempts[type] = nextAttempt

        val base = config.retryInitialDelayMillis.coerceAtLeast(10_000L)
        val max = config.retryMaxDelayMillis.coerceAtLeast(base).coerceAtMost(30_000L)
        val multiplier = 1L shl min(nextAttempt - 1, 4)
        val delay = (base * multiplier).coerceIn(10_000L, max)

        AdsLogger.d("Scheduling ${type.name} retry in ${delay}ms.")
        val runnable = Runnable { MainThread.run(action) }
        pending[type] = runnable
        handler.postDelayed(runnable, delay)
    }
}
