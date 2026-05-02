package com.example.ads_sdk.ads

import android.view.ViewGroup
import com.example.ads_sdk.core.AdsManager

/** Backward-compatible banner helper. New integrations should call AdsManager directly. */
@Deprecated("Use AdsManager.loadBanner(container).")
object BannerAdView {
    fun attach(container: ViewGroup) = AdsManager.loadBanner(container)
}
