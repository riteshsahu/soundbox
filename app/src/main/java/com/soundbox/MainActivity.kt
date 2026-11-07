package com.soundbox

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import com.soundbox.notifications.NotificationAccess
import com.soundbox.speech.HindiSpeechController
import com.soundbox.speech.SpeechActions

class MainActivity : Activity() {
    private enum class Screen { HOME, SETUP, HISTORY }

    private var currentScreen = Screen.HOME
    private var hindiVoiceReady = false
    private var ttsCheckComplete = false
    private var voiceSettingsOpened = false
    private var speechControllerGeneration = 0
    private var speechController: HindiSpeechController? = null

    override fun attachBaseContext(newBase: Context) {
        val language = AppPreferences.uiLanguage(newBase)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private lateinit var page: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeSpeechController()
        showScreen(Screen.HOME)
    }

    override fun onResume() {
        super.onResume()
        if (voiceSettingsOpened) {
            voiceSettingsOpened = false
            initializeSpeechController(refreshListenerService = true)
        }
        if (::page.isInitialized) showScreen(currentScreen)
    }

    override fun onDestroy() {
        speechControllerGeneration++
        speechController?.shutdown()
        speechController = null
        super.onDestroy()
    }

    private fun showScreen(screen: Screen) {
        currentScreen = screen
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
        val listeningReady = NotificationAccess.isEnabled(this) &&
            AppPreferences.announcementsEnabled(this) && hindiVoiceReady
        addText(
            status,
            getString(if (listeningReady) R.string.listening_active else R.string.listening_needs_setup),
            24f,
            R.color.ink,
            medium = true
        )
        addText(
            status,
            getString(if (NotificationAccess.isEnabled(this)) R.string.listener_ready else R.string.listener_not_ready),
            16f,
            R.color.muted_ink
        )
        addText(
            status,
            getString(if (hindiVoiceReady) R.string.offline_voice_ready else R.string.offline_voice_missing),
            16f,
            R.color.muted_ink
        )
        addText(
            status,
            getString(if (AppPreferences.announcementsEnabled(this)) R.string.announcements_enabled else R.string.announcements_disabled),
            16f,
            R.color.muted_ink
        )
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
        addLanguageChoice(language, AppPreferences.HINDI_LANGUAGE, getString(R.string.hindi))
        addLanguageChoice(language, AppPreferences.ENGLISH_LANGUAGE, getString(R.string.english))

        val notification = card(parent)
        addLabel(notification, getString(R.string.notification_access))
        val accessEnabled = NotificationAccess.isEnabled(this)
        addText(
            notification,
            getString(if (accessEnabled) R.string.listener_ready else R.string.listener_not_ready),
            21f,
            R.color.ink,
            medium = true
        )
        addText(
            notification,
            getString(R.string.notification_access_explanation),
            16f,
            R.color.muted_ink
        )
        addText(notification, getString(R.string.notification_access_scope), 15f, R.color.muted_ink)
        addAction(notification, getString(R.string.enable_notification_access), primary = true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val speech = card(parent)
        addLabel(speech, getString(R.string.hindi_voice))
        addText(
            speech,
            getString(
                when {
                    hindiVoiceReady -> R.string.offline_voice_ready
                    !ttsCheckComplete -> R.string.voice_checking
                    else -> R.string.offline_voice_missing
                }
            ),
            21f,
            R.color.ink,
            medium = true
        )
        addText(
            speech,
            getString(R.string.voice_explanation),
            16f,
            R.color.muted_ink
        )
        addAction(speech, getString(R.string.test_speech), primary = true, enabled = hindiVoiceReady) {
            speechController?.speakHindi(getString(R.string.test_speech_phrase))
        }
        if (ttsCheckComplete && !hindiVoiceReady) {
            addAction(speech, getString(R.string.open_voice_settings), primary = false) {
                openHindiVoiceSettings()
            }
        }

        val announcements = card(parent)
        addLabel(announcements, getString(R.string.announcement_setting))
        addAnnouncementsSwitch(announcements)

        val privacy = card(parent)
        addLabel(privacy, getString(R.string.your_information))
        addText(privacy, getString(R.string.allowlist_privacy), 17f, R.color.ink)
        addText(privacy, getString(R.string.local_only_no_notification_text), 15f, R.color.muted_ink)
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

    private fun addAnnouncementsSwitch(parent: LinearLayout) {
        val announcementsSwitch = Switch(this).apply {
            text = getString(R.string.announcements_switch_label)
            textSize = 17f
            minHeight = dp(52)
            isChecked = AppPreferences.announcementsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                AppPreferences.setAnnouncementsEnabled(this@MainActivity, enabled)
                if (!enabled) {
                    sendBroadcast(
                        Intent(SpeechActions.STOP_QUEUED_ANNOUNCEMENTS).setPackage(packageName)
                    )
                }
                showScreen(currentScreen)
            }
        }
        parent.addView(announcementsSwitch, margin(top = 4))
        addText(
            parent,
            getString(if (AppPreferences.announcementsEnabled(this)) R.string.announcements_enabled else R.string.announcements_disabled),
            15f,
            R.color.muted_ink
        )
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
        val nextLanguage = if (getUiLanguage() == AppPreferences.HINDI_LANGUAGE) {
            AppPreferences.ENGLISH_LANGUAGE
        } else {
            AppPreferences.HINDI_LANGUAGE
        }
        setUiLanguage(nextLanguage)
    }

    private fun getUiLanguage(): String = AppPreferences.uiLanguage(this)

    private fun setUiLanguage(language: String) {
        if (getUiLanguage() == language) return
        AppPreferences.setUiLanguage(this, language)
        recreate()
    }

    private fun openHindiVoiceSettings() {
        voiceSettingsOpened = true
        try {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        } catch (_: ActivityNotFoundException) {
            voiceSettingsOpened = false
            Toast.makeText(this, R.string.voice_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeSpeechController(refreshListenerService: Boolean = false) {
        speechControllerGeneration++
        val generation = speechControllerGeneration
        ttsCheckComplete = false
        hindiVoiceReady = false
        speechController?.shutdown()
        speechController = HindiSpeechController(this) { ready ->
            if (generation != speechControllerGeneration) return@HindiSpeechController
            hindiVoiceReady = ready
            ttsCheckComplete = true
            if (ready && refreshListenerService) {
                sendBroadcast(Intent(SpeechActions.REFRESH_LOCAL_HINDI_VOICE).setPackage(packageName))
            }
            if (!isFinishing) showScreen(currentScreen)
        }
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
