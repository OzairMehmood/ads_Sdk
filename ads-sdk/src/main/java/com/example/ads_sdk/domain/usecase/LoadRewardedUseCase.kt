package com.example.ads_sdk.domain.usecase

import android.content.Context
import com.example.ads_sdk.domain.repository.AdsRepository

/** Ensures a rewarded ad is cached for the next reward flow. */
internal class LoadRewardedUseCase(private val repository: AdsRepository) {
    operator fun invoke(context: Context) = repository.loadRewarded(context)
}
