package com.example.ads_sdk.core

import com.example.ads_sdk.domain.model.AdRevenueInfo
import com.example.ads_sdk.domain.model.RevenueBucket
import com.example.ads_sdk.domain.model.RevenueSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** In-memory revenue aggregation for sessions, days, screens, and impression buckets. */
object RevenueAggregator {
    private val sessionRevenue = RevenueCounter()
    private val dailyRevenue = ConcurrentHashMap<String, RevenueCounter>()
    private val screenRevenue = ConcurrentHashMap<String, RevenueCounter>()
    private val providerRevenue = ConcurrentHashMap<String, RevenueCounter>()
    private val currency = "USD"

    @Volatile
    private var currentScreen: String = "unknown"

    fun setCurrentScreen(screenName: String) {
        currentScreen = screenName.ifBlank { "unknown" }
    }

    fun record(revenue: AdRevenueInfo) {
        val code = revenue.currencyCode.ifBlank { currency }
        sessionRevenue.add(revenue.micros)
        dailyRevenue.getOrPut(todayKey()) { RevenueCounter() }.add(revenue.micros)
        screenRevenue.getOrPut(currentScreen) { RevenueCounter() }.add(revenue.micros)
        providerRevenue.getOrPut(revenue.provider) { RevenueCounter() }.add(revenue.micros)
    }

    fun getSessionRevenue(): RevenueSummary = sessionRevenue.summary(currency)

    fun getDailyRevenue(): RevenueSummary = dailyRevenue[todayKey()]?.summary(currency)
        ?: RevenueSummary(0L, currency, 0)

    fun getScreenRevenue(screenName: String = currentScreen): RevenueSummary {
        return screenRevenue[screenName]?.summary(currency) ?: RevenueSummary(0L, currency, 0)
    }

    fun getRevenueBuckets(): List<RevenueBucket> {
        return providerRevenue.map { RevenueBucket(it.key, it.value.summary(currency)) }
    }

    fun resetSession() {
        sessionRevenue.reset()
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private class RevenueCounter {
        private val micros = AtomicLong(0L)
        private val impressions = AtomicInteger(0)

        fun add(valueMicros: Long) {
            micros.addAndGet(valueMicros.coerceAtLeast(0L))
            impressions.incrementAndGet()
        }

        fun summary(currency: String): RevenueSummary {
            return RevenueSummary(micros.get(), currency, impressions.get())
        }

        fun reset() {
            micros.set(0L)
            impressions.set(0)
        }
    }
}
