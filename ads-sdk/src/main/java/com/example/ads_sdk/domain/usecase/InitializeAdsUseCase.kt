package com.example.ads_sdk.domain.usecase

import android.app.Application
import com.example.ads_sdk.config.AdsConfig
import com.example.ads_sdk.domain.repository.AdsRepository

/** Initializes the ads repository and preloads startup ad formats. */
internal class InitializeAdsUseCase(private val repository: AdsRepository) {
    operator fun invoke(application: Application, config: AdsConfig) {
        repository.initialize(application, config)
    }
}
