package com.soundbox.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PaymentHistoryRulesTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun prunesRecordsOlderThanThreeCalendarMonthsAndKeepsCutoff() {
        val now = calendar(2026, Calendar.NOVEMBER, 8, 12).timeInMillis
        val cutoff = PaymentHistoryRules.retentionCutoff(now, utc)
        val retained = PaymentRecord(50_000L, cutoff, "Google Pay")
        val expired = PaymentRecord(20_000L, cutoff - 1L, "PhonePe")

        assertEquals(listOf(retained), PaymentHistoryRules.pruneExpired(listOf(retained, expired), now, utc))
    }

    @Test
    fun todayTotalUsesDeviceLocalDayAndCountsUniqueRecordsOnly() {
        val india = TimeZone.getTimeZone("Asia/Kolkata")
        val now = calendar(2026, Calendar.NOVEMBER, 8, 1).timeInMillis
        val bounds = PaymentHistoryRules.localDayBounds(now, india)
        val todayRecord = PaymentRecord(50_000L, bounds.startInclusiveMillis + 60_000L, "Google Pay")
        val duplicate = todayRecord.copy()
        val previousLocalDay = PaymentRecord(90_000L, bounds.startInclusiveMillis - 1L, "Paytm")
        val nextLocalDay = PaymentRecord(40_000L, bounds.endExclusiveMillis, "PhonePe")

        assertEquals(
            50_000L,
            PaymentHistoryRules.todayTotal(
                listOf(todayRecord, duplicate, previousLocalDay, nextLocalDay),
                now,
                india
            )
        )
    }

    @Test
    fun deduplicatesSameSourceAndAmountOnlyInsideShortWindow() {
        val first = PaymentRecord(50_000L, 1_000_000L, "Google Pay")
        val duplicate = first.copy(receivedAtMillis = 1_020_000L)
        val differentSource = duplicate.copy(sourceApp = "PhonePe")
        val later = first.copy(receivedAtMillis = 1_091_001L)

        assertTrue(PaymentHistoryRules.isDuplicate(duplicate, first))
        assertFalse(PaymentHistoryRules.isDuplicate(differentSource, first))
        assertFalse(PaymentHistoryRules.isDuplicate(later, first))
    }

    private fun calendar(year: Int, month: Int, day: Int, hour: Int): Calendar =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }
}
