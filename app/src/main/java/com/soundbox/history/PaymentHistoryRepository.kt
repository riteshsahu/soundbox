package com.soundbox.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.soundbox.notifications.PaymentProvider

class PaymentHistoryRepository(context: Context) {
    private val helper = PaymentHistoryDatabase(context.applicationContext)

    fun recordIfUnique(record: PaymentRecord): Boolean {
        val nowMillis = System.currentTimeMillis()
        if (
            record.amountPaise <= 0L ||
            record.receivedAtMillis <= 0L ||
            record.receivedAtMillis > nowMillis ||
            record.receivedAtMillis < PaymentHistoryRules.retentionCutoff(nowMillis) ||
            PaymentProvider.entries.none { it.displayName == record.sourceApp }
        ) return false

        val database = helper.writableDatabase
        var inserted = false
        database.beginTransaction()
        try {
            prune(database, nowMillis)
            if (!hasRecentDuplicate(database, record)) {
                val values = ContentValues().apply {
                    put(COLUMN_AMOUNT_PAISE, record.amountPaise)
                    put(COLUMN_RECEIVED_AT, record.receivedAtMillis)
                    put(COLUMN_SOURCE_APP, record.sourceApp)
                }
                inserted = database.insertWithOnConflict(
                    TABLE_PAYMENTS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                ) != -1L
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return inserted
    }

    fun todayTotal(nowMillis: Long = System.currentTimeMillis()): Long {
        val database = helper.writableDatabase
        prune(database, nowMillis)
        val bounds = PaymentHistoryRules.localDayBounds(nowMillis)
        database.rawQuery(
            "SELECT COALESCE(SUM($COLUMN_AMOUNT_PAISE), 0) FROM $TABLE_PAYMENTS " +
                "WHERE $COLUMN_RECEIVED_AT >= ? AND $COLUMN_RECEIVED_AT < ?",
            arrayOf(bounds.startInclusiveMillis.toString(), bounds.endExclusiveMillis.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun latestPayment(): PaymentRecord? {
        val database = helper.writableDatabase
        prune(database, System.currentTimeMillis())
        return database.query(
            TABLE_PAYMENTS,
            PAYMENT_COLUMNS,
            null,
            null,
            null,
            null,
            "$COLUMN_RECEIVED_AT DESC",
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toPaymentRecord() else null }
    }

    fun history(filter: PaymentHistoryFilter): List<PaymentRecord> {
        val database = helper.writableDatabase
        prune(database, System.currentTimeMillis())
        val clauses = mutableListOf(
            "$COLUMN_RECEIVED_AT >= ?",
            "$COLUMN_RECEIVED_AT < ?"
        )
        val args = mutableListOf(
            filter.startInclusiveMillis.toString(),
            filter.endExclusiveMillis.toString()
        )
        filter.sourceApp?.let {
            clauses += "$COLUMN_SOURCE_APP = ?"
            args += it
        }
        filter.minimumAmountPaise?.let {
            clauses += "$COLUMN_AMOUNT_PAISE >= ?"
            args += it.toString()
        }
        filter.maximumAmountPaise?.let {
            clauses += "$COLUMN_AMOUNT_PAISE <= ?"
            args += it.toString()
        }

        return database.query(
            TABLE_PAYMENTS,
            PAYMENT_COLUMNS,
            clauses.joinToString(" AND "),
            args.toTypedArray(),
            null,
            null,
            "$COLUMN_RECEIVED_AT DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toPaymentRecord())
            }
        }
    }

    fun clearHistory() {
        helper.writableDatabase.delete(TABLE_PAYMENTS, null, null)
    }

    fun close() = helper.close()

    private fun hasRecentDuplicate(database: SQLiteDatabase, record: PaymentRecord): Boolean {
        val earliest = (record.receivedAtMillis - PaymentHistoryRules.DEDUPLICATION_WINDOW_MILLIS)
            .coerceAtLeast(0L)
        val latest = record.receivedAtMillis + PaymentHistoryRules.DEDUPLICATION_WINDOW_MILLIS
        return database.query(
            TABLE_PAYMENTS,
            arrayOf(COLUMN_AMOUNT_PAISE),
            "$COLUMN_AMOUNT_PAISE = ? AND $COLUMN_SOURCE_APP = ? AND " +
                "$COLUMN_RECEIVED_AT BETWEEN ? AND ?",
            arrayOf(
                record.amountPaise.toString(),
                record.sourceApp,
                earliest.toString(),
                latest.toString()
            ),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }
    }

    private fun prune(database: SQLiteDatabase, nowMillis: Long) {
        database.delete(
            TABLE_PAYMENTS,
            "$COLUMN_RECEIVED_AT < ?",
            arrayOf(PaymentHistoryRules.retentionCutoff(nowMillis).toString())
        )
    }

    private fun android.database.Cursor.toPaymentRecord(): PaymentRecord = PaymentRecord(
        amountPaise = getLong(getColumnIndexOrThrow(COLUMN_AMOUNT_PAISE)),
        receivedAtMillis = getLong(getColumnIndexOrThrow(COLUMN_RECEIVED_AT)),
        sourceApp = getString(getColumnIndexOrThrow(COLUMN_SOURCE_APP))
    )

    private class PaymentHistoryDatabase(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE $TABLE_PAYMENTS (" +
                    "$COLUMN_AMOUNT_PAISE INTEGER NOT NULL CHECK ($COLUMN_AMOUNT_PAISE > 0), " +
                    "$COLUMN_RECEIVED_AT INTEGER NOT NULL CHECK ($COLUMN_RECEIVED_AT > 0), " +
                    "$COLUMN_SOURCE_APP TEXT NOT NULL CHECK ($COLUMN_SOURCE_APP IN ('Google Pay', 'PhonePe', 'Paytm')), " +
                    "PRIMARY KEY ($COLUMN_AMOUNT_PAISE, $COLUMN_RECEIVED_AT, $COLUMN_SOURCE_APP)" +
                    ") WITHOUT ROWID"
            )
            database.execSQL(
                "CREATE INDEX payments_by_received_at ON $TABLE_PAYMENTS ($COLUMN_RECEIVED_AT DESC)"
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "soundbox_payments.db"
        const val DATABASE_VERSION = 1
        const val TABLE_PAYMENTS = "received_payments"
        const val COLUMN_AMOUNT_PAISE = "amount_paise"
        const val COLUMN_RECEIVED_AT = "received_at_millis"
        const val COLUMN_SOURCE_APP = "source_app"
        val PAYMENT_COLUMNS = arrayOf(
            COLUMN_AMOUNT_PAISE,
            COLUMN_RECEIVED_AT,
            COLUMN_SOURCE_APP
        )
    }
}
