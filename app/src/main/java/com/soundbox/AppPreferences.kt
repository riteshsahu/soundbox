package com.soundbox

import android.content.Context

object AppPreferences {
    const val FILE_NAME = "soundbox_preferences"
    const val UI_LANGUAGE = "ui_language"
    const val HINDI_LANGUAGE = "hi"
    const val ENGLISH_LANGUAGE = "en"
    const val ANNOUNCEMENTS_ENABLED = "announcements_enabled"
    const val LOUDNESS_BOOST_ENABLED = "loudness_boost_enabled"
    const val LOUDNESS_BOOST_DB = "loudness_boost_db"
    const val MAX_LOUDNESS_BOOST_DB = 48
    const val LOUDNESS_BOOST_STEP_DB = 6
    const val SPEECH_RATE_PERCENT = "speech_rate_percent"
    const val DEFAULT_SPEECH_RATE_PERCENT = 85

    fun uiLanguage(context: Context): String = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getString(UI_LANGUAGE, ENGLISH_LANGUAGE) ?: ENGLISH_LANGUAGE

    fun setUiLanguage(context: Context, language: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(UI_LANGUAGE, language)
            .apply()
    }

    fun announcementsEnabled(context: Context): Boolean = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getBoolean(ANNOUNCEMENTS_ENABLED, false)

    fun setAnnouncementsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ANNOUNCEMENTS_ENABLED, enabled)
            .apply()
    }

    fun loudnessBoostEnabled(context: Context): Boolean = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getBoolean(LOUDNESS_BOOST_ENABLED, false)

    fun setLoudnessBoostEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(LOUDNESS_BOOST_ENABLED, enabled)
            .apply()
    }

    fun loudnessBoostDb(context: Context): Int = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getInt(LOUDNESS_BOOST_DB, if (loudnessBoostEnabled(context)) 12 else 0)
        .coerceIn(0, MAX_LOUDNESS_BOOST_DB) / LOUDNESS_BOOST_STEP_DB * LOUDNESS_BOOST_STEP_DB

    fun setLoudnessBoostDb(context: Context, db: Int) {
        val value = (db.coerceIn(0, MAX_LOUDNESS_BOOST_DB) / LOUDNESS_BOOST_STEP_DB) * LOUDNESS_BOOST_STEP_DB
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putInt(LOUDNESS_BOOST_DB, value)
            .putBoolean(LOUDNESS_BOOST_ENABLED, value > 0)
            .apply()
    }

    fun speechRatePercent(context: Context): Int = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getInt(SPEECH_RATE_PERCENT, DEFAULT_SPEECH_RATE_PERCENT)
        .coerceIn(70, 100)

    fun setSpeechRatePercent(context: Context, percent: Int) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putInt(SPEECH_RATE_PERCENT, percent.coerceIn(70, 100))
            .apply()
    }

}
