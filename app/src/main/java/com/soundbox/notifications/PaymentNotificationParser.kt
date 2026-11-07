package com.soundbox.notifications

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class ParsedPayment(
    val amountPaise: Long,
    val source: PaymentProvider
)

/** A deliberately narrow parser. It never returns or stores notification text. */
object PaymentNotificationParser {
    private val amountPattern = Regex(
        "(?i)(?:₹\\s*|\\bINR\\b\\s*|\\bRs\\.?\\s*)(" +
            "[0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?=$|[^0-9,.])"
    )

    private val incomingPattern = Regex(
        "(?iu)(?:\\breceived\\b|\\bcredited\\b|\\bdeposited\\b|" +
            "\\badded to (?:your )?account\\b|प्राप्त हुए|प्राप्त हुई|" +
            "प्राप्त हो गए|प्राप्त हो गई|मिले|मिली|जमा हो गया|जमा हो गई|खाते में जमा)"
    )

    private val disqualifyingPattern = Regex(
        "(?iu)(?:\\bsent\\b|\\bpaid\\b|\\bdebited\\b|\\bpending\\b|" +
            "\\bfailed\\b|\\bfailure\\b|\\bdeclined\\b|\\brejected\\b|" +
            "\\bunsettled\\b|\\bunconfirmed\\b|\\bunsuccessful\\b|" +
            "\\bnot settled\\b|\\bnot completed\\b|\\bnot credited\\b|" +
            "\\breversed?\\b|\\breversal\\b|" +
            "\\brefund(?:ed)?\\b|\\breturned\\b|\\bcancelled\\b|\\bcanceled\\b|" +
            "\\bcashback\\b|\\bcash back\\b|\\breward\\b|\\bbonus\\b|" +
            "\\brecharge\\b|\\bbill\\b|\\bcoupon\\b|\\bgift\\b|" +
            "\\bprocessing\\b|\\binitiated\\b|\\brequested\\b|" +
            "\\bwill be received\\b|\\bto be received\\b|\\bwill receive\\b|" +
            "\\bwill be credited\\b|\\bto be credited\\b|\\bwill be deposited\\b|" +
            "not received|असफल|विफल|लंबित|रद्द|वापस|रिवर्स|भेजा|भेजे|" +
            "प्राप्त करें|प्राप्त करने|जमा करें|जमा करने|मिलेगा|मिलेगी|मिलेंगे|" +
            "प्राप्त नहीं|जमा नहीं|सफल नहीं|पूरा नहीं|अस्वीकृत|कैशबैक|इनाम|" +
            "बोनस|रिचार्ज|रीचार्ज|कूपन|उपहार|" +
            "डेबिट|कटा|निकाला|भुगतान किया)"
    )

    fun parse(packageName: String, title: String?, text: String?): ParsedPayment? {
        val provider = PaymentProvider.fromPackageName(packageName) ?: return null
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val normalized = combined.lowercase(Locale.ROOT)
        if (disqualifyingPattern.containsMatchIn(normalized)) return null
        if (!incomingPattern.containsMatchIn(normalized)) return null

        val amounts = amountPattern.findAll(normalized).toList()
        if (amounts.size != 1) return null

        val amountPaise = parsePaise(amounts.single().groupValues[1]) ?: return null
        if (amountPaise <= 0L) return null

        return ParsedPayment(amountPaise, provider)
    }

    private fun parsePaise(rawAmount: String): Long? {
        val integerPart = rawAmount.substringBefore('.')
        if (integerPart.contains(',') &&
            !WESTERN_GROUPING.matches(integerPart) &&
            !INDIAN_GROUPING.matches(integerPart)
        ) return null

        return try {
            BigDecimal(rawAmount.replace(",", ""))
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private val WESTERN_GROUPING = Regex("[0-9]{1,3}(?:,[0-9]{3})+")
    private val INDIAN_GROUPING = Regex("[0-9]{1,2}(?:,[0-9]{2})*,[0-9]{3}")
}
