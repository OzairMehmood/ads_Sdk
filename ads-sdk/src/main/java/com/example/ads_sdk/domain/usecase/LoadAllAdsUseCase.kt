package com.example.ads_sdk.domain.usecase

import android.content.Context
import com.example.ads_sdk.domain.repository.AdsRepository

/** Preloads all cacheable ad formats without touching UI containers. */
internal class LoadAllAdsUseCase(private val repository: AdsRepository) {
    operator fun invoke(context: Context) = repository.loadAll(context)
}
