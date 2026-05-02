package com.example.ads_sdk.presentation.nativead

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.example.ads_sdk.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/** Binds every supported native asset to the SDK's default native ad layout. */
internal object NativeAdBinder {
    fun bind(nativeAd: NativeAd, adView: NativeAdView) {
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView>(R.id.ad_body)
        val callToAction = adView.findViewById<Button>(R.id.ad_call_to_action)
        val icon = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val media = adView.findViewById<MediaView>(R.id.ad_media)
        val advertiser = adView.findViewById<TextView>(R.id.ad_advertiser)
        val rating = adView.findViewById<RatingBar>(R.id.ad_stars)

        headline.text = nativeAd.headline
        body.bindNullableText(nativeAd.body)
        callToAction.bindNullableText(nativeAd.callToAction)
        advertiser.bindNullableText(nativeAd.advertiser)

        val starRating = nativeAd.starRating
        rating.visibility = if (starRating == null) View.GONE else View.VISIBLE
        rating.rating = starRating?.toFloat() ?: 0f

        val iconDrawable = nativeAd.icon?.drawable
        icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE
        icon.setImageDrawable(iconDrawable)

        media.mediaContent = nativeAd.mediaContent

        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = callToAction
        adView.iconView = icon
        adView.mediaView = media
        adView.advertiserView = advertiser
        adView.starRatingView = rating
        adView.setNativeAd(nativeAd)
    }

    private fun TextView.bindNullableText(value: String?) {
        visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        text = value.orEmpty()
    }
}
