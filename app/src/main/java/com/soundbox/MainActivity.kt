package com.soundbox

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.text.TextWatcher
import com.soundbox.history.PaymentHistoryFilter
import com.soundbox.history.PaymentHistoryRepository
import com.soundbox.history.PaymentHistoryRules
import com.soundbox.history.PaymentRecord
import com.soundbox.notifications.NotificationAccess
import com.soundbox.notifications.PaymentProvider
import com.soundbox.speech.HindiSpeechController
import com.soundbox.speech.HistoryActions
import com.soundbox.speech.SpeechActions
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.text.NumberFormat
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : Activity() {
    private enum class Screen { HOME, HISTORY, SETTINGS, PRIVACY }

    private var currentScreen = Screen.HOME
    private var hindiVoiceReady = false
    private var ttsCheckComplete = false
    private var boostAvailable: Boolean? = null
    private var voiceSettingsOpened = false
    private var speechControllerGeneration = 0
    private var speechController: HindiSpeechController? = null
    private var historyRepository: PaymentHistoryRepository? = null
    private var historyStartMillis = 0L
    private var historyEndDateMillis = 0L
    private var historySourceApp: String? = null
    private var minimumAmountText = ""
    private var maximumAmountText = ""
    private var minimumAmountPaise: Long? = null
    private var maximumAmountPaise: Long? = null
    private var historyFilterError: String? = null
    private var historyFiltersExpanded = false
    private var historyStorageError = false
    private var paymentEventsReceiverRegistered = false
    private var samplePreviewEnabled = false

    private val paymentEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (::page.isInitialized) showScreen(currentScreen)
        }
    }

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
        currentScreen = savedInstanceState?.getString(STATE_SCREEN)
            ?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
            ?: Screen.HOME
        historyRepository = PaymentHistoryRepository(this)
        resetHistoryFilters()
        initializeSpeechController()
        showScreen(currentScreen)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_SCREEN, currentScreen.name)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter(HistoryActions.PAYMENTS_CHANGED).apply {
            addAction(NotificationAccess.ACTION_LISTENER_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(paymentEventsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(paymentEventsReceiver, filter)
        }
        paymentEventsReceiverRegistered = true
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
        historyRepository?.close()
        historyRepository = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.HISTORY, Screen.SETTINGS -> showScreen(Screen.HOME)
            Screen.PRIVACY -> showScreen(Screen.SETTINGS)
            Screen.HOME -> super.onBackPressed()
        }
    }

    override fun onStop() {
        if (paymentEventsReceiverRegistered) {
            unregisterReceiver(paymentEventsReceiver)
            paymentEventsReceiverRegistered = false
        }
        super.onStop()
    }

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        historyStorageError = false
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
        when (screen) {
            Screen.HOME -> addHome(page)
            Screen.HISTORY -> addHistory(page)
            Screen.SETTINGS -> addSettings(page)
            Screen.PRIVACY -> addPrivacy(page)
        }

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addHomeHeader(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        val spacer = View(this)
        row.addView(spacer, LinearLayout.LayoutParams(0, dp(46), 1f))
        addIconButton(row, R.drawable.ic_nav_settings, getString(R.string.open_settings), R.color.lime) {
            showScreen(Screen.SETTINGS)
        }
        parent.addView(row)
    }

    private fun addPageHeader(
        parent: LinearLayout,
        eyebrow: String,
        title: String,
        backScreen: Screen,
        trailingIcon: Int? = null,
        trailingDescription: String? = null,
        trailingAction: (() -> Unit)? = null
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        val backIcon = iconImage(R.drawable.ic_arrow_back, R.color.ink)
        val back = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.surface, 13, R.color.line)
            minimumWidth = dp(44)
            minimumHeight = dp(44)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.back_accessibility)
            setOnClickListener { showScreen(backScreen) }
            addView(backIcon, LinearLayout.LayoutParams(dp(21), dp(21)))
        }
        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })

        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(titleGroup, eyebrow, 11f, R.color.forest, medium = true, bottom = 1)
        addText(titleGroup, title, 23f, R.color.ink, medium = true, bottom = 0)
        row.addView(titleGroup, LinearLayout.LayoutParams(0, -2, 1f))
        if (trailingIcon != null && trailingDescription != null && trailingAction != null) {
            addIconButton(row, trailingIcon, trailingDescription, R.color.surface, trailingAction)
        }
        parent.addView(row)
    }

    private fun addIconButton(
        parent: LinearLayout,
        icon: Int,
        description: String,
        backgroundColor: Int,
        action: () -> Unit
    ) {
        val button = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(backgroundColor, 16, if (backgroundColor == R.color.surface) R.color.line else null)
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { action() }
            addView(iconImage(icon, R.color.forest_dark), LinearLayout.LayoutParams(dp(22), dp(22)))
        }
        parent.addView(button)
    }

    private fun iconImage(resource: Int, tint: Int): ImageView = ImageView(this).apply {
        setImageResource(resource)
        setColorFilter(getColor(tint))
        contentDescription = null
    }

    private fun addHome(parent: LinearLayout) {
        addHomeHeader(parent)
        if (samplePreviewEnabled && BuildConfig.DEBUG) addSamplePreviewNotice(parent)

        val notificationAccessEnabled = NotificationAccess.isEnabled(this)
        val listenerConnected = notificationAccessEnabled && NotificationAccess.isListenerConnected()
        if (!notificationAccessEnabled) addNotificationAccessCard(parent)
        val announcementsEnabled = AppPreferences.announcementsEnabled(this)
        val announcementReady = listenerConnected && announcementsEnabled &&
            ttsCheckComplete && hindiVoiceReady

        val total = FrameLayout(this).apply {
            background = rounded(if (announcementReady) R.color.forest_dark else R.color.paused_hero, 27)
            clipToOutline = true
        }
        val heroRing = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(24), getColor(R.color.lime))
            }
            alpha = 0.08f
            translationX = dp(70).toFloat()
        }
        total.addView(
            heroRing,
            FrameLayout.LayoutParams(dp(180), dp(180), Gravity.END or Gravity.TOP).apply {
                topMargin = dp(10)
            }
        )
        val totalContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(19), dp(20), dp(16))
        }
        total.addView(totalContent, FrameLayout.LayoutParams(-1, -2))
        parent.addView(total, margin(bottom = 14))
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            background = rounded(if (announcementReady) R.color.lime else R.color.boost_orange, 20)
        }
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(8) })
        val statusText = TextView(this).apply {
            text = getString(
                when {
                    !notificationAccessEnabled -> R.string.listening_needs_setup
                    !listenerConnected -> R.string.listener_connecting
                    !announcementsEnabled -> R.string.announcements_disabled
                    !ttsCheckComplete -> R.string.voice_checking
                    !hindiVoiceReady -> R.string.home_voice_missing
                    else -> R.string.announcements_enabled
                }
            )
            textSize = 14f
            setTextColor(getColor(if (announcementReady) R.color.lime else R.color.hero_muted))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        statusRow.addView(statusText)
        totalContent.addView(statusRow, margin(bottom = 20))
        addText(totalContent, getString(R.string.today_total), 15f, R.color.hero_muted, medium = true, bottom = 2)
        val now = System.currentTimeMillis()
        val todayBounds = PaymentHistoryRules.localDayBounds(now)
        val previewRecords = if (samplePreviewEnabled && BuildConfig.DEBUG) samplePayments(now) else null
        val todayRecords = previewRecords?.let { filterSamplePayments(
            it,
            PaymentHistoryFilter(todayBounds.startInclusiveMillis, todayBounds.endExclusiveMillis)
        ) }
        val todayTotalPaise = todayRecords?.sumOf { it.amountPaise }
            ?: readHistory(0L) { historyRepository?.todayTotal() ?: 0L }
        val todayPaymentCount = todayRecords?.size ?: readHistory(0) {
            historyRepository?.history(
                PaymentHistoryFilter(todayBounds.startInclusiveMillis, todayBounds.endExclusiveMillis)
            )?.size ?: 0
        }
        if (historyStorageError) {
            addText(totalContent, getString(R.string.history_storage_error), 17f, R.color.hero_error)
        } else {
            addText(totalContent, formatAmount(todayTotalPaise), 40f, R.color.surface, medium = true, bottom = 2)
        }
        val meta = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val paymentCountText = makeTextView(getString(R.string.today_payment_count, todayPaymentCount), 14f, R.color.hero_muted)
        meta.addView(paymentCountText, LinearLayout.LayoutParams(-2, -2))
        meta.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val localDateText = makeTextView(getString(R.string.today_date_label, formatDate(now)), 13f, R.color.hero_muted)
        meta.addView(localDateText, LinearLayout.LayoutParams(-2, -2))
        totalContent.addView(meta)
        addSoundWave(total)

        val latestSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(latestSection, getString(R.string.latest_payments), 19f, R.color.ink, medium = true, bottom = 8)
        val retainedStart = PaymentHistoryRules.localDayBounds(
            PaymentHistoryRules.retentionCutoff(System.currentTimeMillis())
        ).startInclusiveMillis
        val recentPayments = previewRecords?.let { filterSamplePayments(
            it,
            PaymentHistoryFilter(retainedStart, PaymentHistoryRules.localDayBounds(now).endExclusiveMillis)
        ).take(3) } ?: readHistory(emptyList()) {
            historyRepository?.history(
                PaymentHistoryFilter(retainedStart, PaymentHistoryRules.localDayBounds(now).endExclusiveMillis)
            )?.take(3) ?: emptyList()
        }
        if (historyStorageError) {
            addText(latestSection, getString(R.string.history_storage_error), 17f, R.color.amber)
        } else if (recentPayments.isEmpty()) {
            addEmptyPaymentState(
                latestSection,
                getString(R.string.no_payment_recorded),
                getString(R.string.empty_history_body),
                compact = true
            )
        } else {
            val transactionList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(R.color.surface, 20, R.color.line)
                clipToOutline = true
            }
            recentPayments.forEachIndexed { index, record ->
                addPaymentRecord(transactionList, record, compact = true, listRow = true)
                if (index < recentPayments.lastIndex) {
                    View(this).apply { setBackgroundColor(getColor(R.color.segment_surface)) }
                        .also { transactionList.addView(it, LinearLayout.LayoutParams(-1, dp(1)).apply {
                            marginStart = dp(12)
                            marginEnd = dp(12)
                        }) }
                }
            }
            latestSection.addView(transactionList)
        }
        addHistoryButton(latestSection)
        parent.addView(latestSection)
    }

    private fun addNotificationAccessCard(parent: LinearLayout) {
        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(R.color.access_surface, 18, R.color.access_line)
            setPadding(dp(13), dp(12), dp(13), dp(12))
        }
        notice.addView(iconImage(R.drawable.ic_bell, R.color.access_ink), LinearLayout.LayoutParams(dp(27), dp(27)).apply {
            marginEnd = dp(10)
        })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(copy, getString(R.string.access_step_title), 15f, R.color.ink, medium = true, bottom = 1)
        addText(copy, getString(R.string.access_step_body), 13f, R.color.muted_ink, bottom = 0)
        notice.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val enable = TextView(this).apply {
            text = getString(R.string.enable_short)
            textSize = 14f
            setTextColor(getColor(R.color.surface))
            gravity = Gravity.CENTER
            background = rounded(R.color.access_ink, 12)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.enable_notification_access)
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        notice.addView(enable, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        parent.addView(notice, margin(bottom = 13))
    }

    private fun addSoundWave(parent: FrameLayout) {
        val wave = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = null
            alpha = 0.18f
        }
        listOf(10, 20, 13, 30, 22, 39, 25, 48, 31, 18, 27, 15, 20, 11).forEach { height ->
            val bar = View(this).apply { background = rounded(R.color.lime, 8) }
            wave.addView(bar, LinearLayout.LayoutParams(dp(3), dp(height)).apply { marginEnd = dp(3) })
        }
        parent.addView(
            wave,
            FrameLayout.LayoutParams(-2, dp(52), Gravity.END or Gravity.BOTTOM).apply {
                marginEnd = dp(18)
                bottomMargin = dp(56)
            }
        )
    }

    private fun addHistoryButton(parent: LinearLayout) {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(R.color.setting_teal_surface, 14, R.color.forest)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.view_full_history)
            setOnClickListener { showScreen(Screen.HISTORY) }
        }
        val label = makeTextView(getString(R.string.view_full_history), 15f, R.color.forest, medium = true)
        button.addView(label)
        button.addView(
            iconImage(R.drawable.ic_chevron, R.color.forest),
            LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(4) }
        )
        parent.addView(button, margin(top = 10))
    }

    private fun addSamplePreviewNotice(parent: LinearLayout) {
        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(R.color.access_surface, 16, R.color.access_line)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        notice.addView(iconImage(R.drawable.ic_info, R.color.access_ink), LinearLayout.LayoutParams(dp(22), dp(22)).apply {
            marginEnd = dp(9)
        })
        notice.addView(
            makeTextView(getString(R.string.sample_preview_notice), 14f, R.color.ink, medium = true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val exit = TextView(this).apply {
            text = getString(R.string.exit_sample_preview)
            textSize = 14f
            setTextColor(getColor(R.color.access_ink))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.exit_sample_preview)
            setOnClickListener {
                samplePreviewEnabled = false
                showScreen(currentScreen)
            }
        }
        notice.addView(exit, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        parent.addView(notice, margin(bottom = 13))
    }

    private fun addSettings(parent: LinearLayout) {
        addPageHeader(parent, getString(R.string.settings_eyebrow), getString(R.string.settings_title), Screen.HOME)

        val announcements = card(parent)
        addToggleSetting(
            announcements,
            R.drawable.ic_speaker,
            getString(R.string.announcement_setting),
            getString(R.string.announcement_description)
        )
        addAction(announcements, getString(R.string.test_speech), primary = false, enabled = hindiVoiceReady) {
            speechController?.speakHindi(getString(R.string.test_speech_phrase))
        }
        addInlineInfo(announcements, getString(R.string.announcements_hindi_only))
        addText(announcements, getString(R.string.announcement_off_history_on), 13f, R.color.muted_ink, top = 5, bottom = 0)

        addSectionLabel(parent, getString(R.string.sound_language_section))
        val loudness = card(parent)
        addSettingHeading(loudness, R.drawable.ic_volume, R.color.boost_surface,
            getString(R.string.loudness_boost), getString(R.string.boost_short_description))
        addLoudnessSegment(loudness)
        addText(
            loudness,
            getString(
                when {
                    !AppPreferences.loudnessBoostEnabled(this) -> R.string.boost_effect_normal
                    boostAvailable == true -> R.string.boost_effect_available
                    boostAvailable == false -> R.string.boost_effect_unavailable
                    else -> R.string.boost_effect_not_checked
                }
            ),
            14f,
            R.color.muted_ink,
            top = 8
        )
        addText(loudness, getString(R.string.boost_device_note), 13f, R.color.muted_ink, top = 4)
        addAction(loudness, getString(R.string.test_selected_sound), primary = false, enabled = hindiVoiceReady) {
            speechController?.speakHindi(getString(R.string.test_speech_phrase))
        }

        val language = card(parent)
        addSettingHeading(language, R.drawable.ic_language, R.color.language_surface,
            getString(R.string.app_language), getString(R.string.language_choice_hint))
        addLanguageChoice(language, AppPreferences.ENGLISH_LANGUAGE, getString(R.string.english))
        addLanguageChoice(language, AppPreferences.HINDI_LANGUAGE, getString(R.string.hindi))

        addSectionLabel(parent, getString(R.string.system_support_section))
        val support = card(parent)
        addSupportRow(
            support,
            R.drawable.ic_bell,
            getString(R.string.notification_access),
            getString(notificationListenerStatusString()),
            if (notificationListenerStatusString() == R.string.listener_connected) {
                R.color.status_green
            } else {
                R.color.status_amber
            }
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        addSupportRow(
            support,
            R.drawable.ic_check,
            getString(R.string.hindi_voice),
            getString(
                when {
                    hindiVoiceReady -> R.string.offline_voice_ready
                    !ttsCheckComplete -> R.string.voice_checking
                    else -> R.string.offline_voice_missing
                }
            ),
            if (hindiVoiceReady) R.color.status_green else R.color.status_amber
        ) {
            if (hindiVoiceReady) speechController?.speakHindi(getString(R.string.test_speech_phrase))
            else openHindiVoiceSettings()
        }
        addSupportRow(
            support,
            R.drawable.ic_shield,
            getString(R.string.privacy_data),
            getString(R.string.privacy_data_summary),
            R.color.status_slate
        ) { showScreen(Screen.PRIVACY) }
        if (BuildConfig.DEBUG) {
            addSupportRow(
                support,
                R.drawable.ic_nav_history,
                getString(R.string.sample_preview_setting),
                getString(R.string.sample_preview_setting_summary),
                R.color.status_slate
            ) {
                samplePreviewEnabled = true
                showScreen(Screen.HOME)
            }
        }
        addText(parent, getString(R.string.app_version), 13f, R.color.muted_ink, top = 8, bottom = 20)
    }

    private fun notificationListenerStatusString(): Int = when {
        !NotificationAccess.isEnabled(this) -> R.string.listener_not_ready
        NotificationAccess.isListenerConnected() -> R.string.listener_connected
        else -> R.string.listener_connecting
    }

    private fun addToggleSetting(parent: LinearLayout, icon: Int, title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addSettingIcon(row, icon, R.color.setting_teal)
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(copy, title, 17f, R.color.ink, medium = true, bottom = 2)
        addText(copy, subtitle, 14f, R.color.muted_ink, bottom = 0)
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        val toggle = Switch(this).apply {
            contentDescription = title
            minHeight = dp(52)
            isChecked = AppPreferences.announcementsEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                AppPreferences.setAnnouncementsEnabled(this@MainActivity, enabled)
                if (!enabled) sendBroadcast(Intent(SpeechActions.STOP_QUEUED_ANNOUNCEMENTS).setPackage(packageName))
                showScreen(currentScreen)
            }
        }
        row.addView(toggle)
        parent.addView(row)
    }

    private fun addSettingHeading(parent: LinearLayout, icon: Int, iconBackground: Int, title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addSettingIcon(row, icon, iconBackground)
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(copy, title, 17f, R.color.ink, medium = true, bottom = 2)
        addText(copy, subtitle, 14f, R.color.muted_ink, bottom = 0)
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        parent.addView(row, margin(bottom = 12))
    }

    private fun addSettingIcon(parent: LinearLayout, icon: Int, color: Int) {
        val (backgroundColor, tintColor) = when (color) {
            R.color.setting_teal -> R.color.setting_teal_surface to R.color.setting_teal
            R.color.boost_surface -> R.color.boost_surface to R.color.access_ink
            R.color.language_surface -> R.color.language_surface to R.color.setting_violet
            R.color.status_green -> R.color.status_green_surface to R.color.status_green
            R.color.status_amber -> R.color.status_amber_surface to R.color.status_amber
            R.color.status_coral -> R.color.status_coral_surface to R.color.status_coral
            R.color.status_slate -> R.color.status_slate_surface to R.color.status_slate
            R.color.access_surface -> R.color.access_surface to R.color.access_ink
            else -> color to R.color.forest
        }
        val tile = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(backgroundColor, 14)
        }
        tile.addView(iconImage(icon, tintColor), LinearLayout.LayoutParams(dp(22), dp(22)))
        parent.addView(tile, LinearLayout.LayoutParams(dp(44), dp(44)))
    }

    private fun addLoudnessSegment(parent: LinearLayout) {
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.segment_surface, 13)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        fun choice(enabled: Boolean, title: String, description: String) {
            val selected = AppPreferences.loudnessBoostEnabled(this) == enabled
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(68)
                setPadding(dp(8), dp(8), dp(10), dp(8))
                background = rounded(if (selected) R.color.surface else R.color.segment_surface, 10)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = if (selected) getString(R.string.selected_format, "$title. $description") else "$title. $description"
                setOnClickListener {
                    AppPreferences.setLoudnessBoostEnabled(this@MainActivity, enabled)
                    boostAvailable = speechController?.setBoostEnabled(enabled)
                    sendBroadcast(Intent(SpeechActions.SET_LOUDNESS_BOOST).setPackage(packageName)
                        .putExtra(SpeechActions.EXTRA_BOOST_ENABLED, enabled))
                    showScreen(currentScreen)
                }
            }
            val radio = RadioButton(this).apply {
                isChecked = selected
                isClickable = false
                isFocusable = false
                contentDescription = null
            }
            option.addView(radio, LinearLayout.LayoutParams(dp(48), dp(48)))
            val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            addText(copy, title, 16f, R.color.ink, medium = true, bottom = 1)
            addText(copy, description, 13f, R.color.muted_ink, bottom = 0)
            option.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            group.addView(option, LinearLayout.LayoutParams(-1, -2).apply {
                if (group.childCount > 0) topMargin = dp(4)
            })
        }
        choice(
            false,
            getString(R.string.normal_sound),
            getString(R.string.normal_sound_description)
        )
        choice(
            true,
            getString(R.string.boosted_sound),
            getString(R.string.boosted_sound_description)
        )
        parent.addView(group)
    }

    private fun addLanguageChoice(parent: LinearLayout, language: String, label: String) {
        val selected = getUiLanguage() == language
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(10))
            isClickable = true
            isFocusable = true
            isSelected = selected
            contentDescription = if (selected) getString(R.string.selected_format, label) else label
            setOnClickListener { setUiLanguage(language) }
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(getColor(R.color.ink))
        }
        row.addView(labelView, LinearLayout.LayoutParams(0, -2, 1f))
        val radio = RadioButton(this).apply { isChecked = selected; isClickable = false; contentDescription = null }
        row.addView(radio)
        parent.addView(row)
    }

    private fun addSupportRow(
        parent: LinearLayout,
        icon: Int,
        title: String,
        subtitle: String,
        iconColor: Int,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            setOnClickListener { action() }
        }
        addSettingIcon(row, icon, iconColor)
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(copy, title, 15f, R.color.ink, medium = true, bottom = 1)
        addText(copy, subtitle, 13f, R.color.muted_ink, bottom = 0)
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        row.addView(iconImage(R.drawable.ic_chevron, R.color.muted_ink), LinearLayout.LayoutParams(dp(20), dp(20)))
        parent.addView(row)
        val divider = View(this).apply { setBackgroundColor(getColor(R.color.line)) }
        parent.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(2); bottomMargin = dp(2) })
    }

    private fun addSectionLabel(parent: LinearLayout, text: String) {
        val label = TextView(this).apply {
            this.text = text
            textSize = 12f
            letterSpacing = 0.08f
            setTextColor(getColor(R.color.muted_ink))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(3), dp(7), dp(3), dp(7))
        }
        parent.addView(label, margin(top = 5, bottom = 4))
    }

    private fun addInlineInfo(parent: LinearLayout, text: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(iconImage(R.drawable.ic_info, R.color.muted_ink), LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(6) })
        row.addView(makeTextView(text, 13f, R.color.muted_ink), LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row, margin(top = 9))
    }

    private fun addPrivacy(parent: LinearLayout) {
        addPageHeader(parent, getString(R.string.privacy_eyebrow), getString(R.string.privacy_title), Screen.SETTINGS)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(R.color.forest_dark, 25)
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        parent.addView(hero, margin(bottom = 14))
        val shield = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.lime, 24)
        }
        shield.addView(iconImage(R.drawable.ic_shield, R.color.forest_dark), LinearLayout.LayoutParams(dp(45), dp(45)))
        hero.addView(shield, LinearLayout.LayoutParams(dp(70), dp(70)).apply { bottomMargin = dp(12) })
        addText(hero, getString(R.string.privacy_hero_title), 23f, R.color.surface, medium = true, bottom = 4)
        addText(hero, getString(R.string.privacy_hero_body), 15f, R.color.hero_muted, bottom = 14)
        val offlineBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(40).toFloat()
                setStroke(dp(1), Color.argb(52, 255, 255, 255))
            }
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }
        val offlineDot = View(this).apply { background = rounded(R.color.lime, 20) }
        offlineBadge.addView(offlineDot, LinearLayout.LayoutParams(dp(7), dp(7)).apply { marginEnd = dp(7) })
        offlineBadge.addView(makeTextView(getString(R.string.local_only_badge), 13f, R.color.surface, medium = true))
        hero.addView(offlineBadge)

        val privacyItems = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.surface, 20, R.color.line)
            clipToOutline = true
        }
        addPrivacyItem(privacyItems, R.drawable.ic_phone, R.string.privacy_on_device_title, R.string.privacy_on_device_body, dividerAfter = true)
        addPrivacyItem(privacyItems, R.drawable.ic_nav_history, R.string.privacy_minimal_title, R.string.privacy_minimal_body, dividerAfter = true)
        addPrivacyItem(privacyItems, R.drawable.ic_lock, R.string.privacy_sensitive_title, R.string.privacy_sensitive_body)
        parent.addView(privacyItems, margin(bottom = 14))

        val access = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.access_surface, 17, R.color.access_line)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        parent.addView(access, margin(bottom = 14))
        addSettingHeading(access, R.drawable.ic_info, R.color.access_surface, getString(R.string.notification_access_scope_title),
            getString(R.string.notification_access_scope))
        addText(access, getString(R.string.notification_access_explanation), 14f, R.color.muted_ink, bottom = 0)

        addSectionLabel(parent, getString(R.string.never_requests_title))
        addPermissionChipRow(parent, listOf(
            R.string.no_internet_permission,
            R.string.no_sms_permission,
            R.string.no_contacts_permission
        ))
        addPermissionChipRow(parent, listOf(
            R.string.no_location_permission,
            R.string.no_storage_permission,
            R.string.no_account_permission
        ))
        addAction(parent, getString(R.string.done_action), primary = true) { showScreen(Screen.SETTINGS) }
    }

    private fun addPrivacyItem(parent: LinearLayout, icon: Int, titleRes: Int, bodyRes: Int, dividerAfter: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        addSettingIcon(row, icon, R.color.setting_teal)
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(copy, getString(titleRes), 16f, R.color.ink, medium = true, bottom = 2)
        addText(copy, getString(bodyRes), 14f, R.color.muted_ink, bottom = 0)
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(11) })
        parent.addView(row)
        if (dividerAfter) {
            val divider = View(this).apply { setBackgroundColor(getColor(R.color.segment_surface)) }
            parent.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply {
                marginStart = dp(65)
                marginEnd = dp(14)
            })
        }
    }

    private fun addPermissionChipRow(parent: LinearLayout, permissionResources: List<Int>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        permissionResources.forEachIndexed { index, permissionResource ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(R.color.history_count_surface, 10)
                setPadding(dp(8), dp(8), dp(6), dp(8))
                contentDescription = getString(R.string.not_requested_accessibility, getString(permissionResource))
            }
            val xMark = makeTextView("×", 16f, R.color.status_slate, medium = true)
            chip.addView(xMark, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(4) })
            chip.addView(makeTextView(getString(permissionResource), 12f, R.color.status_slate, medium = true))
            row.addView(chip, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        parent.addView(row, margin(bottom = 6))
    }

    private fun addHistory(parent: LinearLayout) {
        if (samplePreviewEnabled && BuildConfig.DEBUG) {
            addPageHeader(
                parent,
                getString(R.string.last_three_months_label),
                getString(R.string.history_title),
                Screen.HOME
            )
        } else {
            addPageHeader(
                parent,
                getString(R.string.last_three_months_label),
                getString(R.string.history_title),
                Screen.HOME,
                R.drawable.ic_trash,
                getString(R.string.clear_history),
                ::confirmClearHistory
            )
        }
        if (samplePreviewEnabled && BuildConfig.DEBUG) addSamplePreviewNotice(parent)
        val now = System.currentTimeMillis()
        val retainedStart = PaymentHistoryRules.localDayBounds(PaymentHistoryRules.retentionCutoff(now)).startInclusiveMillis
        val retainedEnd = PaymentHistoryRules.localDayBounds(now).endExclusiveMillis
        val previewRecords = if (samplePreviewEnabled && BuildConfig.DEBUG) samplePayments(now) else null
        val allRecords = previewRecords ?: readHistory(emptyList()) {
            historyRepository?.history(PaymentHistoryFilter(retainedStart, retainedEnd)).orEmpty()
        }
        val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summary.addView(
            historySummaryTile(getString(R.string.history_total_received), formatAmount(allRecords.sumOf { it.amountPaise }), true),
            LinearLayout.LayoutParams(0, -2, 2f).apply { marginEnd = dp(8) }
        )
        summary.addView(
            historySummaryTile(getString(R.string.history_payment_count), allRecords.size.toString(), false),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        parent.addView(summary, margin(bottom = 12))

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val activeCount = activeHistoryFilterCount()
        addHistoryChip(
            filterRow,
            getString(R.string.filters) + if (activeCount > 0) " · $activeCount" else "",
            R.drawable.ic_filter,
            historyFiltersExpanded || activeCount > 0
        ) {
            historyFiltersExpanded = !historyFiltersExpanded
            showScreen(Screen.HISTORY)
        }
        filterRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        addHistoryChip(filterRow, getString(historyDateRangeLabel()), R.drawable.ic_clock, false) {
            cycleHistoryDateRange()
        }
        parent.addView(filterRow, margin(bottom = 10))

        if (historyFiltersExpanded) addHistoryFilterPanel(parent)
        val records = previewRecords?.let { filterSamplePayments(it, currentHistoryFilter()) }
            ?: readHistory(emptyList()) { historyRepository?.history(currentHistoryFilter()).orEmpty() }
        if (historyStorageError) {
            val error = card(parent)
            addText(error, getString(R.string.history_storage_error), 17f, R.color.amber)
        } else if (records.isEmpty()) {
            val active = activeHistoryFilterCount() > 0
            addEmptyPaymentState(
                parent,
                getString(if (active) R.string.no_matching_title else R.string.empty_history_title),
                getString(if (active) R.string.no_matching_payments else R.string.empty_history_body),
                filtered = active
            )
            addText(parent, getString(R.string.notification_not_bank_confirmation), 14f, R.color.muted_ink)
            if (BuildConfig.DEBUG && !samplePreviewEnabled) {
                addAction(parent, getString(R.string.view_sample_payments), primary = false) {
                    samplePreviewEnabled = true
                    showScreen(Screen.HOME)
                }
            }
        } else {
            records.groupBy { PaymentHistoryRules.localDayBounds(it.receivedAtMillis).startInclusiveMillis }
                .toList()
                .sortedByDescending { it.first }
                .forEach { (dayStart, dayRecords) ->
                    val dayGroup = card(parent)
                    val dayHeading = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    dayHeading.addView(makeTextView(formatDate(dayStart), 14f, R.color.muted_ink, medium = true))
                    dayHeading.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    dayHeading.addView(makeTextView(formatAmount(dayRecords.sumOf { it.amountPaise }), 14f, R.color.ink, medium = true))
                    dayGroup.addView(dayHeading, margin(bottom = 8))
                    dayRecords.forEachIndexed { index, record ->
                        addPaymentRecord(dayGroup, record, compact = true)
                        if (index < dayRecords.lastIndex) {
                            View(this).apply { setBackgroundColor(getColor(R.color.segment_surface)) }
                                .also { dayGroup.addView(it, LinearLayout.LayoutParams(-1, dp(1)).apply {
                                    marginStart = dp(10)
                                    marginEnd = dp(10)
                                }) }
                        }
                    }
                }
        }
        addInlineInfo(parent, getString(R.string.retention_note))
    }

    private fun samplePayments(now: Long): List<PaymentRecord> {
        fun receivedAt(daysAgo: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis

        return listOf(
            PaymentRecord(85_000L, receivedAt(0, 10, 42), "Google Pay"),
            PaymentRecord(120_000L, receivedAt(0, 9, 16), "PhonePe"),
            PaymentRecord(45_000L, receivedAt(0, 8, 3), "Paytm"),
            PaymentRecord(250_000L, receivedAt(1, 18, 34), "Google Pay"),
            PaymentRecord(18_000L, receivedAt(2, 14, 21), "PhonePe")
        )
    }

    private fun filterSamplePayments(
        records: List<PaymentRecord>,
        filter: PaymentHistoryFilter
    ): List<PaymentRecord> = records.filter { record ->
        record.receivedAtMillis >= filter.startInclusiveMillis &&
            record.receivedAtMillis < filter.endExclusiveMillis &&
            (filter.sourceApp == null || record.sourceApp == filter.sourceApp) &&
            (filter.minimumAmountPaise == null || record.amountPaise >= filter.minimumAmountPaise) &&
            (filter.maximumAmountPaise == null || record.amountPaise <= filter.maximumAmountPaise)
    }

    private fun addHistoryFilterPanel(parent: LinearLayout) {
        val filters = card(parent)
        addLabel(filters, getString(R.string.date_range_filter))
        val dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addHistoryChip(dateRow, getString(R.string.date_from, formatDate(historyStartMillis)), null, false) {
            showDatePicker(selectStartDate = true)
        }
        addHistoryChip(dateRow, getString(R.string.date_to, formatDate(historyEndDateMillis)), null, false) {
            showDatePicker(selectStartDate = false)
        }
        filters.addView(dateRow, margin(bottom = 8))
        addLabel(filters, getString(R.string.source_filter))
        val sourceGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addSourceOption(this, null, getString(R.string.all_sources))
            PaymentProvider.entries.forEach { addSourceOption(this, it.displayName, it.displayName) }
        }
        sourceGroup.setOnCheckedChangeListener { group, checkedId ->
            historySourceApp = group.findViewById<RadioButton>(checkedId)?.tag as? String
            historyFilterError = null
            showScreen(Screen.HISTORY)
        }
        filters.addView(sourceGroup, margin(bottom = 8))
        addLabel(filters, getString(R.string.amount_filter))
        val amountRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minimumField = amountInput(getString(R.string.minimum_amount_hint), minimumAmountText) { minimumAmountText = it }
        val maximumField = amountInput(getString(R.string.maximum_amount_hint), maximumAmountText) { maximumAmountText = it }
        amountRow.addView(minimumField, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(6) })
        amountRow.addView(maximumField, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(6) })
        filters.addView(amountRow, margin(bottom = 8))
        historyFilterError?.let { addText(filters, it, 15f, R.color.amber) }
        addAction(filters, getString(R.string.apply_filters), primary = true) { applyHistoryFilters() }
        addAction(filters, getString(R.string.reset_filters), primary = false) {
            resetHistoryFilters()
            showScreen(Screen.HISTORY)
        }
        addText(filters, getString(R.string.total_ignores_filters), 14f, R.color.muted_ink, top = 6)
    }

    private fun historySummaryTile(label: String, value: String, primary: Boolean): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(if (primary) R.color.forest_dark else R.color.history_count_surface, 18)
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        addText(tile, label, 13f, if (primary) R.color.hero_muted else R.color.muted_ink, bottom = 2)
        addText(tile, value, 21f, if (primary) R.color.surface else R.color.ink, medium = true, bottom = 0)
        return tile
    }

    private fun addHistoryChip(parent: LinearLayout, label: String, icon: Int?, selected: Boolean, action: () -> Unit) {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(if (selected) R.color.setting_teal_surface else R.color.surface, 12,
                if (selected) R.color.forest else R.color.line)
            minimumHeight = dp(42)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { action() }
        }
        if (icon != null) button.addView(iconImage(icon, if (selected) R.color.forest else R.color.muted_ink),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })
        val text = TextView(this).apply {
            this.text = label
            textSize = 14f
            setTextColor(getColor(if (selected) R.color.forest_dark else R.color.muted_ink))
        }
        button.addView(text)
        parent.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
    }

    private fun activeHistoryFilterCount(): Int {
        val now = System.currentTimeMillis()
        val defaultStart = PaymentHistoryRules.localDayBounds(PaymentHistoryRules.retentionCutoff(now)).startInclusiveMillis
        val defaultEnd = PaymentHistoryRules.localDayBounds(now).startInclusiveMillis
        return (if (historySourceApp != null) 1 else 0) +
            (if (minimumAmountPaise != null || maximumAmountPaise != null) 1 else 0) +
            (if (historyStartMillis > defaultStart || historyEndDateMillis < defaultEnd) 1 else 0)
    }

    private fun historyDateRangeLabel(): Int {
        val now = System.currentTimeMillis()
        val today = PaymentHistoryRules.localDayBounds(now).startInclusiveMillis
        val allStart = PaymentHistoryRules.localDayBounds(PaymentHistoryRules.retentionCutoff(now)).startInclusiveMillis
        val weekCalendar = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val weekStart = PaymentHistoryRules.localDayBounds(weekCalendar.timeInMillis).startInclusiveMillis
        return when {
            historyStartMillis == today && historyEndDateMillis == today -> R.string.today_only
            historyStartMillis == weekStart && historyEndDateMillis == today -> R.string.last_seven_days
            historyStartMillis == allStart && historyEndDateMillis == today -> R.string.three_months
            else -> R.string.custom_date_range
        }
    }

    private fun cycleHistoryDateRange() {
        val now = System.currentTimeMillis()
        val today = PaymentHistoryRules.localDayBounds(now).startInclusiveMillis
        val allStart = PaymentHistoryRules.localDayBounds(PaymentHistoryRules.retentionCutoff(now)).startInclusiveMillis
        val weekCalendar = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val weekStart = PaymentHistoryRules.localDayBounds(weekCalendar.timeInMillis).startInclusiveMillis
        when (historyDateRangeLabel()) {
            R.string.three_months -> {
                historyStartMillis = today
                historyEndDateMillis = today
            }
            R.string.today_only -> {
                historyStartMillis = weekStart
                historyEndDateMillis = today
            }
            else -> {
                historyStartMillis = allStart
                historyEndDateMillis = today
            }
        }
        showScreen(Screen.HISTORY)
    }

    private fun addSourceOption(group: RadioGroup, sourceApp: String?, label: String) {
        val option = RadioButton(this).apply {
            id = View.generateViewId()
            text = label
            textSize = 17f
            minHeight = dp(48)
            tag = sourceApp
            isChecked = historySourceApp == sourceApp
        }
        group.addView(option)
    }

    private fun amountInput(hint: String, value: String, onChanged: (String) -> Unit): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 16f
            minHeight = dp(56)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onChanged(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }

    private fun addPaymentRecord(
        parent: LinearLayout,
        record: PaymentRecord,
        compact: Boolean = false,
        listRow: Boolean = false
    ) {
        val item = if (compact) parent else card(parent)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            when {
                listRow -> {
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    minimumHeight = dp(68)
                }
                !compact -> setPadding(0, dp(2), 0, dp(2))
            }
        }
        val providerIcon = when (record.sourceApp) {
            "Google Pay" -> R.drawable.ic_provider_google
            "PhonePe" -> R.drawable.ic_provider_phonepe
            "Paytm" -> R.drawable.ic_provider_paytm
            else -> R.drawable.ic_provider_generic
        }
        val providerSurface = when (record.sourceApp) {
            "Google Pay" -> R.color.source_google_surface
            "PhonePe" -> R.color.source_phonepe_surface
            "Paytm" -> R.color.source_paytm_surface
            else -> R.color.history_count_surface
        }
        val receivedMark = ImageView(this).apply {
            setImageResource(providerIcon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(providerSurface, 13)
            contentDescription = getString(R.string.source_app_accessibility, record.sourceApp)
        }
        row.addView(receivedMark, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        addText(details, record.sourceApp, 16f, R.color.ink, medium = true, bottom = 2)
        addText(details, formatDateTime(record.receivedAtMillis), 14f, R.color.muted_ink, bottom = 0)
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        val amountColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val amount = TextView(this).apply {
            text = "+${formatAmount(record.amountPaise)}"
            textSize = if (compact) 20f else 19f
            setTextColor(getColor(R.color.forest_dark))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.END
            contentDescription = getString(R.string.received_amount_accessibility, formatAmount(record.amountPaise))
        }
        amountColumn.addView(amount)
        row.addView(amountColumn)
        item.addView(row)
    }

    private fun currentHistoryFilter(): PaymentHistoryFilter = PaymentHistoryFilter(
        startInclusiveMillis = historyStartMillis,
        endExclusiveMillis = PaymentHistoryRules.localDayBounds(historyEndDateMillis).endExclusiveMillis,
        sourceApp = historySourceApp,
        minimumAmountPaise = minimumAmountPaise,
        maximumAmountPaise = maximumAmountPaise
    )

    private fun applyHistoryFilters() {
        val minimum = parseAmountFilter(minimumAmountText)
        val maximum = parseAmountFilter(maximumAmountText)
        val invalidMinimum = minimumAmountText.isNotBlank() && minimum == null
        val invalidMaximum = maximumAmountText.isNotBlank() && maximum == null
        if (invalidMinimum || invalidMaximum || (minimum != null && maximum != null && minimum > maximum)) {
            historyFilterError = getString(R.string.invalid_amount_range)
            showScreen(Screen.HISTORY)
            return
        }
        minimumAmountPaise = minimum
        maximumAmountPaise = maximum
        historyFilterError = null
        historyFiltersExpanded = false
        showScreen(Screen.HISTORY)
    }

    private fun parseAmountFilter(raw: String): Long? {
        if (raw.isBlank()) return null
        return try {
            BigDecimal(raw.trim().replace(",", "").removePrefix("₹"))
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { it >= 0L }
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun resetHistoryFilters() {
        val now = System.currentTimeMillis()
        val cutoff = PaymentHistoryRules.retentionCutoff(now)
        historyStartMillis = PaymentHistoryRules.localDayBounds(cutoff).startInclusiveMillis
        historyEndDateMillis = PaymentHistoryRules.localDayBounds(now).startInclusiveMillis
        historySourceApp = null
        minimumAmountText = ""
        maximumAmountText = ""
        minimumAmountPaise = null
        maximumAmountPaise = null
        historyFilterError = null
    }

    private fun showDatePicker(selectStartDate: Boolean) {
        val selectedMillis = if (selectStartDate) historyStartMillis else historyEndDateMillis
        val initial = Calendar.getInstance().apply { timeInMillis = selectedMillis }
        val cutoff = PaymentHistoryRules.retentionCutoff(System.currentTimeMillis())
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (selectStartDate) {
                    historyStartMillis = selected
                    if (historyStartMillis > historyEndDateMillis) historyEndDateMillis = selected
                } else {
                    historyEndDateMillis = selected
                    if (historyEndDateMillis < historyStartMillis) historyStartMillis = selected
                }
                showScreen(Screen.HISTORY)
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = cutoff
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_message)
            .setNegativeButton(R.string.cancel_action, null)
            .setPositiveButton(R.string.confirm_clear_action) { _, _ ->
                val cleared = try {
                    historyRepository?.clearHistory()
                    true
                } catch (_: SQLiteException) {
                    false
                }
                Toast.makeText(
                    this,
                    if (cleared) R.string.history_cleared else R.string.history_storage_error,
                    Toast.LENGTH_SHORT
                ).show()
                showScreen(currentScreen)
            }
            .show()
    }

    private inline fun <T> readHistory(fallback: T, action: () -> T): T = try {
        action()
    } catch (_: SQLiteException) {
        historyStorageError = true
        fallback
    }

    private fun card(parent: LinearLayout): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(R.color.surface, 22, R.color.line)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        parent.addView(container, margin(bottom = 14))
        return container
    }

    private fun addEmptyPaymentState(
        parent: LinearLayout,
        title: String,
        body: String,
        compact: Boolean = false,
        filtered: Boolean = false
    ) {
        val state = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            if (compact) background = rounded(R.color.surface, 20, R.color.line)
            setPadding(dp(18), dp(if (compact) 20 else 22), dp(18), dp(20))
        }
        val iconTile = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(R.color.mint, 20)
        }
        val icon = if (filtered) R.drawable.ic_filter else R.drawable.ic_nav_history
        iconTile.addView(iconImage(icon, R.color.forest), LinearLayout.LayoutParams(dp(30), dp(30)))
        state.addView(iconTile, LinearLayout.LayoutParams(dp(58), dp(58)).apply { bottomMargin = dp(14) })
        addText(state, title, 17f, R.color.ink, medium = true, bottom = 4)
        addText(state, body, 14f, R.color.muted_ink, bottom = 0)
        parent.addView(state, margin(top = if (compact) 0 else 18, bottom = 10))
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
        val text = makeTextView(value, size, color, medium)
        parent.addView(text, margin(top = top, bottom = bottom))
    }

    private fun makeTextView(value: String, size: Float, color: Int, medium: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(getColor(color))
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = true
            setLineSpacing(dp(2).toFloat(), 1f)
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

    private fun getUiLanguage(): String = AppPreferences.uiLanguage(this)

    private fun setUiLanguage(language: String) {
        if (getUiLanguage() == language) return
        AppPreferences.setUiLanguage(this, language)
        recreate()
    }

    private fun formatAmount(amountPaise: Long): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
            minimumFractionDigits = if (amountPaise % 100L == 0L) 0 else 2
            maximumFractionDigits = 2
        }
        return formatter.format(BigDecimal.valueOf(amountPaise, 2))
    }

    private fun formatDate(timestampMillis: Long): String = java.text.SimpleDateFormat(
        "d MMMM yyyy",
        Locale.forLanguageTag(getUiLanguage())
    ).format(Date(timestampMillis))

    private fun formatDateTime(timestampMillis: Long): String = java.text.SimpleDateFormat(
        "d MMMM yyyy, h:mm a",
        Locale.forLanguageTag(getUiLanguage())
    ).format(Date(timestampMillis))

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
        boostAvailable = null
        speechController?.shutdown()
        speechController = HindiSpeechController(
            context = this,
            onReadinessChanged = { ready ->
                if (generation != speechControllerGeneration) return@HindiSpeechController
                hindiVoiceReady = ready
                ttsCheckComplete = true
                if (ready && refreshListenerService) {
                    sendBroadcast(Intent(SpeechActions.REFRESH_LOCAL_HINDI_VOICE).setPackage(packageName))
                }
                if (!isFinishing) showScreen(currentScreen)
            },
            onBoostAvailabilityChanged = { available ->
                if (generation != speechControllerGeneration) return@HindiSpeechController
                boostAvailable = available
                if (!isFinishing) showScreen(currentScreen)
            },
            initialBoostEnabled = AppPreferences.loudnessBoostEnabled(this)
        )
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(getColor(color))
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), getColor(strokeColor))
        }

    private fun margin(top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
            marginStart = dp(start)
            marginEnd = dp(end)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val STATE_SCREEN = "current_screen"
    }
}
