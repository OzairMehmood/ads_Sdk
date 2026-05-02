package com.example.ads_sdk.core

import com.example.ads_sdk.config.RemoteConfigManager

/** SDK identity and version-gated feature switches for commercial releases. */
object SdkInfo {
    const val VERSION_NAME = "1.0.0"
    const val VERSION_CODE = 10000

    fun isFeatureEnabled(feature: SdkFeature): Boolean {
        val override = RemoteConfigManager.getConfig().featureOverrides[feature]
        return override ?: feature.defaultEnabled
    }
}

enum class SdkFeature(val defaultEnabled: Boolean) {
    COLD_START_PROTECTION(true),
    MEMORY_PRESSURE_CLEANUP(true),
    IMPRESSION_REVENUE_TRACKING(true),
    RECYCLER_NATIVE_ADS(true),
    APP_OPEN_COOLDOWN(true)
}
