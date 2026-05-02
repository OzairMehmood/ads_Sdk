package com.example.myadssdk

import android.app.Application
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.core.AdsManager

class MyAdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsManager.init(
            application = this,
            config = AdsConfig(
                debugLogs = true,
                interstitialInterval = 3
            )
        )
    }
}
