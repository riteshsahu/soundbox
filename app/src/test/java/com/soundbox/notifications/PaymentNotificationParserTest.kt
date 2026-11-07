package com.soundbox.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PaymentNotificationParserTest {
    @Test
    fun parsesClearIncomingCreditAndIndianAmountGrouping() {
        val parsed = PaymentNotificationParser.parse(
            PaymentProvider.GOOGLE_PAY.packageName,
            "Payment received",
            "₹ 1,25,000.50 has been credited to your account"
        )

        assertNotNull(parsed)
        assertEquals(12_500_050L, parsed?.amountPaise)
        assertEquals(PaymentProvider.GOOGLE_PAY, parsed?.source)
    }

    @Test
    fun parsesHindiIncomingCredit() {
        val parsed = PaymentNotificationParser.parse(
            PaymentProvider.PHONEPE.packageName,
            "भुगतान सूचना",
            "₹500 आपके खाते में जमा हो गए"
        )

        assertEquals(50_000L, parsed?.amountPaise)
        assertEquals(PaymentProvider.PHONEPE, parsed?.source)
    }

    @Test
    fun acceptsCurrencyCodeAndDecimalAmount() {
        val parsed = PaymentNotificationParser.parse(
            PaymentProvider.PAYTM.packageName,
            "Money received",
            "INR 15.5 received"
        )

        assertEquals(1_550L, parsed?.amountPaise)
    }

    @Test
    fun rejectsDebit() = assertRejected("₹500 debited from your account")

    @Test
    fun rejectsFailedPaymentEvenWhenItAlsoSaysReceived() =
        assertRejected("₹500 received, but the transfer failed")

    @Test
    fun rejectsPendingPayment() = assertRejected("₹500 payment is pending")

    @Test
    fun rejectsReversedOrRefundedPayment() {
        assertRejected("₹500 received, then reversed")
        assertRejected("₹500 payment refunded")
    }

    @Test
    fun rejectsAmbiguousSuccessfulPaymentWithoutIncomingDirection() =
        assertRejected("Payment successful for ₹500")

    @Test
    fun rejectsFutureOrRequestedCreditWording() {
        assertRejected("₹500 will be credited to your account")
        assertRejected("₹500 प्राप्त करें")
    }

    @Test
    fun rejectsMissingAmountAndAmountOnlyMessages() {
        assertRejected("Payment received successfully")
        assertRejected("₹500")
    }

    @Test
    fun rejectsUnrelatedNotification() = assertRejected("Your bill of ₹500 is due tomorrow")

    @Test
    fun rejectsCashbackAndUnsettledMessages() {
        assertRejected("Cashback of ₹500 credited")
        assertRejected("₹500 received but not settled")
        assertRejected("₹500 प्राप्त हुए लेकिन भुगतान सफल नहीं हुआ")
    }

    @Test
    fun rejectsMultipleAmountsAsAmbiguous() =
        assertRejected("₹500 received; available balance ₹2,000")

    @Test
    fun rejectsPackageOutsideExactAllowlist() {
        val parsed = PaymentNotificationParser.parse(
            "com.google.android.apps.nbu.paisa.user.clone",
            "Payment received",
            "₹500 received"
        )
        assertNull(parsed)
    }

    private fun assertRejected(text: String) {
        assertNull(
            PaymentNotificationParser.parse(
                PaymentProvider.GOOGLE_PAY.packageName,
                "Payment update",
                text
            )
        )
    }
}
