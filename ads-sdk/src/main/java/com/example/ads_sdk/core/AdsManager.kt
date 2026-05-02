package com.example.ads_sdk.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.config.RemoteConfigManager
import com.example.ads_sdk.data.repository.AdsRepositoryImpl
import com.example.ads_sdk.domain.model.AdEventListener
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.model.DeviceTier
import com.example.ads_sdk.domain.model.RewardCallback
import com.example.ads_sdk.domain.model.RewardInfo
import com.example.ads_sdk.domain.model.RevenueBucket
import com.example.ads_sdk.domain.model.RevenueSummary
import com.example.ads_sdk.domain.model.SdkMetricsSnapshot
import com.example.ads_sdk.domain.repository.AdsRepository
import com.example.ads_sdk.domain.state.AdStateSnapshot
import com.example.ads_sdk.domain.usecase.InitializeAdsUseCase
import com.example.ads_sdk.domain.usecase.LoadAllAdsUseCase
import com.example.ads_sdk.domain.usecase.LoadAppOpenUseCase
import com.example.ads_sdk.domain.usecase.LoadBannerUseCase
import com.example.ads_sdk.domain.usecase.LoadInterstitialUseCase
import com.example.ads_sdk.domain.usecase.LoadNativeUseCase
import com.example.ads_sdk.domain.usecase.LoadRewardedUseCase
import com.example.ads_sdk.domain.usecase.ShowAppOpenUseCase
import com.example.ads_sdk.domain.usecase.ShowInterstitialUseCase
import com.example.ads_sdk.domain.usecase.ShowRewardedUseCase
import com.example.ads_sdk.presentation.nativead.NativeAdRecyclerAdapter

/**
 * Public facade for host apps. Keep app code on this singleton and avoid
 * depending on repository or Google Mobile Ads implementation details.
 */
object AdsManager {
    private val repository: AdsRepository = AdsRepositoryImpl()

    private val initializeAds = InitializeAdsUseCase(repository)
    private val loadAllAds = LoadAllAdsUseCase(repository)
    private val loadBannerAd = LoadBannerUseCase(repository)
    private val loadInterstitialAd = LoadInterstitialUseCase(repository)
    private val showInterstitialAd = ShowInterstitialUseCase(repository)
    private val loadRewardedAd = LoadRewardedUseCase(repository)
    private val showRewardedAd = ShowRewardedUseCase(repository)
    private val loadNativeAd = LoadNativeUseCase(repository)
    private val loadAppOpenAd = LoadAppOpenUseCase(repository)
    private val showAppOpenAd = ShowAppOpenUseCase(repository)

    fun init(application: Application, config: AdsConfig = AdsConfig()) {
        AdsConcurrencyGuard.initializeOnce { initializeAds(application, config) }
    }

    fun updateRemoteConfig(config: AdsConfig) {
        AdsConcurrencyGuard.runApi { RemoteConfigManager.updateConfig(config) }
    }

    fun setDebugLogsEnabled(enabled: Boolean) {
        AdsConcurrencyGuard.runApi { RemoteConfigManager.updateConfig { it.copy(debugLogs = enabled) } }
    }

    fun setAdEventListener(listener: AdEventListener?) {
        AdsConcurrencyGuard.runApi { repository.setAdEventListener(listener) }
    }

    fun loadAds(context: Context) {
        AdsConcurrencyGuard.runApi { loadAllAds(context) }
    }

    fun onTrimMemory(level: Int) {
        AdsConcurrencyGuard.runApi { repository.onTrimMemory(level) }
    }

    fun loadBanner(container: ViewGroup) {
        AdsConcurrencyGuard.runApi { loadBannerAd(container) }
    }

    fun destroyBanner(container: ViewGroup) {
        AdsConcurrencyGuard.runApi { repository.destroyBanner(container) }
    }

    fun loadInterstitial(context: Context) {
        AdsConcurrencyGuard.runApi { loadInterstitialAd(context) }
    }

    fun showInterstitial(activity: Activity) {
        AdsConcurrencyGuard.runApi { showInterstitialAd(activity) }
    }

    fun loadRewarded(context: Context) {
        AdsConcurrencyGuard.runApi { loadRewardedAd(context) }
    }

    fun showRewarded(activity: Activity, onReward: (RewardInfo) -> Unit) {
        AdsConcurrencyGuard.runApi {
            showRewardedAd(activity, RewardCallback { reward -> onReward(reward) })
        }
    }

    fun loadNative(container: ViewGroup) {
        AdsConcurrencyGuard.runApi { loadNativeAd(container) }
    }

    fun destroyNative(container: ViewGroup) {
        AdsConcurrencyGuard.runApi { repository.destroyNative(container) }
    }

    fun loadAppOpen(context: Context) {
        AdsConcurrencyGuard.runApi { loadAppOpenAd(context) }
    }

    fun showAppOpen(activity: Activity) {
        AdsConcurrencyGuard.runApi { showAppOpenAd(activity) }
    }

    fun getAdState(type: AdType): AdStateSnapshot {
        return AdStateManager.snapshot(type)
    }

    fun withNativeAds(
        adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>,
        adInterval: Int = 6
    ): NativeAdRecyclerAdapter {
        return NativeAdRecyclerAdapter(adapter, adInterval)
    }

    fun setCurrentScreen(screenName: String) {
        RevenueAggregator.setCurrentScreen(screenName)
    }

    fun getSessionRevenue(): RevenueSummary = RevenueAggregator.getSessionRevenue()

    fun getDailyRevenue(): RevenueSummary = RevenueAggregator.getDailyRevenue()

    fun getScreenRevenue(screenName: String): RevenueSummary = RevenueAggregator.getScreenRevenue(screenName)

    fun getRevenueBuckets(): List<RevenueBucket> = RevenueAggregator.getRevenueBuckets()

    fun getDeviceTier(): DeviceTier = DeviceSignalManager.getDeviceTier()

    fun recordUserEngagement(delta: Int = 1) {
        DeviceSignalManager.recordEngagement(delta)
    }

    fun getMetricsSnapshot(): SdkMetricsSnapshot = SdkMetrics.snapshot()

    fun getDebugDashboardLogs(): String = SdkMetrics.dashboard()
}
