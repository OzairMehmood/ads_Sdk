package com.example.ads_sdk.utils

/** Crash-immunity wrapper for SDK callbacks and host-provided listeners. */
internal object SafeExecutor {
    fun run(label: String, block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            AdsLogger.e("SafeExecutor swallowed exception in $label.", throwable)
        }
    }
}
