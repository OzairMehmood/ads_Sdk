package com.example.ads_sdk.core

import com.example.ads_sdk.utils.AdsLogger
import java.util.concurrent.atomic.AtomicBoolean

/** Global concurrency gate for idempotent SDK initialization and public API calls. */
internal object AdsConcurrencyGuard {
    private val initStarted = AtomicBoolean(false)
    private val initCompleted = AtomicBoolean(false)
    private val lock = Any()

    fun runApi(block: () -> Unit) {
        synchronized(lock) { block() }
    }

    fun initializeOnce(block: () -> Unit) {
        synchronized(lock) {
            if (!initStarted.compareAndSet(false, true)) {
                AdsLogger.d("AdsManager.init ignored because SDK is already initialized or initializing.")
                return
            }
            block()
            initCompleted.set(true)
        }
    }

    fun isInitialized(): Boolean = initCompleted.get()
}
