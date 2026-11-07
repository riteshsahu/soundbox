package com.soundbox.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HindiPaymentPhraseTest {
    @Test
    fun formatsCommonAmountInHindiOnly() {
        assertEquals("पाँच सौ रुपये प्राप्त हुए", HindiPaymentPhrase.received(50_000L))
    }

    @Test
    fun includesPaiseWhenPresent() {
        assertEquals("एक रुपया पचास पैसे प्राप्त हुए", HindiPaymentPhrase.received(150L))
    }

    @Test
    fun rejectsInvalidAmount() {
        assertNull(HindiPaymentPhrase.received(0L))
    }
}
