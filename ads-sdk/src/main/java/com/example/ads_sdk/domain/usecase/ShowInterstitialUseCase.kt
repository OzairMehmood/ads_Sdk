package com.example.ads_sdk.domain.usecase

import android.app.Activity
import com.example.ads_sdk.domain.repository.AdsRepository

/** Shows an interstitial only when frequency and lifecycle checks pass. */
internal class ShowInterstitialUseCase(private val repository: AdsRepository) {
    operator fun invoke(activity: Activity) = repository.showInterstitial(activity)
}
