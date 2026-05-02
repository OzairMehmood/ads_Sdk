package com.example.ads_sdk.domain.state

import com.example.ads_sdk.domain.model.AdType

data class AdStateSnapshot(
    val type: AdType,
    val isLoading: Boolean,
    val isReady: Boolean,
    val isShowing: Boolean,
    val cooldownUntilMillis: Long,
    val lastRequestAtMillis: Long,
    val updatedAtMillis: Long
)
