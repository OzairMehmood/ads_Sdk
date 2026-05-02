package com.example.ads_sdk.data.provider

import android.app.Application
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.domain.provider.AdProvider

/** Placeholder provider for future Meta Audience Network mediation support. */
class MetaAdProvider : AdProvider {
    override val name: String = "meta"
    override val priority: Int = 1
    override val isLoadSupported: Boolean = false

    override fun initialize(application: Application, config: AdsConfig, onInitialized: () -> Unit) {
        onInitialized()
    }
}
