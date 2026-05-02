package com.example.ads_sdk.core

import android.os.SystemClock
import com.example.ads_sdk.domain.model.AdType
import com.example.ads_sdk.domain.model.SdkMetricsSnapshot
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Internal observability engine for load/show performance and failure rates. */
object SdkMetrics {
    private val loadStarts = EnumMap<AdType, AtomicLong>(AdType::class.java)
    private val showStarts = EnumMap<AdType, AtomicLong>(AdType::class.java)
    private val loadAttempts = counters()
    private val loadSuccesses = counters()
    private val loadFailures = counters()
    private val showAttempts = counters()
    private val showSuccesses = counters()
    private val loadTimeTotals = longCounters()
    private val showTimeTotals = longCounters()

    fun markLoadStart(type: AdType) {
        loadAttempts.getValue(type).incrementAndGet()
        loadStarts.getValue(type).set(SystemClock.elapsedRealtime())
    }

    fun markLoadSuccess(type: AdType) {
        loadSuccesses.getValue(type).incrementAndGet()
        recordDuration(loadStarts, loadTimeTotals, type)
    }

    fun markLoadFailure(type: AdType) {
        loadFailures.getValue(type).incrementAndGet()
        recordDuration(loadStarts, loadTimeTotals, type)
    }

    fun markShowStart(type: AdType) {
        showAttempts.getValue(type).incrementAndGet()
        showStarts.getValue(type).set(SystemClock.elapsedRealtime())
    }

    fun markShowSuccess(type: AdType) {
        showSuccesses.getValue(type).incrementAndGet()
        recordDuration(showStarts, showTimeTotals, type)
    }

    fun snapshot(): SdkMetricsSnapshot {
        return SdkMetricsSnapshot(
            loadAttempts = loadAttempts.toIntMap(),
            loadSuccesses = loadSuccesses.toIntMap(),
            loadFailures = loadFailures.toIntMap(),
            showAttempts = showAttempts.toIntMap(),
            showSuccesses = showSuccesses.toIntMap(),
            averageLoadTimeMillis = averages(loadTimeTotals, loadSuccesses, loadFailures),
            averageShowTimeMillis = averages(showTimeTotals, showSuccesses, null)
        )
    }

    fun dashboard(): String {
        val snapshot = snapshot()
        return buildString {
            appendLine("Ads SDK metrics")
            AdType.entries.forEach { type ->
                val attempts = snapshot.loadAttempts[type] ?: 0
                val failures = snapshot.loadFailures[type] ?: 0
                val failRate = if (attempts == 0) 0 else failures * 100 / attempts
                appendLine(
                    "${type.name}: loads=$attempts success=${snapshot.loadSuccesses[type] ?: 0} " +
                        "fail=$failures failRate=${failRate}% shows=${snapshot.showSuccesses[type] ?: 0} " +
                        "avgLoadMs=${snapshot.averageLoadTimeMillis[type] ?: 0}"
                )
            }
        }
    }

    private fun counters(): EnumMap<AdType, AtomicInteger> {
        return EnumMap<AdType, AtomicInteger>(AdType::class.java).apply {
            AdType.entries.forEach { put(it, AtomicInteger(0)) }
        }
    }

    private fun longCounters(): EnumMap<AdType, AtomicLong> {
        return EnumMap<AdType, AtomicLong>(AdType::class.java).apply {
            AdType.entries.forEach { put(it, AtomicLong(0L)) }
        }
    }

    private fun recordDuration(
        starts: EnumMap<AdType, AtomicLong>,
        totals: EnumMap<AdType, AtomicLong>,
        type: AdType
    ) {
        val started = starts.getValue(type).getAndSet(0L)
        if (started > 0L) totals.getValue(type).addAndGet(SystemClock.elapsedRealtime() - started)
    }

    private fun EnumMap<AdType, AtomicInteger>.toIntMap(): Map<AdType, Int> {
        return mapValues { it.value.get() }
    }

    private fun averages(
        totals: EnumMap<AdType, AtomicLong>,
        successes: EnumMap<AdType, AtomicInteger>,
        failures: EnumMap<AdType, AtomicInteger>?
    ): Map<AdType, Long> {
        return AdType.entries.associateWith { type ->
            val count = successes.getValue(type).get() + (failures?.getValue(type)?.get() ?: 0)
            if (count == 0) 0L else totals.getValue(type).get() / count
        }
    }
}
