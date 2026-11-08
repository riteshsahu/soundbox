package com.soundbox

import android.content.Context

object AppPreferences {
    const val FILE_NAME = "soundbox_preferences"
    const val UI_LANGUAGE = "ui_language"
    const val HINDI_LANGUAGE = "hi"
    const val ENGLISH_LANGUAGE = "en"
    const val ANNOUNCEMENTS_ENABLED = "announcements_enabled"
    const val LOUDNESS_BOOST_ENABLED = "loudness_boost_enabled"

    fun uiLanguage(context: Context): String = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getString(UI_LANGUAGE, HINDI_LANGUAGE) ?: HINDI_LANGUAGE

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
}
