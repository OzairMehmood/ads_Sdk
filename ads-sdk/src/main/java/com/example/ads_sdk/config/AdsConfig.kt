package com.example.ads_sdk.config

import com.example.ads_sdk.core.SdkFeature
import com.example.ads_sdk.utils.LogLevel

/**
 * Runtime configuration for every ad format. Values can come from defaults,
 * Firebase Remote Config, your backend, or any local feature flag system.
 */
data class AdsConfig(
    val adsEnabled: Boolean = true,
    val bannerEnabled: Boolean = true,
    val interstitialEnabled: Boolean = true,
    val rewardedEnabled: Boolean = true,
    val nativeEnabled: Boolean = true,
    val appOpenEnabled: Boolean = true,
    val adUnitIds: AdUnitIds = AdUnitIds(),
    val interstitialInterval: Int = 3,
    val debugLogs: Boolean = false,
    val logLevel: LogLevel = LogLevel.ERROR,
    val retryEnabled: Boolean = true,
    val retryInitialDelayMillis: Long = 10_000L,
    val retryMaxDelayMillis: Long = 30_000L,
    val appOpenCooldownMillis: Long = 30_000L,
    val appOpenForegroundWindowMillis: Long = 1_500L,
    val loadDebounceMillis: Long = 1_000L,
    val perScreenCacheTtlMillis: Long = 30 * 60 * 1000L,
    val coldStartDelayMillis: Long = 3_000L,
    val autoLoadAdsOnInit: Boolean = true,
    val featureOverrides: Map<SdkFeature, Boolean> = emptyMap(),
    val testDeviceIds: List<String> = emptyList()
) {
    val canShowBanner: Boolean get() = adsEnabled && bannerEnabled
    val canShowInterstitial: Boolean get() = adsEnabled && interstitialEnabled
    val canShowRewarded: Boolean get() = adsEnabled && rewardedEnabled
    val canShowNative: Boolean get() = adsEnabled && nativeEnabled
    val canShowAppOpen: Boolean get() = adsEnabled && appOpenEnabled
}

/** Ad unit ids used by the SDK. Defaults are Google's official test ids. */
data class AdUnitIds(
    val banner: String = TestAdUnitIds.BANNER,
    val interstitial: String = TestAdUnitIds.INTERSTITIAL,
    val rewarded: String = TestAdUnitIds.REWARDED,
    val native: String = TestAdUnitIds.NATIVE,
    val appOpen: String = TestAdUnitIds.APP_OPEN
)

/** Google's public test ad ids. Replace these with production ids before release. */
object TestAdUnitIds {
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}
