package com.soundbox

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private enum class Screen { HOME, SETUP, HISTORY }

    companion object {
        private const val PREFERENCES = "soundbox_preferences"
        private const val UI_LANGUAGE = "ui_language"
        private const val HINDI_LANGUAGE = "hi"
        private const val ENGLISH_LANGUAGE = "en"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(UI_LANGUAGE, HINDI_LANGUAGE) ?: HINDI_LANGUAGE
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private lateinit var page: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen(Screen.HOME)
    }

    private fun showScreen(screen: Screen) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.canvas))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(20), dp(16), dp(20), dp(18))
        }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(page)
        addHeader(page)
        addStageNotice(page)

        when (screen) {
            Screen.HOME -> addHome(page)
            Screen.SETUP -> addSetup(page)
            Screen.HISTORY -> addHistory(page)
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(createNavigation(screen))
        setContentView(root)
    }

    private fun addHeader(parent: LinearLayout) {
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val brand = TextView(this).apply {
            text = "SoundBox"
            textSize = 29f
            setTextColor(getColor(R.color.ink))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            contentDescription = getString(R.string.brand_accessibility)
        }
        val languageButton = Button(this).apply {
            text = getString(R.string.switch_language)
            textSize = 14f
            isAllCaps = false
            minHeight = dp(48)
            setTextColor(getColor(R.color.forest_dark))
            background = rounded(R.color.mint, 18)
            contentDescription = getString(R.string.switch_language_accessibility)
            setOnClickListener { toggleLanguage() }
        }
        heading.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(languageButton)
        parent.addView(heading)
    }

    private fun addStageNotice(parent: LinearLayout) {
        val notice = TextView(this).apply {
            text = getString(R.string.stage_notice)
            textSize = 15f
            setTextColor(getColor(R.color.amber))
            background = rounded(R.color.amber_surface, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
        parent.addView(notice, margin(bottom = 18))
    }

    private fun addHome(parent: LinearLayout) {
        addTitle(parent, getString(R.string.home_title), getString(R.string.home_subtitle))

        val status = card(parent)
        addLabel(status, getString(R.string.listening_status))
        addText(status, getString(R.string.listening_off), 24f, R.color.ink, medium = true)
        addText(status, getString(R.string.setup_next_step), 16f, R.color.muted_ink)
        addAction(status, getString(R.string.view_setup), primary = true) { showScreen(Screen.SETUP) }

        val total = card(parent)
        addLabel(total, getString(R.string.today_total))
        addText(total, getString(R.string.not_available_yet), 25f, R.color.forest_dark, medium = true)
        addText(total, getString(R.string.local_date_note), 15f, R.color.muted_ink)
        addText(total, getString(R.string.recording_unavailable), 15f, R.color.muted_ink)

        val latest = card(parent)
        addLabel(latest, getString(R.string.latest_payment))
        addText(latest, getString(R.string.no_payment_recorded), 19f, R.color.ink, medium = true)
        addText(latest, getString(R.string.history_next_step), 15f, R.color.muted_ink)
        addAction(latest, getString(R.string.view_history), primary = false) { showScreen(Screen.HISTORY) }
    }

    private fun addSetup(parent: LinearLayout) {
        addTitle(parent, getString(R.string.setup_title), getString(R.string.setup_subtitle))

        val language = card(parent)
        addLabel(language, getString(R.string.app_language))
        addText(language, getString(R.string.language_choice_hint), 16f, R.color.muted_ink)
        addLanguageChoice(language, HINDI_LANGUAGE, getString(R.string.hindi))
        addLanguageChoice(language, ENGLISH_LANGUAGE, getString(R.string.english))

        val notification = card(parent)
        addLabel(notification, getString(R.string.notification_access))
        addText(notification, getString(R.string.not_checked), 21f, R.color.ink, medium = true)
        addText(
            notification,
            getString(R.string.notification_access_explanation),
            16f,
            R.color.muted_ink
        )
        addAction(notification, getString(R.string.available_next_milestone), primary = false, enabled = false) {}

        val speech = card(parent)
        addLabel(speech, getString(R.string.hindi_voice))
        addText(speech, getString(R.string.voice_not_checked), 21f, R.color.ink, medium = true)
        addText(
            speech,
            getString(R.string.voice_explanation),
            16f,
            R.color.muted_ink
        )
        addAction(speech, getString(R.string.check_voice), primary = true, enabled = false) {}

        val privacy = card(parent)
        addLabel(privacy, getString(R.string.your_information))
        addText(privacy, getString(R.string.no_read_or_store_yet), 17f, R.color.ink)
        addText(privacy, getString(R.string.permission_purpose_next_step), 15f, R.color.muted_ink)
    }

    private fun addLanguageChoice(parent: LinearLayout, language: String, label: String) {
        val isSelected = getUiLanguage() == language
        val button = Button(this).apply {
            text = if (isSelected) getString(R.string.selected_format, label) else label
            textSize = 16f
            isAllCaps = false
            minHeight = dp(52)
            contentDescription = text
            setTextColor(getColor(if (isSelected) R.color.surface else R.color.forest_dark))
            background = rounded(if (isSelected) R.color.forest else R.color.mint, 14)
            setOnClickListener { setUiLanguage(language) }
        }
        parent.addView(button, margin(top = 4, bottom = 4))
    }

    private fun addHistory(parent: LinearLayout) {
        addTitle(parent, getString(R.string.history_title), getString(R.string.history_subtitle))

        val summary = card(parent)
        addLabel(summary, getString(R.string.retention_period))
        addText(summary, getString(R.string.three_months), 21f, R.color.ink, medium = true)
        addText(summary, getString(R.string.total_ignores_filters), 15f, R.color.muted_ink)

        val filters = card(parent)
        addLabel(filters, getString(R.string.filters))
        addText(filters, getString(R.string.filter_fields), 17f, R.color.ink, medium = true)
        addText(filters, getString(R.string.filters_next_step), 15f, R.color.muted_ink)
        addAction(filters, getString(R.string.reset_filters), primary = false, enabled = false) {}

        val empty = card(parent)
        addLabel(empty, getString(R.string.empty_history_title))
        addText(empty, getString(R.string.empty_history_body), 18f, R.color.ink, medium = true)
        addText(empty, getString(R.string.notification_not_bank_confirmation), 15f, R.color.muted_ink)
        addAction(empty, getString(R.string.clear_history), primary = false, enabled = false) {}
    }

    private fun addTitle(parent: LinearLayout, title: String, subtitle: String) {
        addText(parent, title, 27f, R.color.ink, medium = true, top = 4, bottom = 2)
        addText(parent, subtitle, 16f, R.color.muted_ink, bottom = 18)
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.surface, 20, R.color.line)
            setPadding(dp(18), dp(17), dp(18), dp(17))
        }
        parent.addView(container, margin(bottom = 14))
        return container
    }

    private fun addLabel(parent: LinearLayout, label: String) {
        addText(parent, label, 15f, R.color.forest, medium = true, bottom = 7)
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        medium: Boolean = false,
        top: Int = 0,
        bottom: Int = 8
    ) {
        val text = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(getColor(color))
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = true
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        parent.addView(text, margin(top = top, bottom = bottom))
    }

    private fun addAction(
        parent: LinearLayout,
        label: String,
        primary: Boolean,
        enabled: Boolean = true,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            minHeight = dp(52)
            isEnabled = enabled
            setTextColor(getColor(if (primary) R.color.surface else R.color.forest_dark))
            background = rounded(
                if (primary) R.color.forest else R.color.mint,
                14
            )
            setOnClickListener { action() }
        }
        parent.addView(button, margin(top = 4))
    }

    private fun createNavigation(selected: Screen): LinearLayout {
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(getColor(R.color.surface))
            setPadding(dp(8), dp(7), dp(8), dp(7))
            elevation = dp(8).toFloat()
        }
        val options = listOf(
            Screen.HOME to getString(R.string.nav_home),
            Screen.SETUP to getString(R.string.nav_setup),
            Screen.HISTORY to getString(R.string.nav_history)
        )
        options.forEach { (screen, label) ->
            val button = Button(this).apply {
                text = label
                textSize = 15f
                isAllCaps = false
                minHeight = dp(50)
                setTextColor(getColor(if (selected == screen) R.color.surface else R.color.ink))
                background = rounded(
                    if (selected == screen) R.color.forest else R.color.surface,
                    14
                )
                contentDescription = if (selected == screen) {
                    getString(R.string.selected_format, label)
                } else {
                    label
                }
                setOnClickListener { showScreen(screen) }
            }
            navigation.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        return navigation
    }

    private fun toggleLanguage() {
        val nextLanguage = if (getUiLanguage() == HINDI_LANGUAGE) {
            ENGLISH_LANGUAGE
        } else {
            HINDI_LANGUAGE
        }
        setUiLanguage(nextLanguage)
    }

    private fun getUiLanguage(): String = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        .getString(UI_LANGUAGE, HINDI_LANGUAGE) ?: HINDI_LANGUAGE

    private fun setUiLanguage(language: String) {
        if (getUiLanguage() == language) return
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putString(UI_LANGUAGE, language)
            .apply()
        recreate()
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(getColor(color))
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), getColor(strokeColor))
        }

    private fun margin(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
