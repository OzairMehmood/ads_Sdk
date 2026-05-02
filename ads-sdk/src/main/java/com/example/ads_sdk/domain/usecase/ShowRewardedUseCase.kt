package com.example.ads_sdk.domain.usecase

import android.app.Activity
import com.example.ads_sdk.domain.model.RewardCallback
import com.example.ads_sdk.domain.repository.AdsRepository

/** Shows a rewarded ad and returns a stable SDK reward model to host apps. */
internal class ShowRewardedUseCase(private val repository: AdsRepository) {
    operator fun invoke(activity: Activity, callback: RewardCallback) {
        repository.showRewarded(activity, callback)
    }
}
