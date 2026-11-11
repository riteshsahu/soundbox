package com.soundbox.notifications

/** Exact package IDs only. Never broaden this to substring or dynamic matching. */
enum class PaymentProvider(
    val packageName: String,
    val displayName: String,
    private val alternatePackageNames: Set<String> = emptySet()
) {
    GOOGLE_PAY(
        "com.google.android.apps.nbu.paisa.user",
        "Google Pay",
        setOf("com.google.android.apps.nbu.paisa.merchant")
    ),
    PHONEPE("com.phonepe.app", "PhonePe", setOf("com.phonepe.app.business")),
    PAYTM("net.one97.paytm", "Paytm", setOf("net.one97.paytm.merchant"));

    companion object {
        fun fromPackageName(packageName: String): PaymentProvider? =
            entries.firstOrNull {
                it.packageName == packageName || packageName in it.alternatePackageNames
            }
    }
}
