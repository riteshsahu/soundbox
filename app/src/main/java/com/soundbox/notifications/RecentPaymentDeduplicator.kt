package com.soundbox.notifications

/** Short-lived, in-memory duplicate suppression without transaction IDs. */
class RecentPaymentDeduplicator(
    private val windowMillis: Long = 90_000L,
    private val maxEntries: Int = 32
) {
    private data class Entry(
        val source: PaymentProvider,
        val amountPaise: Long,
        val postedAtMillis: Long
    )

    private val recent = ArrayDeque<Entry>()

    fun shouldAnnounce(payment: ParsedPayment, postedAtMillis: Long): Boolean {
        while (recent.isNotEmpty() && postedAtMillis - recent.first().postedAtMillis > windowMillis) {
            recent.removeFirst()
        }

        val duplicate = recent.any { entry ->
            entry.source == payment.source &&
                entry.amountPaise == payment.amountPaise &&
                kotlin.math.abs(postedAtMillis - entry.postedAtMillis) <= windowMillis
        }
        if (duplicate) return false

        recent.addLast(Entry(payment.source, payment.amountPaise, postedAtMillis))
        while (recent.size > maxEntries) recent.removeFirst()
        return true
    }
}
