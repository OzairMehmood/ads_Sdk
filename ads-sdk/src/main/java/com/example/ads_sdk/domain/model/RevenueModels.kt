package com.example.ads_sdk.domain.model

data class RevenueSummary(
    val totalMicros: Long,
    val currencyCode: String,
    val impressionCount: Int
)

data class RevenueBucket(
    val key: String,
    val summary: RevenueSummary
)

enum class DeviceTier {
    LOW,
    MID,
    HIGH
}

data class SdkMetricsSnapshot(
    val loadAttempts: Map<AdType, Int>,
    val loadSuccesses: Map<AdType, Int>,
    val loadFailures: Map<AdType, Int>,
    val showAttempts: Map<AdType, Int>,
    val showSuccesses: Map<AdType, Int>,
    val averageLoadTimeMillis: Map<AdType, Long>,
    val averageShowTimeMillis: Map<AdType, Long>
)
