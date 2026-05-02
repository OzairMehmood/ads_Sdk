Got it 👍 — tumhein **single clean block** chahiye jo tum direct GitHub README.md mein paste kar sako, no sections split.

Yeh lo 👇

---

````md
# MyAdsSdk 🚀

Enterprise-grade Android Ads SDK written in Kotlin.

This SDK provides a complete monetization system:
- Banner Ads
- Interstitial Ads
- Rewarded Ads
- App Open Ads
- Native Ads (XML + RecyclerView)
- Revenue Tracking
- Device + Session Optimization
- Mediation-ready Architecture

---

# 📦 INSTALLATION

## Gradle (JitPack)

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.OzairMehmood:MyAdsSdk:1.0.0'
}
````

OR local module:

```gradle
implementation(project(":ads-sdk"))
```

---

# 📱 SETUP

## AndroidManifest.xml

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-3940256099942544~3347511713"/>
```

---

## Application Class

```kotlin
class MyAdsApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AdsManager.init(
            application = this,
            config = AdsConfig(
                debugLogs = true,
                interstitialInterval = 3,
                autoLoadAdsOnInit = true,
                coldStartDelayMillis = 3000L
            )
        )
    }
}
```

---

# 🚀 USAGE

## Load Ads

```kotlin
AdsManager.loadAds(context)
```

---

# 📺 BANNER AD

## XML

```xml
<FrameLayout
    android:id="@+id/bannerContainer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="60dp"/>
```

## Kotlin

```kotlin
AdsManager.loadBanner(binding.bannerContainer)
```

---

# 🎯 INTERSTITIAL AD

```kotlin
AdsManager.showInterstitial(this)
```

---

# 🎁 REWARDED AD

```kotlin
AdsManager.showRewarded(this) { reward ->
    // reward earned
}
```

---

# 🚀 APP OPEN AD

```kotlin
AdsManager.showAppOpen(this)
```

---

# 🧩 NATIVE AD

## XML

```xml
<com.google.android.gms.ads.nativead.NativeAdView
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <TextView
        android:id="@+id/ad_headline"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textStyle="bold"/>

    <TextView
        android:id="@+id/ad_body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>

</com.google.android.gms.ads.nativead.NativeAdView>
```

## Load

```kotlin
AdsManager.loadNative(binding.nativeContainer)
```

---

# 📊 RECYCLER VIEW NATIVE ADS

```kotlin
val adapter = FeedAdapter()

binding.recyclerView.adapter = AdsManager.withNativeAds(
    adapter = adapter,
    adInterval = 6
)
```

---

## Release

```kotlin
(binding.recyclerView.adapter as? NativeAdRecyclerAdapter)?.release()
```

---

# 📱 FULL EXAMPLE

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AdsManager.loadAds(this)

        AdsManager.loadBanner(findViewById(R.id.bannerContainer))

        findViewById<Button>(R.id.btnAd).setOnClickListener {
            AdsManager.showInterstitial(this)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AdsManager.onTrimMemory(level)
    }
}
```

---

# 📊 ANALYTICS

```kotlin
val session = AdsManager.getSessionRevenue()
val daily = AdsManager.getDailyRevenue()
val screen = AdsManager.getScreenRevenue("Home")
val tier = AdsManager.getDeviceTier()
```

---

# ⚙️ FEATURES

✔ Banner / Interstitial / Rewarded / AppOpen
✔ Native Ads (XML + RecyclerView)
✔ Cold Start Protection
✔ Retry System
✔ Device Tier Optimization
✔ Revenue Tracking
✔ Session Analytics
✔ Crash Safe SDK
✔ Mediation Ready (AdMob → Meta → Unity)

---

# 🧪 BUILD

```bash
./gradlew assembleDebug
```

---

# 🚀 RESULT

✔ Plug & Play Ads SDK
✔ Production Ready Monetization System
✔ GitHub + JitPack Compatible Library

```

---

