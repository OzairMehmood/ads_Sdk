package com.example.ads_sdk.domain.repository

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.ViewGroup
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.domain.model.AdEventListener
import com.example.ads_sdk.domain.model.RewardCallback

/** Domain boundary used by use cases; implementations can be swapped in tests. */
internal interface AdsRepository {
    fun initialize(application: Application, config: AdsConfig)
    fun setAdEventListener(listener: AdEventListener?)
    fun loadAll(context: Context)
    fun onTrimMemory(level: Int)

    fun loadBanner(container: ViewGroup)
    fun destroyBanner(container: ViewGroup)

    fun loadInterstitial(context: Context)
    fun showInterstitial(activity: Activity)

    fun loadRewarded(context: Context)
    fun showRewarded(activity: Activity, callback: RewardCallback)

    fun loadNative(container: ViewGroup)
    fun destroyNative(container: ViewGroup)

    fun loadAppOpen(context: Context)
    fun showAppOpenIfAvailable(activity: Activity)
}
