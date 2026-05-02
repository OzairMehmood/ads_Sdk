package com.example.ads_sdk.domain.usecase

import android.content.Context
import com.example.ads_sdk.domain.repository.AdsRepository

/** Ensures an interstitial is cached and ready for the next eligible show. */
internal class LoadInterstitialUseCase(private val repository: AdsRepository) {
    operator fun invoke(context: Context) = repository.loadInterstitial(context)
}
