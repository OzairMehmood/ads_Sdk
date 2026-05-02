package com.example.ads_sdk.core

import com.example.ads_sdk.data.provider.AdMobAdProvider
import com.example.ads_sdk.data.provider.MetaAdProvider
import com.example.ads_sdk.data.provider.UnityAdProvider
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.provider.AdProvider
import com.example.ads_sdk.utils.AdsLogger
import java.util.EnumMap

/** Provider priority and failover coordinator: AdMob, then Meta, then Unity. */
internal class ProviderWaterfall(
    providers: List<AdProvider> = listOf(AdMobAdProvider(), MetaAdProvider(), UnityAdProvider())
) {
    private val chain = providers.sortedBy { it.priority }
    private val activeIndex = EnumMap<AdType, Int>(AdType::class.java)

    init {
        AdType.entries.forEach { activeIndex[it] = firstSupportedIndex() }
    }

    fun currentProvider(type: AdType): AdProvider {
        return chain[activeIndex[type] ?: firstSupportedIndex()]
    }

    fun reportLoadSuccess(type: AdType) {
        activeIndex[type] = firstSupportedIndex()
    }

    fun reportLoadFailure(type: AdType): AdProvider? {
        val current = activeIndex[type] ?: firstSupportedIndex()
        val next = ((current + 1) until chain.size).firstOrNull { chain[it].isLoadSupported }
        return if (next == null) {
            activeIndex[type] = firstSupportedIndex()
            null
        } else {
            activeIndex[type] = next
            chain[next].also { AdsLogger.d("Waterfall failover for ${type.name}: ${it.name}") }
        }
    }

    fun providers(): List<AdProvider> = chain

    private fun firstSupportedIndex(): Int {
        return chain.indexOfFirst { it.isLoadSupported }.takeIf { it >= 0 } ?: 0
    }
}
