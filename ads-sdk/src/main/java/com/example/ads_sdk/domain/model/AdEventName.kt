package com.example.ads_sdk.domain.model

/** Canonical analytics names emitted by the central ad event tracker. */
enum class AdEventName(val value: String) {
    AD_LOADED("ad_loaded"),
    AD_FAILED("ad_failed"),
    AD_SHOWN("ad_shown"),
    AD_CLICKED("ad_clicked"),
    REWARD_EARNED("reward_earned"),
    AD_REVENUE_PAID("ad_revenue_paid")
}
