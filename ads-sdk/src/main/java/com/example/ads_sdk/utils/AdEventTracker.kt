package com.example.ads_sdk.utils

import com.example.ads_sdk.domain.model.AdEventListener
import com.example.ads_sdk.domain.model.AdEventName
import com.example.ads_sdk.domain.model.AdEventPayload
import com.example.ads_sdk.domain.model.AdLoadErrorInfo
import com.example.ads_sdk.domain.model.AdRevenueInfo
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.model.RewardInfo
import com.example.ads_sdk.core.RevenueAggregator
import com.example.ads_sdk.core.SessionManager

/**
 * Central event hub for all ad formats. Apps can listen through AdsManager,
 * while the SDK keeps event names consistent for analytics integrations.
 */
internal class AdEventTracker {
    @Volatile
    private var listener: AdEventListener? = null

    fun setListener(listener: AdEventListener?) {
        this.listener = listener
    }

    fun loaded(type: AdType) {
        val payload = payload(AdEventName.AD_LOADED, type)
        track(payload)
        notify("onAdLoaded") { it.onAdLoaded(type) }
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    fun failed(type: AdType, error: AdLoadErrorInfo) {
        val payload = payload(AdEventName.AD_FAILED, type, error = error)
        track(payload)
        notify("onAdFailedToLoad") { it.onAdFailedToLoad(type, error) }
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    fun shown(type: AdType) {
        val payload = payload(AdEventName.AD_SHOWN, type)
        track(payload)
        notify("onAdShown") { it.onAdShown(type) }
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    fun clicked(type: AdType) {
        val payload = payload(AdEventName.AD_CLICKED, type)
        track(payload)
        notify("onAdClicked") { it.onAdClicked(type) }
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    fun dismissed(type: AdType) {
        notify("onAdDismissed") { it.onAdDismissed(type) }
    }

    fun failedToShow(type: AdType, message: String) {
        AdsLogger.e("${type.name} show failed: $message")
        notify("onAdFailedToShow") { it.onAdFailedToShow(type, message) }
    }

    fun rewardEarned(reward: RewardInfo) {
        val payload = payload(AdEventName.REWARD_EARNED, AdType.REWARDED, reward = reward)
        track(payload)
        notify("onRewardEarned") { it.onRewardEarned(reward) }
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    fun revenuePaid(type: AdType, revenue: AdRevenueInfo) {
        RevenueAggregator.record(revenue)
        val payload = payload(AdEventName.AD_REVENUE_PAID, type, revenue = revenue)
        track(payload)
        notify("onAdEvent") { it.onAdEvent(payload) }
    }

    private fun payload(
        name: AdEventName,
        type: AdType,
        error: AdLoadErrorInfo? = null,
        reward: RewardInfo? = null,
        revenue: AdRevenueInfo = AdRevenueInfo()
    ): AdEventPayload {
        return AdEventPayload(
            name = name,
            type = type,
            sessionId = SessionManager.currentSessionId(),
            timestampMillis = System.currentTimeMillis(),
            revenue = revenue,
            error = error,
            reward = reward
        )
    }

    private fun track(event: AdEventPayload) {
        val detail = event.error?.let { " code=${it.code} reason=${it.reason}" }
            ?: event.reward?.let { " reward=${it.amount} ${it.type}" }
            ?: event.revenue.takeIf { event.name == AdEventName.AD_REVENUE_PAID }
                ?.let { " revenueMicros=${it.micros} ${it.currencyCode}" }
            ?: ""
        AdsLogger.d("${event.name.value}: ${event.type.name} session=${event.sessionId}$detail")
    }

    private fun notify(label: String, block: (AdEventListener) -> Unit) {
        val current = listener ?: return
        SafeExecutor.run(label) { block(current) }
    }
}
