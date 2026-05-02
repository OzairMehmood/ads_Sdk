package com.example.ads_sdk.domain.usecase

import android.content.Context
import com.example.ads_sdk.domain.repository.AdsRepository

/** Preloads one app-open ad for the next foreground event. */
internal class LoadAppOpenUseCase(private val repository: AdsRepository) {
    operator fun invoke(context: Context) = repository.loadAppOpen(context)
}
