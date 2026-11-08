package com.soundbox.history

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

object PaymentHistoryRules {
    const val RETENTION_MONTHS = 3
    const val DEDUPLICATION_WINDOW_MILLIS = 90_000L

    fun retentionCutoff(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, -RETENTION_MONTHS)
        }.timeInMillis

    fun localDayBounds(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): TimeBounds {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return TimeBounds(start, calendar.timeInMillis)
    }

    fun isDuplicate(
        candidate: PaymentRecord,
        existing: PaymentRecord,
        windowMillis: Long = DEDUPLICATION_WINDOW_MILLIS
    ): Boolean = candidate.sourceApp == existing.sourceApp &&
        candidate.amountPaise == existing.amountPaise &&
        abs(candidate.receivedAtMillis - existing.receivedAtMillis) <= windowMillis

    fun pruneExpired(
        records: Iterable<PaymentRecord>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<PaymentRecord> {
        val cutoff = retentionCutoff(nowMillis, timeZone)
        return records.filter { it.receivedAtMillis >= cutoff }
    }

    fun todayTotal(
        records: Iterable<PaymentRecord>,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val bounds = localDayBounds(nowMillis, timeZone)
        return records.asSequence()
            .filter {
                it.receivedAtMillis >= bounds.startInclusiveMillis &&
                    it.receivedAtMillis < bounds.endExclusiveMillis
            }
            .distinctBy { Triple(it.amountPaise, it.receivedAtMillis, it.sourceApp) }
            .sumOf(PaymentRecord::amountPaise)
    }
}
