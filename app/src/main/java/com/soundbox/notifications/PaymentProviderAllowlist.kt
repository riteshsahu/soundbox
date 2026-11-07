package com.soundbox.notifications

/** Exact package IDs only. Never broaden this to substring or dynamic matching. */
enum class PaymentProvider(
    val packageName: String,
    val displayName: String
) {
    GOOGLE_PAY("com.google.android.apps.nbu.paisa.user", "Google Pay"),
    PHONEPE("com.phonepe.app", "PhonePe"),
    PAYTM("net.one97.paytm", "Paytm");

    companion object {
        fun fromPackageName(packageName: String): PaymentProvider? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
