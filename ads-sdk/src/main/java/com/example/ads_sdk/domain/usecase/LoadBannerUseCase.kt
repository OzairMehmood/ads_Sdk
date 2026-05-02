package com.example.ads_sdk.domain.usecase

import android.view.ViewGroup
import com.example.ads_sdk.domain.repository.AdsRepository

/** Loads and attaches a banner ad to the provided container. */
internal class LoadBannerUseCase(private val repository: AdsRepository) {
    operator fun invoke(container: ViewGroup) = repository.loadBanner(container)
}
