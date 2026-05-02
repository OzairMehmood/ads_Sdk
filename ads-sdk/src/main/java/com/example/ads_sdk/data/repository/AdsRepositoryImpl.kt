package com.example.ads_sdk.data.repository

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ads_sdk.R
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.config.RemoteConfigManager
import com.example.ads_sdk.core.ActivityAdLifecycleGuard
import com.example.ads_sdk.core.DeviceSignalManager
import com.example.ads_sdk.data.mapper.toInfo
import com.example.ads_sdk.data.mapper.toRevenueInfo
import com.example.ads_sdk.data.provider.AdMobAdProvider
import com.example.ads_sdk.data.provider.MetaAdProvider
import com.example.ads_sdk.data.provider.UnityAdProvider
import com.example.ads_sdk.core.AdStateManager
import com.example.ads_sdk.core.ColdStartGuard
import com.example.ads_sdk.core.ProviderWaterfall
import com.example.ads_sdk.core.RevenueAggregator
import com.example.ads_sdk.core.SdkMetrics
import com.example.ads_sdk.core.SessionManager
import com.example.ads_sdk.domain.model.AdFailureReason
import com.example.ads_sdk.domain.model.AdEventListener
import com.example.ads_sdk.domain.model.AdLoadErrorInfo
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.model.RewardCallback
import com.example.ads_sdk.domain.provider.AdProvider
import com.example.ads_sdk.domain.repository.AdsRepository
import com.example.ads_sdk.presentation.nativead.NativeAdBinder
import com.example.ads_sdk.utils.AdEventTracker
import com.example.ads_sdk.utils.AdsLogger
import com.example.ads_sdk.utils.MainThread
import com.example.ads_sdk.utils.NetworkMonitor
import com.example.ads_sdk.utils.RetryController
import com.example.ads_sdk.utils.SafeExecutor
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Mobile Ads backed repository. This is the only class that talks to the
 * AdMob SDK directly, keeping the public AdsManager API small and stable.
 */
internal class AdsRepositoryImpl(
    private val adProvider: AdProvider = AdMobAdProvider()
) : AdsRepository {
    private var application: Application? = null
    private var currentActivity = WeakReference<Activity?>(null)

    private val eventTracker = AdEventTracker()
    private val retryController = RetryController()
    private val providerWaterfall = ProviderWaterfall(listOf(adProvider, MetaAdProvider(), UnityAdProvider()))

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAtMillis = 0L
    private var cachedNativeAd: NativeAd? = null
    private var pendingNativeContainer = WeakReference<ViewGroup?>(null)

    private val isShowingFullScreenAd = AtomicBoolean(false)
    private val bannerViews = WeakHashMap<ViewGroup, AdView>()
    private val nativeAds = WeakHashMap<ViewGroup, NativeAd>()

    private var interstitialOpportunityCount = 0
    private var lifecycleRegistered = false
    private var trimCallbacksRegistered = false
    private var lastForegroundAtMillis = 0L
    private var lastAppOpenShownAtMillis = 0L

    override fun initialize(application: Application, config: AdsConfig) {
        MainThread.run {
            this.application = application
            RemoteConfigManager.init(config)
            DeviceSignalManager.initialize(application)
            SessionManager.startNewSession()
            RevenueAggregator.resetSession()
            ColdStartGuard.markInitialized()
            adProvider.initialize(application, config) {
                AdsLogger.d("${adProvider.name} provider initialized.")
                if (RemoteConfigManager.getConfig().autoLoadAdsOnInit) {
                    ColdStartGuard.runWhenReady { loadAll(application) }
                }
            }
            registerLifecycleCallbacks(application)
            registerTrimCallbacks(application)
        }
    }

    override fun setAdEventListener(listener: AdEventListener?) {
        eventTracker.setListener(listener)
    }

    override fun loadAll(context: Context) {
        ColdStartGuard.runWhenReady {
            val appContext = context.applicationContext
            loadInterstitial(appContext)
            loadRewarded(appContext)
            loadAppOpen(appContext)
            preloadNative(appContext)
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        MainThread.run {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                AdsLogger.d("Trim memory level $level. Clearing cached ads.")
                clearCachedAds(clearViews = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            }
        }
    }

    override fun loadBanner(container: ViewGroup) {
        MainThread.run {
            if (!ColdStartGuard.isReady()) {
                ColdStartGuard.runWhenReady { loadBanner(container) }
                return@run
            }
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowBanner) {
                destroyBanner(container)
                return@run
            }

            if (!NetworkMonitor.isOnline(container.context)) {
                hideAdContainer(container)
                SdkMetrics.markLoadFailure(AdType.BANNER)
                markOffline(AdType.BANNER)
                retryController.schedule(AdType.BANNER) { loadBanner(container) }
                return@run
            }

            container.doOnLayout {
                val existing = bannerViews[container]
                if (existing != null && existing.adUnitId == config.adUnitIds.banner) {
                    AdsLogger.d("Banner already attached; skipping duplicate load.")
                    return@doOnLayout
                }
                if (!AdStateManager.tryBeginLoad(AdType.BANNER)) return@doOnLayout
                SdkMetrics.markLoadStart(AdType.BANNER)

                destroyBanner(container)
                val adView = AdView(container.context).apply {
                    adUnitId = config.adUnitIds.banner
                    setAdSize(resolveBannerSize(container))
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            SdkMetrics.markLoadSuccess(AdType.BANNER)
                            providerWaterfall.reportLoadSuccess(AdType.BANNER)
                            container.visibility = View.VISIBLE
                            AdStateManager.markReady(AdType.BANNER)
                            AdStateManager.markConsumed(AdType.BANNER)
                            retryController.reset(AdType.BANNER)
                            eventTracker.loaded(AdType.BANNER)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            SdkMetrics.markLoadFailure(AdType.BANNER)
                            val info = error.toInfo()
                            providerWaterfall.reportLoadFailure(AdType.BANNER)
                            hideAdContainer(container)
                            AdStateManager.markFailed(AdType.BANNER, failureCooldown(info))
                            eventTracker.failed(AdType.BANNER, info)
                            retryController.schedule(AdType.BANNER) { loadBanner(container) }
                        }

                        override fun onAdClicked() {
                            eventTracker.clicked(AdType.BANNER)
                        }
                    }
                }

                bannerViews[container] = adView
                attachRevenueTracking(
                    type = AdType.BANNER,
                    adUnitId = config.adUnitIds.banner,
                    responseInfoProvider = { adView.responseInfo?.mediationAdapterClassName },
                    setListener = { listener -> adView.setOnPaidEventListener(listener) }
                )
                container.removeAllViews()
                container.addView(
                    adView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                attachDestroyOnDetach(container)
                adView.loadAd(buildAdRequest())
            }
        }
    }

    override fun destroyBanner(container: ViewGroup) {
        MainThread.run {
            bannerViews.remove(container)?.destroy()
            container.removeAllViews()
        }
    }

    override fun loadInterstitial(context: Context) {
        MainThread.run {
            if (!ColdStartGuard.isReady()) {
                ColdStartGuard.runWhenReady { loadInterstitial(context.applicationContext) }
                return@run
            }
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowInterstitial || interstitialAd != null) return@run
            if (!NetworkMonitor.isOnline(context)) {
                SdkMetrics.markLoadFailure(AdType.INTERSTITIAL)
                markOffline(AdType.INTERSTITIAL)
                retryController.schedule(AdType.INTERSTITIAL) {
                    loadInterstitial(context.applicationContext)
                }
                return@run
            }
            if (!AdStateManager.tryBeginLoad(AdType.INTERSTITIAL)) return@run
            SdkMetrics.markLoadStart(AdType.INTERSTITIAL)

            InterstitialAd.load(
                context.applicationContext,
                config.adUnitIds.interstitial,
                buildAdRequest(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        SdkMetrics.markLoadSuccess(AdType.INTERSTITIAL)
                        providerWaterfall.reportLoadSuccess(AdType.INTERSTITIAL)
                        AdStateManager.markReady(AdType.INTERSTITIAL)
                        retryController.reset(AdType.INTERSTITIAL)
                        attachRevenueTracking(
                            type = AdType.INTERSTITIAL,
                            adUnitId = config.adUnitIds.interstitial,
                            responseInfoProvider = { ad.responseInfo?.mediationAdapterClassName },
                            setListener = { listener -> ad.onPaidEventListener = listener }
                        )
                        interstitialAd = ad
                        eventTracker.loaded(AdType.INTERSTITIAL)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        SdkMetrics.markLoadFailure(AdType.INTERSTITIAL)
                        val info = error.toInfo()
                        providerWaterfall.reportLoadFailure(AdType.INTERSTITIAL)
                        AdStateManager.markFailed(AdType.INTERSTITIAL, failureCooldown(info))
                        interstitialAd = null
                        eventTracker.failed(AdType.INTERSTITIAL, info)
                        retryController.schedule(AdType.INTERSTITIAL) {
                            loadInterstitial(context.applicationContext)
                        }
                    }
                }
            )
        }
    }

    override fun showInterstitial(activity: Activity) {
        MainThread.run {
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowInterstitial) return@run
            if (!activity.isSafeForAdShow()) {
                eventTracker.failedToShow(AdType.INTERSTITIAL, "Activity is finishing or destroyed.")
                return@run
            }

            interstitialOpportunityCount++
            val interval = DeviceSignalManager.adjustedInterstitialInterval(config.interstitialInterval.coerceAtLeast(1))
            if (interstitialOpportunityCount < interval) {
                AdsLogger.d("Interstitial skipped by frequency cap.")
                loadInterstitial(activity.applicationContext)
                return@run
            }

            val ad = interstitialAd
            if (ad == null) {
                eventTracker.failedToShow(AdType.INTERSTITIAL, "Interstitial ad is not ready yet.")
                loadInterstitial(activity.applicationContext)
                return@run
            }
            if (!AdStateManager.tryBeginShow(AdType.INTERSTITIAL)) {
                eventTracker.failedToShow(AdType.INTERSTITIAL, "Interstitial is already showing or cooling down.")
                return@run
            }

            interstitialAd = null
            interstitialOpportunityCount = 0
            isShowingFullScreenAd.set(true)
            SdkMetrics.markShowStart(AdType.INTERSTITIAL)
            val lifecycleGuard = ActivityAdLifecycleGuard(activity)
            val appContext = activity.applicationContext
            ad.fullScreenContentCallback = fullScreenCallback(
                type = AdType.INTERSTITIAL,
                lifecycleGuard = lifecycleGuard,
                onDismissOrFail = {
                    isShowingFullScreenAd.set(false)
                    AdStateManager.finishShow(AdType.INTERSTITIAL)
                    loadInterstitial(appContext)
                }
            )
            ad.show(activity)
        }
    }

    override fun loadRewarded(context: Context) {
        MainThread.run {
            if (!ColdStartGuard.isReady()) {
                ColdStartGuard.runWhenReady { loadRewarded(context.applicationContext) }
                return@run
            }
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowRewarded || rewardedAd != null) return@run
            if (!NetworkMonitor.isOnline(context)) {
                SdkMetrics.markLoadFailure(AdType.REWARDED)
                markOffline(AdType.REWARDED)
                retryController.schedule(AdType.REWARDED) {
                    loadRewarded(context.applicationContext)
                }
                return@run
            }
            if (!AdStateManager.tryBeginLoad(AdType.REWARDED)) return@run
            SdkMetrics.markLoadStart(AdType.REWARDED)

            RewardedAd.load(
                context.applicationContext,
                config.adUnitIds.rewarded,
                buildAdRequest(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        SdkMetrics.markLoadSuccess(AdType.REWARDED)
                        providerWaterfall.reportLoadSuccess(AdType.REWARDED)
                        AdStateManager.markReady(AdType.REWARDED)
                        retryController.reset(AdType.REWARDED)
                        attachRevenueTracking(
                            type = AdType.REWARDED,
                            adUnitId = config.adUnitIds.rewarded,
                            responseInfoProvider = { ad.responseInfo?.mediationAdapterClassName },
                            setListener = { listener -> ad.onPaidEventListener = listener }
                        )
                        rewardedAd = ad
                        eventTracker.loaded(AdType.REWARDED)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        SdkMetrics.markLoadFailure(AdType.REWARDED)
                        val info = error.toInfo()
                        providerWaterfall.reportLoadFailure(AdType.REWARDED)
                        AdStateManager.markFailed(AdType.REWARDED, failureCooldown(info))
                        rewardedAd = null
                        eventTracker.failed(AdType.REWARDED, info)
                        retryController.schedule(AdType.REWARDED) {
                            loadRewarded(context.applicationContext)
                        }
                    }
                }
            )
        }
    }

    override fun showRewarded(activity: Activity, callback: RewardCallback) {
        MainThread.run {
            if (!RemoteConfigManager.getConfig().canShowRewarded) return@run
            if (!activity.isSafeForAdShow()) {
                eventTracker.failedToShow(AdType.REWARDED, "Activity is finishing or destroyed.")
                return@run
            }

            val ad = rewardedAd
            if (ad == null) {
                eventTracker.failedToShow(AdType.REWARDED, "Rewarded ad is not ready yet.")
                loadRewarded(activity.applicationContext)
                return@run
            }
            if (!AdStateManager.tryBeginShow(AdType.REWARDED)) {
                eventTracker.failedToShow(AdType.REWARDED, "Rewarded ad is already showing or cooling down.")
                return@run
            }

            rewardedAd = null
            isShowingFullScreenAd.set(true)
            SdkMetrics.markShowStart(AdType.REWARDED)
            val lifecycleGuard = ActivityAdLifecycleGuard(activity)
            val appContext = activity.applicationContext
            ad.fullScreenContentCallback = fullScreenCallback(
                type = AdType.REWARDED,
                lifecycleGuard = lifecycleGuard,
                onDismissOrFail = {
                    isShowingFullScreenAd.set(false)
                    AdStateManager.finishShow(AdType.REWARDED)
                    loadRewarded(appContext)
                }
            )
            ad.show(activity) { reward ->
                if (lifecycleGuard.getActivityOrNull() == null) return@show
                SafeExecutor.run("reward callback") {
                    val rewardInfo = reward.toInfo()
                    callback.onRewardEarned(rewardInfo)
                    eventTracker.rewardEarned(rewardInfo)
                }
            }
        }
    }

    override fun loadNative(container: ViewGroup) {
        MainThread.run {
            if (!ColdStartGuard.isReady()) {
                ColdStartGuard.runWhenReady { loadNative(container) }
                return@run
            }
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowNative) {
                destroyNative(container)
                return@run
            }
            if (!NetworkMonitor.isOnline(container.context)) {
                hideAdContainer(container)
                SdkMetrics.markLoadFailure(AdType.NATIVE)
                markOffline(AdType.NATIVE)
                retryController.schedule(AdType.NATIVE) { loadNative(container) }
                return@run
            }

            cachedNativeAd?.let { cached ->
                cachedNativeAd = null
                AdStateManager.markConsumed(AdType.NATIVE)
                bindNativeAd(container, cached)
                preloadNative(container.context.applicationContext)
                return@run
            }

            loadNativeAndBind(container)
        }
    }

    override fun destroyNative(container: ViewGroup) {
        MainThread.run {
            nativeAds.remove(container)?.destroy()
            container.removeAllViews()
        }
    }

    override fun loadAppOpen(context: Context) {
        MainThread.run {
            if (!ColdStartGuard.isReady()) {
                ColdStartGuard.runWhenReady { loadAppOpen(context.applicationContext) }
                return@run
            }
            val config = RemoteConfigManager.getConfig()
            if (!config.canShowAppOpen || isAppOpenAvailable()) return@run
            if (!NetworkMonitor.isOnline(context)) {
                SdkMetrics.markLoadFailure(AdType.APP_OPEN)
                markOffline(AdType.APP_OPEN)
                retryController.schedule(AdType.APP_OPEN) {
                    loadAppOpen(context.applicationContext)
                }
                return@run
            }
            if (!AdStateManager.tryBeginLoad(AdType.APP_OPEN)) return@run
            SdkMetrics.markLoadStart(AdType.APP_OPEN)

            AppOpenAd.load(
                context.applicationContext,
                config.adUnitIds.appOpen,
                buildAdRequest(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        SdkMetrics.markLoadSuccess(AdType.APP_OPEN)
                        providerWaterfall.reportLoadSuccess(AdType.APP_OPEN)
                        AdStateManager.markReady(AdType.APP_OPEN)
                        retryController.reset(AdType.APP_OPEN)
                        attachRevenueTracking(
                            type = AdType.APP_OPEN,
                            adUnitId = config.adUnitIds.appOpen,
                            responseInfoProvider = { ad.responseInfo?.mediationAdapterClassName },
                            setListener = { listener -> ad.onPaidEventListener = listener }
                        )
                        appOpenAd = ad
                        appOpenLoadedAtMillis = SystemClock.elapsedRealtime()
                        eventTracker.loaded(AdType.APP_OPEN)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        SdkMetrics.markLoadFailure(AdType.APP_OPEN)
                        val info = error.toInfo()
                        providerWaterfall.reportLoadFailure(AdType.APP_OPEN)
                        AdStateManager.markFailed(AdType.APP_OPEN, failureCooldown(info))
                        appOpenAd = null
                        eventTracker.failed(AdType.APP_OPEN, info)
                        retryController.schedule(AdType.APP_OPEN) {
                            loadAppOpen(context.applicationContext)
                        }
                    }
                }
            )
        }
    }

    override fun showAppOpenIfAvailable(activity: Activity) {
        MainThread.run {
            val config = RemoteConfigManager.getConfig()
            val now = SystemClock.elapsedRealtime()
            val foregroundWindow = now - lastForegroundAtMillis <= config.appOpenForegroundWindowMillis
            val cooldownPassed = now - lastAppOpenShownAtMillis >= config.appOpenCooldownMillis

            if (!config.canShowAppOpen || !foregroundWindow || !cooldownPassed) return@run
            if (!activity.isSafeForAdShow()) return@run
            if (isShowingFullScreenAd.get()) return@run

            val ad = appOpenAd
            if (ad == null || !isAppOpenAvailable()) {
                appOpenAd = null
                loadAppOpen(activity.applicationContext)
                return@run
            }
            if (!AdStateManager.tryBeginShow(AdType.APP_OPEN)) return@run

            appOpenAd = null
            lastAppOpenShownAtMillis = now
            isShowingFullScreenAd.set(true)
            SdkMetrics.markShowStart(AdType.APP_OPEN)
            val lifecycleGuard = ActivityAdLifecycleGuard(activity)
            val appContext = activity.applicationContext
            ad.fullScreenContentCallback = fullScreenCallback(
                type = AdType.APP_OPEN,
                lifecycleGuard = lifecycleGuard,
                onDismissOrFail = {
                    isShowingFullScreenAd.set(false)
                    AdStateManager.finishShow(AdType.APP_OPEN, config.appOpenCooldownMillis)
                    loadAppOpen(appContext)
                }
            )
            ad.show(activity)
        }
    }

    private fun loadNativeAndBind(container: ViewGroup) {
        val context = container.context
        val config = RemoteConfigManager.getConfig()
        pendingNativeContainer = WeakReference(container)
        if (!AdStateManager.tryBeginLoad(AdType.NATIVE)) return
        SdkMetrics.markLoadStart(AdType.NATIVE)

        com.google.android.gms.ads.AdLoader.Builder(context, config.adUnitIds.native)
            .forNativeAd { ad ->
                SdkMetrics.markLoadSuccess(AdType.NATIVE)
                providerWaterfall.reportLoadSuccess(AdType.NATIVE)
                AdStateManager.markReady(AdType.NATIVE)
                retryController.reset(AdType.NATIVE)
                attachNativeRevenueTracking(ad, config.adUnitIds.native)
                if (!container.isAttachedToWindow) {
                    ad.destroy()
                    pendingNativeContainer = WeakReference(null)
                    preloadNative(context.applicationContext)
                    return@forNativeAd
                }
                pendingNativeContainer = WeakReference(null)
                AdStateManager.markConsumed(AdType.NATIVE)
                bindNativeAd(container, ad)
                eventTracker.loaded(AdType.NATIVE)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    SdkMetrics.markLoadFailure(AdType.NATIVE)
                    val info = error.toInfo()
                    providerWaterfall.reportLoadFailure(AdType.NATIVE)
                    hideAdContainer(container)
                    AdStateManager.markFailed(AdType.NATIVE, failureCooldown(info))
                    eventTracker.failed(AdType.NATIVE, info)
                    val weakContainer = WeakReference(container)
                    retryController.schedule(AdType.NATIVE) {
                        weakContainer.get()?.takeIf { it.isAttachedToWindow }?.let(::loadNative)
                    }
                }

                override fun onAdClicked() {
                    eventTracker.clicked(AdType.NATIVE)
                }
            })
            .withNativeAdOptions(nativeAdOptions())
            .build()
            .loadAd(buildAdRequest())
    }

    private fun preloadNative(context: Context) {
        val config = RemoteConfigManager.getConfig()
        if (!config.canShowNative || cachedNativeAd != null) return
        if (!NetworkMonitor.isOnline(context)) {
            SdkMetrics.markLoadFailure(AdType.NATIVE)
            markOffline(AdType.NATIVE)
            retryController.schedule(AdType.NATIVE) { preloadNative(context.applicationContext) }
            return
        }
        if (!AdStateManager.tryBeginLoad(AdType.NATIVE)) return
        SdkMetrics.markLoadStart(AdType.NATIVE)

        com.google.android.gms.ads.AdLoader.Builder(context, config.adUnitIds.native)
            .forNativeAd { ad ->
                SdkMetrics.markLoadSuccess(AdType.NATIVE)
                providerWaterfall.reportLoadSuccess(AdType.NATIVE)
                AdStateManager.markReady(AdType.NATIVE)
                retryController.reset(AdType.NATIVE)
                attachNativeRevenueTracking(ad, config.adUnitIds.native)
                val pendingContainer = pendingNativeContainer.get()
                if (pendingContainer?.isAttachedToWindow == true) {
                    pendingNativeContainer = WeakReference(null)
                    AdStateManager.markConsumed(AdType.NATIVE)
                    bindNativeAd(pendingContainer, ad)
                } else {
                    cachedNativeAd?.destroy()
                    cachedNativeAd = ad
                }
                eventTracker.loaded(AdType.NATIVE)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    SdkMetrics.markLoadFailure(AdType.NATIVE)
                    val info = error.toInfo()
                    providerWaterfall.reportLoadFailure(AdType.NATIVE)
                    AdStateManager.markFailed(AdType.NATIVE, failureCooldown(info))
                    eventTracker.failed(AdType.NATIVE, info)
                    retryController.schedule(AdType.NATIVE) { preloadNative(context.applicationContext) }
                }
            })
            .withNativeAdOptions(nativeAdOptions())
            .build()
            .loadAd(buildAdRequest())
    }

    private fun bindNativeAd(container: ViewGroup, ad: NativeAd) {
        destroyNative(container)
        val view = LayoutInflater.from(container.context)
            .inflate(R.layout.native_ad_layout, container, false) as NativeAdView
        NativeAdBinder.bind(ad, view)
        nativeAds[container] = ad
        container.addView(view)
        attachDestroyOnDetach(container)
    }

    private fun attachDestroyOnDetach(container: ViewGroup) {
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                destroyBanner(container)
                destroyNative(container)
                container.removeOnAttachStateChangeListener(this)
            }
        })
    }

    private fun nativeAdOptions(): NativeAdOptions {
        return NativeAdOptions.Builder()
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()
    }

    private fun fullScreenCallback(
        type: AdType,
        lifecycleGuard: ActivityAdLifecycleGuard,
        onDismissOrFail: () -> Unit
    ): FullScreenContentCallback {
        val shownOnce = AtomicBoolean(false)
        val completedOnce = AtomicBoolean(false)
        val clickedOnce = AtomicBoolean(false)
        return object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            if (shownOnce.compareAndSet(false, true) && lifecycleGuard.getActivityOrNull() != null) {
                SdkMetrics.markShowSuccess(type)
                DeviceSignalManager.recordAdShown(type)
                eventTracker.shown(type)
            }
        }

        override fun onAdDismissedFullScreenContent() {
            finishOnce("$type dismiss callback", lifecycleGuard, completedOnce, type, onDismissOrFail) {
                eventTracker.dismissed(type)
            }
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            finishOnce("$type failed-show callback", lifecycleGuard, completedOnce, type, onDismissOrFail) {
                eventTracker.failedToShow(type, error.message)
            }
        }

        override fun onAdClicked() {
            if (clickedOnce.compareAndSet(false, true) && lifecycleGuard.getActivityOrNull() != null) {
                eventTracker.clicked(type)
            }
        }
    }
    }

    private fun finishOnce(
        label: String,
        lifecycleGuard: ActivityAdLifecycleGuard,
        completedOnce: AtomicBoolean,
        type: AdType,
        onDismissOrFail: () -> Unit,
        notify: () -> Unit
    ) {
        if (!completedOnce.compareAndSet(false, true)) return
        if (lifecycleGuard.getActivityOrNull() != null) notify()
        SafeExecutor.run(label) { onDismissOrFail() }
        lifecycleGuard.release()
    }

    private fun buildAdRequest(): AdRequest = AdRequest.Builder().build()

    private fun markOffline(type: AdType) {
        val info = AdLoadErrorInfo(
            code = -1,
            message = "No validated internet connection. Ad request postponed.",
            domain = "sdk.network",
            reason = AdFailureReason.NO_INTERNET
        )
        AdStateManager.markFailed(type, failureCooldown(info))
        eventTracker.failed(type, info)
    }

    private fun failureCooldown(error: AdLoadErrorInfo): Long {
        return when (error.reason) {
            AdFailureReason.NO_FILL -> RemoteConfigManager.getConfig().retryMaxDelayMillis
            AdFailureReason.NO_INTERNET -> RemoteConfigManager.getConfig().retryInitialDelayMillis
            else -> 0L
        }
    }

    private fun hideAdContainer(container: ViewGroup) {
        container.removeAllViews()
        container.visibility = View.GONE
    }

    private fun attachNativeRevenueTracking(ad: NativeAd, adUnitId: String) {
        attachRevenueTracking(
            type = AdType.NATIVE,
            adUnitId = adUnitId,
            responseInfoProvider = { ad.responseInfo?.mediationAdapterClassName },
            setListener = { listener -> ad.setOnPaidEventListener(listener) }
        )
    }

    private fun attachRevenueTracking(
        type: AdType,
        adUnitId: String,
        responseInfoProvider: () -> String?,
        setListener: (com.google.android.gms.ads.OnPaidEventListener) -> Unit
    ) {
        SafeExecutor.run("attach revenue tracking") {
            setListener(
                com.google.android.gms.ads.OnPaidEventListener { adValue ->
                    SafeExecutor.run("paid event") {
                        eventTracker.revenuePaid(
                            type,
                            adValue.toRevenueInfo(
                                provider = providerWaterfall.currentProvider(type).name,
                                mediationNetwork = responseInfoProvider(),
                                adUnitId = adUnitId
                            )
                        )
                    }
                }
            )
        }
    }

    private fun clearCachedAds(clearViews: Boolean) {
        interstitialAd = null
        rewardedAd = null
        appOpenAd = null
        appOpenLoadedAtMillis = 0L
        cachedNativeAd?.destroy()
        cachedNativeAd = null
        pendingNativeContainer = WeakReference(null)
        AdType.entries.forEach(AdStateManager::reset)

        if (clearViews) {
            bannerViews.keys.toList().forEach { container ->
                bannerViews.remove(container)?.destroy()
                container.removeAllViews()
            }
            nativeAds.keys.toList().forEach { container ->
                nativeAds.remove(container)?.destroy()
                container.removeAllViews()
            }
        } else {
            nativeAds.values.toList().forEach(NativeAd::destroy)
            nativeAds.clear()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveBannerSize(container: ViewGroup): AdSize {
        val density = container.resources.displayMetrics.density
        val widthPixels = container.width.takeIf { it > 0 }
            ?: container.resources.displayMetrics.widthPixels
        val adWidth = (widthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(container.context, adWidth)
    }

    private fun isAppOpenAvailable(): Boolean {
        val maxAgeMillis = 4 * 60 * 60 * 1000L
        return appOpenAd != null && SystemClock.elapsedRealtime() - appOpenLoadedAtMillis < maxAgeMillis
    }

    private fun Activity.isSafeForAdShow(): Boolean {
        return !isFinishing && !isDestroyed
    }

    private fun registerLifecycleCallbacks(application: Application) {
        if (lifecycleRegistered) return
        lifecycleRegistered = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity.get() === activity) {
                    currentActivity = WeakReference(null)
                }
            }
        })

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                SessionManager.startNewSession()
                RevenueAggregator.resetSession()
                lastForegroundAtMillis = SystemClock.elapsedRealtime()
                currentActivity.get()?.let { activity ->
                    showAppOpenIfAvailable(activity)
                } ?: application.let(::loadAppOpen)
            }
        })
    }

    private fun registerTrimCallbacks(application: Application) {
        if (trimCallbacksRegistered) return
        trimCallbacksRegistered = true
        application.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Suppress("DEPRECATION")
            override fun onLowMemory() {
                onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
            }

            override fun onTrimMemory(level: Int) {
                this@AdsRepositoryImpl.onTrimMemory(level)
            }
        })
    }
}
