package com.example.ads_sdk.domain.model

/** Ad formats supported by the SDK event system. */
enum class AdType {
    BANNER,
    INTERSTITIAL,
    REWARDED,
    NATIVE,
    APP_OPEN
}

/** Public, SDK-owned load error model so apps do not need a direct ads dependency. */
data class AdLoadErrorInfo(
    val code: Int,
    val message: String,
    val domain: String,
    val reason: AdFailureReason = AdFailureReason.UNKNOWN
)

/** Public reward model returned when a user earns a rewarded ad reward. */
data class RewardInfo(
    val amount: Int,
    val type: String
)

enum class AdFailureReason {
    NO_FILL,
    NO_INTERNET,
    INTERNAL,
    INVALID_REQUEST,
    UNKNOWN
}

data class AdRevenueInfo(
    val micros: Long = 0L,
    val currencyCode: String = "USD",
    val precision: String = "placeholder",
    val provider: String = "unknown",
    val mediationNetwork: String? = null,
    val adUnitId: String? = null,
    val impressionId: String? = null,
    val isEstimated: Boolean = true
)

data class MediationRevenueInfo(
    val provider: String,
    val network: String?,
    val revenue: AdRevenueInfo,
    val adapterClassName: String? = null
)

data class AdEventPayload(
    val name: AdEventName,
    val type: AdType,
    val sessionId: String,
    val timestampMillis: Long,
    val revenue: AdRevenueInfo = AdRevenueInfo(),
    val error: AdLoadErrorInfo? = null,
    val reward: RewardInfo? = null,
    val message: String? = null
)

/** Optional callback interface for analytics, debugging, and product-level ad handling. */
interface AdEventListener {
    fun onAdEvent(event: AdEventPayload) = Unit
    fun onAdLoaded(type: AdType) = Unit
    fun onAdFailedToLoad(type: AdType, error: AdLoadErrorInfo) = Unit
    fun onAdShown(type: AdType) = Unit
    fun onAdClicked(type: AdType) = Unit
    fun onAdDismissed(type: AdType) = Unit
    fun onAdFailedToShow(type: AdType, message: String) = Unit
    fun onRewardEarned(reward: RewardInfo) = Unit
}

fun interface RewardCallback {
    fun onRewardEarned(reward: RewardInfo)
}
