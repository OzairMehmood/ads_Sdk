package com.example.ads_sdk.core

import android.os.SystemClock
import com.example.ads_sdk.domain.model.AdType
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Tracks foreground app sessions and per-session counters shared by all SDK systems. */
object SessionManager {
    @Volatile
    private var sessionId: String = UUID.randomUUID().toString()
    private val startedAtMillis = AtomicLong(SystemClock.elapsedRealtime())
    private val adCounters = EnumMap<AdType, Int>(AdType::class.java)

    @Synchronized
    fun startNewSession(): String {
        sessionId = UUID.randomUUID().toString()
        startedAtMillis.set(SystemClock.elapsedRealtime())
        adCounters.clear()
        DeviceSignalManager.resetSession()
        return sessionId
    }

    fun currentSessionId(): String = sessionId

    @Synchronized
    fun incrementAdCount(type: AdType): Int {
        val next = (adCounters[type] ?: 0) + 1
        adCounters[type] = next
        return next
    }

    fun sessionAgeMillis(): Long = SystemClock.elapsedRealtime() - startedAtMillis.get()
}
