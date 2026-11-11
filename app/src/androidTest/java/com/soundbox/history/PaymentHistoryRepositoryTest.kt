package com.soundbox.history

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentHistoryRepositoryTest {
    private lateinit var repository: PaymentHistoryRepository
    private lateinit var appContext: android.content.Context

    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.deleteDatabase(DATABASE_NAME)
        repository = PaymentHistoryRepository(appContext)
    }

    @After
    fun tearDown() {
        repository.close()
        appContext.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun testRecordSuppressesLikelyDuplicateAndClearRemovesRows() {
        val now = System.currentTimeMillis()
        val payment = PaymentRecord(50_000L, now - 1_000L, "Google Pay")

        assertTrue(repository.recordIfUnique(payment))
        assertFalse(repository.recordIfUnique(payment))
        assertFalse(repository.recordIfUnique(payment.copy(receivedAtMillis = now - 500L)))
        assertEquals(listOf(payment), repository.history(PaymentHistoryFilter(now - 5_000L, now + 1)))

        repository.clearHistory()

        assertTrue(repository.history(PaymentHistoryFilter(now - 5_000L, now + 1)).isEmpty())
    }

    @Test
    fun testHistoryReadPrunesRowsOlderThanThreeMonths() {
        val now = System.currentTimeMillis()
        val cutoff = PaymentHistoryRules.retentionCutoff(now)
        val retainedTimestamp = cutoff + 60_000L
        repository.latestPayment() // Create the schema before inserting controlled test rows.
        val database = appContext.openOrCreateDatabase(DATABASE_NAME, 0, null)
        try {
            insert(database, 10_000L, cutoff - 1L, "Paytm")
            insert(database, 20_000L, retainedTimestamp, "PhonePe")
        } finally {
            database.close()
        }

        val retained = repository.history(PaymentHistoryFilter(cutoff - 1_000L, now + 1_000L))

        assertEquals(listOf(PaymentRecord(20_000L, retainedTimestamp, "PhonePe")), retained)
    }

    @Test
    fun testTodayTotalUsesDeviceLocalDayAndUniqueInsertedRows() {
        val now = System.currentTimeMillis()
        val first = PaymentRecord(50_000L, now - 2_000L, "Google Pay")
        val second = PaymentRecord(25_000L, now - 1_000L, "PhonePe")

        assertTrue(repository.recordIfUnique(first))
        assertTrue(repository.recordIfUnique(second))
        assertFalse(repository.recordIfUnique(first))

        assertEquals(75_000L, repository.todayTotal(now))
    }

    private fun insert(database: SQLiteDatabase, amountPaise: Long, receivedAtMillis: Long, sourceApp: String) {
        val values = ContentValues().apply {
            put("amount_paise", amountPaise)
            put("received_at_millis", receivedAtMillis)
            put("source_app", sourceApp)
        }
        assertTrue(database.insert("received_payments", null, values) != -1L)
    }

    private companion object {
        const val DATABASE_NAME = "soundbox_payments.db"
    }
}
