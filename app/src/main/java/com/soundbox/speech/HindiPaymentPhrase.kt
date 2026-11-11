package com.soundbox.speech

import com.soundbox.notifications.PaymentProvider

object HindiPaymentPhrase {
    private val words = arrayOf(
        "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
        "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
        "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
        "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
        "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
        "पचास", "इक्यावन", "बावन", "तिरपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
        "साठ", "इकसठ", "बासठ", "तिरसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
        "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उनासी",
        "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
        "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे"
    )

    fun received(amountPaise: Long, provider: PaymentProvider): String? {
        if (amountPaise <= 0L) return null
        val rupees = amountPaise / 100
        val paise = (amountPaise % 100).toInt()
        if (rupees > 99_99_99_999L) return null

        val amount = buildString {
            append(numberToWords(rupees))
            append(if (rupees == 1L) " रुपया" else " रुपये")
            if (paise > 0) {
                append(" ")
                append(numberToWords(paise.toLong()))
                append(" पैसे")
            }
        }
        val appName = when (provider) {
            PaymentProvider.GOOGLE_PAY -> "गूगल पे"
            PaymentProvider.PHONEPE -> "फोनपे"
            PaymentProvider.PAYTM -> "पेटीएम"
        }
        return "$appName पर $amount प्राप्त हुए"
    }

    private fun numberToWords(value: Long): String {
        if (value == 0L) return words[0]
        var remaining = value
        val parts = mutableListOf<String>()

        fun addGroup(divisor: Long, unit: String) {
            val group = remaining / divisor
            if (group > 0) {
                parts += "${underThousand(group)} $unit"
                remaining %= divisor
            }
        }

        addGroup(10_000_000L, "करोड़")
        addGroup(100_000L, "लाख")
        addGroup(1_000L, "हज़ार")
        if (remaining > 0) parts += underThousand(remaining)
        return parts.joinToString(" ")
    }

    private fun underThousand(value: Long): String {
        val parts = mutableListOf<String>()
        val hundreds = value / 100
        if (hundreds > 0) {
            parts += "${words[hundreds.toInt()]} सौ"
        }
        val remainder = (value % 100).toInt()
        if (remainder > 0) parts += words[remainder]
        return parts.joinToString(" ")
    }
}
