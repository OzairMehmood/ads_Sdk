package com.example.ads_sdk.domain.usecase

import android.app.Activity
import com.example.ads_sdk.domain.repository.AdsRepository

/** Attempts to show an app-open ad when lifecycle and cooldown gates allow it. */
internal class ShowAppOpenUseCase(private val repository: AdsRepository) {
    operator fun invoke(activity: Activity) = repository.showAppOpenIfAvailable(activity)
}
