package com.example.ads_sdk.domain.usecase

import android.view.ViewGroup
import com.example.ads_sdk.domain.repository.AdsRepository

/** Loads, binds, and tracks the lifecycle of a native ad container. */
internal class LoadNativeUseCase(private val repository: AdsRepository) {
    operator fun invoke(container: ViewGroup) = repository.loadNative(container)
}
