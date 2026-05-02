# MyAdsSdk

Enterprise-grade Android Ads SDK library module written in Kotlin.

## Folder Structure

```text
MyAdsSdk/
|-- ads-sdk/
|   |-- build.gradle.kts
|   `-- src/main/
|       |-- AndroidManifest.xml
|       |-- java/com/example/ads_sdk/
|       |   |-- ads/
|       |   |-- config/
|       |   |-- core/
|       |   |   |-- ColdStartGuard.kt
|       |   |   |-- ActivityAdLifecycleGuard.kt
|       |   |   |-- AdsManager.kt
|       |   |   |-- AdStateManager.kt
|       |   |   |-- AdsConcurrencyGuard.kt
|       |   |   |-- ProviderWaterfall.kt
|       |   |   |-- RevenueAggregator.kt
|       |   |   |-- SessionManager.kt
|       |   |   |-- DeviceSignalManager.kt
|       |   |   |-- SdkInfo.kt
|       |   |   `-- SdkMetrics.kt
|       |   |-- data/
|       |   |   |-- mapper/
|       |   |   |-- provider/
|       |   |   `-- repository/
|       |   |-- domain/
|       |   |   |-- model/
|       |   |   |-- provider/
|       |   |   |-- repository/
|       |   |   |-- state/
|       |   |   `-- usecase/
|       |   |-- presentation/nativead/
|       |   |   |-- NativeAdBinder.kt
|       |   |   `-- NativeAdRecyclerAdapter.kt
|       |   `-- utils/
|       |       |-- AdEventTracker.kt
|       |       |-- AdsLogger.kt
|       |       |-- MainThread.kt
|       |       |-- NetworkMonitor.kt
|       |       |-- RetryController.kt
|       |       `-- SafeExecutor.kt
|       `-- res/layout/
|           |-- native_ad_layout.xml
|           `-- item_native_ad_container.xml
`-- app/
    `-- sample app usage
```

## Gradle

```kotlin
dependencies {
    implementation(project(":ads-sdk"))
}
```

The library uses Google Mobile Ads, lifecycle-process, and RecyclerView support.

## Application Setup

```kotlin
class MyAdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AdsManager.init(
            application = this,
            config = AdsConfig(
                debugLogs = true,
                interstitialInterval = 3,
                retryEnabled = true,
                retryInitialDelayMillis = 10_000L,
                retryMaxDelayMillis = 30_000L,
                appOpenCooldownMillis = 30_000L,
                loadDebounceMillis = 1_000L,
                coldStartDelayMillis = 3_000L,
                autoLoadAdsOnInit = true
            )
        )
    }
}
```

Host apps must provide a real AdMob app id. The sample uses Google's test id.

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713" />
```

## Public API

```kotlin
AdsManager.init(application)
AdsManager.loadAds(context)
AdsManager.loadBanner(container)
AdsManager.loadNative(container)
AdsManager.showInterstitial(activity)
AdsManager.showRewarded(activity) { reward -> }
AdsManager.showAppOpen(activity)
AdsManager.getAdState(AdType.INTERSTITIAL)
AdsManager.onTrimMemory(level)
AdsManager.getSessionRevenue()
AdsManager.getDailyRevenue()
AdsManager.getScreenRevenue("Home")
AdsManager.getDeviceTier()
AdsManager.recordUserEngagement()
AdsManager.getDebugDashboardLogs()
```

`AdsManager` is protected by `AdsConcurrencyGuard`, which serializes public API calls and makes initialization idempotent.

## State And Retry

`AdStateManager` tracks loading, ready, showing, cooldown, and last-request timestamps per ad type. Failed loads retry with bounded exponential backoff from 10 to 30 seconds by default.

No-internet and no-fill are classified through `AdFailureReason`. Banner and native containers are hidden gracefully when the SDK cannot load an ad.

## Waterfall

`ProviderWaterfall` manages provider priority and failover:

```text
AdMob -> Meta -> Unity
```

AdMob is the functional default provider. Meta and Unity are structural providers ready for SDK-specific loaders when those dependencies are added.

## Cold Start Protection

`ColdStartGuard` delays startup ad work so the SDK does not compete with splash, first frame, or navigation setup. Explicit ad loads during startup are queued and flushed after `coldStartDelayMillis`; duplicate launch requests are collapsed through single-flight state checks.

## Memory Pressure

The repository registers `ComponentCallbacks2` automatically. On memory pressure it clears cached full-screen ads, cached native ads, and view-held ads when the UI is hidden. Host apps can also forward memory events manually:

```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    AdsManager.onTrimMemory(level)
}
```

## Event Tracking

```kotlin
AdsManager.setAdEventListener(object : AdEventListener {
    override fun onAdEvent(event: AdEventPayload) {
        // event.name, event.type, event.sessionId, event.timestampMillis, event.revenue
    }
})
```

Central event names:

```text
ad_loaded
ad_failed
ad_shown
ad_clicked
reward_earned
```

Each event includes a session id, timestamp, SDK-owned error/reward models, and a placeholder revenue payload ready for paid-event integration.

Paid impression hooks are attached to AdMob ad objects where supported. Revenue events use:

```kotlin
AdRevenueInfo(
    micros = valueMicros,
    currencyCode = "USD",
    provider = "admob",
    mediationNetwork = "...",
    adUnitId = "...",
    impressionId = "..."
)
```

## SDK Versioning And Feature Flags

```kotlin
SdkInfo.VERSION_NAME
SdkInfo.VERSION_CODE
SdkInfo.isFeatureEnabled(SdkFeature.COLD_START_PROTECTION)
```

Feature defaults can be overridden without breaking older `AdsConfig` callers:

```kotlin
AdsConfig(
    featureOverrides = mapOf(
        SdkFeature.IMPRESSION_REVENUE_TRACKING to true,
        SdkFeature.APP_OPEN_COOLDOWN to true
    )
)
```

## Crash Immunity

`SafeExecutor` wraps SDK and host callbacks so listener exceptions are logged instead of crashing the host app. Full-screen callbacks are lifecycle guarded and only dispatch while the bound activity is alive.

## Revenue And Observability

Revenue is aggregated from impression-level paid events:

```kotlin
AdsManager.setCurrentScreen("Home")
val session = AdsManager.getSessionRevenue()
val daily = AdsManager.getDailyRevenue()
val screen = AdsManager.getScreenRevenue("Home")
val buckets = AdsManager.getRevenueBuckets()
```

Internal observability tracks load attempts, successes, failures, show attempts, show successes, fail rate, and average load/show time:

```kotlin
val metrics = AdsManager.getMetricsSnapshot()
val dashboard = AdsManager.getDebugDashboardLogs()
```

## User And Device Signals

`DeviceSignalManager` classifies devices into `LOW`, `MID`, or `HIGH` tiers and adjusts interstitial frequency using session ad fatigue and engagement:

```kotlin
AdsManager.getDeviceTier()
AdsManager.recordUserEngagement(delta = 1)
```

The session manager resets ad counters and session revenue on foreground sessions and attaches the global session id to every ad event.

## Mediation Layer

`AdProvider` is the provider abstraction. `AdMobAdProvider` is the default implementation, with placeholder structure for Meta and Unity providers:

```text
domain/provider/AdProvider.kt
data/provider/AdMobAdProvider.kt
data/provider/MetaAdProvider.kt
data/provider/UnityAdProvider.kt
```

## Native Ads In RecyclerView

Wrap an existing adapter to inject native ad rows:

```kotlin
val contentAdapter = FeedAdapter()
binding.recyclerView.adapter = AdsManager.withNativeAds(
    adapter = contentAdapter,
    adInterval = 6
)
```

`NativeAdRecyclerAdapter` destroys native ads when rows recycle, detach, or the adapter is released:

```kotlin
(binding.recyclerView.adapter as? NativeAdRecyclerAdapter)?.release()
```

## Banner Container Example

```xml
<FrameLayout
    android:id="@+id/bannerContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="64dp" />
```

```kotlin
AdsManager.loadBanner(binding.bannerContainer)
```

## Verification

```bash
./gradlew assembleDebug
```
