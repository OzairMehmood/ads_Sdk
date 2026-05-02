package com.example.ads_sdk.domain.provider

import android.app.Application
import com.example.ads_sdk.config.AdsConfig

/** Mediation provider boundary. Add Meta or Unity implementations beside AdMob. */
interface AdProvider {
    val name: String
    val priority: Int
    val isLoadSupported: Boolean
    fun initialize(application: Application, config: AdsConfig, onInitialized: () -> Unit)
}
