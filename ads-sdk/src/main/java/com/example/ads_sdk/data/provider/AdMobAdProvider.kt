package com.example.ads_sdk.data.provider

import android.app.Application
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.domain.provider.AdProvider
import com.example.ads_sdk.utils.AdsLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/** Default mediation provider backed by Google Mobile Ads. */
class AdMobAdProvider : AdProvider {
    override val name: String = "admob"
    override val priority: Int = 0
    override val isLoadSupported: Boolean = true

    override fun initialize(application: Application, config: AdsConfig, onInitialized: () -> Unit) {
        if (config.testDeviceIds.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(config.testDeviceIds)
                    .build()
            )
        }
        MobileAds.initialize(application) {
            AdsLogger.d("AdMob initialized.")
            onInitialized()
        }
    }
}
