package com.example.ads_sdk.core

import android.app.ActivityManager
import android.content.Context
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.model.DeviceTier
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min

/** Device and session signal layer for fatigue-aware frequency decisions. */
object DeviceSignalManager {
    @Volatile
    private var deviceTier: DeviceTier = DeviceTier.MID
    private val fatigue = EnumMap<AdType, Int>(AdType::class.java)
    private var engagementScore: Int = 0

    fun initialize(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass ?: 128
        val lowRam = activityManager?.isLowRamDevice ?: false
        val cores = Runtime.getRuntime().availableProcessors()
        deviceTier = when {
            lowRam || memoryClass <= 128 || cores <= 4 -> DeviceTier.LOW
            memoryClass >= 256 && cores >= 8 -> DeviceTier.HIGH
            else -> DeviceTier.MID
        }
    }

    fun getDeviceTier(): DeviceTier = deviceTier

    @Synchronized
    fun recordAdShown(type: AdType) {
        fatigue[type] = (fatigue[type] ?: 0) + 1
        SessionManager.incrementAdCount(type)
    }

    @Synchronized
    fun recordEngagement(delta: Int = 1) {
        engagementScore = (engagementScore + delta).coerceIn(0, 100)
    }

    @Synchronized
    fun adjustedInterstitialInterval(baseInterval: Int): Int {
        val fatiguePenalty = (fatigue[AdType.INTERSTITIAL] ?: 0) / 2
        val engagementCredit = engagementScore / 25
        val tierPenalty = if (deviceTier == DeviceTier.LOW) 1 else 0
        return max(1, baseInterval + fatiguePenalty + tierPenalty - min(engagementCredit, 2))
    }

    @Synchronized
    fun resetSession() {
        fatigue.clear()
        engagementScore = 0
    }
}
