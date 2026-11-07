package com.soundbox.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentPaymentDeduplicatorTest {
    @Test
    fun suppressesSameSourceAndAmountInsideShortWindow() {
        val deduplicator = RecentPaymentDeduplicator()
        val payment = ParsedPayment(50_000L, PaymentProvider.GOOGLE_PAY)

        assertTrue(deduplicator.shouldAnnounce(payment, 1_000_000L))
        assertFalse(deduplicator.shouldAnnounce(payment, 1_020_000L))
    }

    @Test
    fun allowsSameAmountFromDifferentSource() {
        val deduplicator = RecentPaymentDeduplicator()

        assertTrue(
            deduplicator.shouldAnnounce(
                ParsedPayment(50_000L, PaymentProvider.GOOGLE_PAY),
                1_000_000L
            )
        )
        assertTrue(
            deduplicator.shouldAnnounce(
                ParsedPayment(50_000L, PaymentProvider.PHONEPE),
                1_010_000L
            )
        )
    }

    @Test
    fun allowsSameSourceAndAmountAfterWindowExpires() {
        val deduplicator = RecentPaymentDeduplicator()
        val payment = ParsedPayment(50_000L, PaymentProvider.PAYTM)

        assertTrue(deduplicator.shouldAnnounce(payment, 1_000_000L))
        assertTrue(deduplicator.shouldAnnounce(payment, 1_091_001L))
    }
}
