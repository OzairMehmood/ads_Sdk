package com.example.ads_sdk.core

import android.os.SystemClock
import com.example.ads_sdk.config.RemoteConfigManager
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.state.AdStateSnapshot
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Global atomic state manager for every ad format. It prevents duplicate loads,
 * duplicate show calls, cooldown violations, and overly chatty ad requests.
 */
object AdStateManager {
    private val states = EnumMap<AdType, AdState>(AdType::class.java)

    init {
        AdType.entries.forEach { states[it] = AdState() }
    }

    fun tryBeginLoad(type: AdType, debounceMillis: Long = RemoteConfigManager.getConfig().loadDebounceMillis): Boolean {
        val state = states.getValue(type)
        val now = SystemClock.elapsedRealtime()
        if (state.ready.get() || state.showing.get()) return false
        if (now - state.lastRequestAt.get() < debounceMillis) return false
        if (!state.loading.compareAndSet(false, true)) return false
        state.lastRequestAt.set(now)
        state.updatedAt.set(now)
        return true
    }

    fun markReady(type: AdType) {
        val state = states.getValue(type)
        val now = SystemClock.elapsedRealtime()
        state.loading.set(false)
        state.ready.set(true)
        state.updatedAt.set(now)
    }

    fun markFailed(type: AdType, cooldownMillis: Long = 0L) {
        val state = states.getValue(type)
        val now = SystemClock.elapsedRealtime()
        state.loading.set(false)
        state.ready.set(false)
        if (cooldownMillis > 0L) {
            state.cooldownUntil.set(now + cooldownMillis)
        }
        state.updatedAt.set(now)
    }

    fun tryBeginShow(type: AdType): Boolean {
        val state = states.getValue(type)
        val now = SystemClock.elapsedRealtime()
        if (!state.ready.get()) return false
        if (now < state.cooldownUntil.get()) return false
        if (!state.showing.compareAndSet(false, true)) return false
        state.ready.set(false)
        state.updatedAt.set(now)
        return true
    }

    fun finishShow(type: AdType, cooldownMillis: Long = 0L) {
        val state = states.getValue(type)
        val now = SystemClock.elapsedRealtime()
        state.showing.set(false)
        if (cooldownMillis > 0L) {
            state.cooldownUntil.set(now + cooldownMillis)
        }
        state.updatedAt.set(now)
    }

    fun markConsumed(type: AdType) {
        val state = states.getValue(type)
        state.ready.set(false)
        state.updatedAt.set(SystemClock.elapsedRealtime())
    }

    fun isReady(type: AdType): Boolean = states.getValue(type).ready.get()

    fun snapshot(type: AdType): AdStateSnapshot {
        val state = states.getValue(type)
        return AdStateSnapshot(
            type = type,
            isLoading = state.loading.get(),
            isReady = state.ready.get(),
            isShowing = state.showing.get(),
            cooldownUntilMillis = state.cooldownUntil.get(),
            lastRequestAtMillis = state.lastRequestAt.get(),
            updatedAtMillis = state.updatedAt.get()
        )
    }

    fun reset(type: AdType) {
        val state = states.getValue(type)
        state.loading.set(false)
        state.ready.set(false)
        state.showing.set(false)
        state.cooldownUntil.set(0L)
        state.lastRequestAt.set(0L)
        state.updatedAt.set(SystemClock.elapsedRealtime())
    }

    private class AdState {
        val loading = AtomicBoolean(false)
        val ready = AtomicBoolean(false)
        val showing = AtomicBoolean(false)
        val cooldownUntil = AtomicLong(0L)
        val lastRequestAt = AtomicLong(0L)
        val updatedAt = AtomicLong(0L)
    }
}
