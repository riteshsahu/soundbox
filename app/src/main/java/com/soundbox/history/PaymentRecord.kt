package com.soundbox.history

data class PaymentRecord(
    val amountPaise: Long,
    val receivedAtMillis: Long,
    val sourceApp: String
)

data class TimeBounds(
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long
)

data class PaymentHistoryFilter(
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long,
    val sourceApp: String? = null,
    val minimumAmountPaise: Long? = null,
    val maximumAmountPaise: Long? = null
)
