package com.example.ads_sdk.data.provider

import android.app.Application
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.domain.provider.AdProvider

/** Placeholder provider for future Unity Ads mediation support. */
class UnityAdProvider : AdProvider {
    override val name: String = "unity"
    override val priority: Int = 2
    override val isLoadSupported: Boolean = false

    override fun initialize(application: Application, config: AdsConfig, onInitialized: () -> Unit) {
        onInitialized()
    }
}
