package com.example.ads_sdk.data.mapper

import com.example.ads_sdk.domain.model.AdLoadErrorInfo
import com.example.ads_sdk.domain.model.AdFailureReason
import com.example.ads_sdk.domain.model.RewardInfo
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem

/** Converts Google Mobile Ads SDK models into public SDK-owned models. */
internal fun LoadAdError.toInfo(): AdLoadErrorInfo {
    return AdLoadErrorInfo(
        code = code,
        message = message,
        domain = domain,
        reason = when (code) {
            AdRequest.ERROR_CODE_NO_FILL -> AdFailureReason.NO_FILL
            AdRequest.ERROR_CODE_NETWORK_ERROR -> AdFailureReason.NO_INTERNET
            AdRequest.ERROR_CODE_INVALID_REQUEST -> AdFailureReason.INVALID_REQUEST
            AdRequest.ERROR_CODE_INTERNAL_ERROR -> AdFailureReason.INTERNAL
            else -> AdFailureReason.UNKNOWN
        }
    )
}

internal fun RewardItem.toInfo(): RewardInfo {
    return RewardInfo(
        amount = amount,
        type = type
    )
}
