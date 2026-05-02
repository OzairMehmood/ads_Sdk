package com.example.ads_sdk.ads

import android.view.ViewGroup
import com.example.ads_sdk.core.AdsManager

/** Backward-compatible native helper. New integrations should call AdsManager directly. */
@Deprecated("Use AdsManager.loadNative(container).")
object NativeAdLoader {
    fun load(container: ViewGroup) = AdsManager.loadNative(container)
}
