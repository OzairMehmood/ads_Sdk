package com.example.ads_sdk.data.mapper

import com.example.ads_sdk.domain.model.AdRevenueInfo
import com.google.android.gms.ads.AdValue
import java.util.UUID

/** Converts paid impression values into SDK-owned revenue attribution models. */
internal fun AdValue.toRevenueInfo(
    provider: String,
    mediationNetwork: String?,
    adUnitId: String?
): AdRevenueInfo {
    return AdRevenueInfo(
        micros = valueMicros,
        currencyCode = currencyCode,
        precision = precisionType.toString(),
        provider = provider,
        mediationNetwork = mediationNetwork,
        adUnitId = adUnitId,
        impressionId = UUID.randomUUID().toString(),
        isEstimated = false
    )
}
