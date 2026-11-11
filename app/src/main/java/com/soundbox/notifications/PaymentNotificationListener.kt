package com.soundbox.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteException
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.os.Build
import com.soundbox.AppPreferences
import com.soundbox.history.PaymentHistoryRepository
import com.soundbox.history.PaymentRecord
import com.soundbox.speech.HindiPaymentPhrase
import com.soundbox.speech.HindiSpeechController
import com.soundbox.speech.HistoryActions
import com.soundbox.speech.SpeechActions

class PaymentNotificationListener : NotificationListenerService() {
    private lateinit var speechController: HindiSpeechController
    private lateinit var historyRepository: PaymentHistoryRepository
    private val refreshVoiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SpeechActions.STOP_QUEUED_ANNOUNCEMENTS -> speechController.stopAll()
                SpeechActions.SET_LOUDNESS_BOOST -> {
                    speechController.setBoostEnabled(
                        intent.getBooleanExtra(
                            SpeechActions.EXTRA_BOOST_ENABLED,
                            AppPreferences.loudnessBoostEnabled(applicationContext)
                        )
                    )
                }
                SpeechActions.REFRESH_LOCAL_HINDI_VOICE -> {
                    speechController.shutdown()
                    speechController = HindiSpeechController(
                        context = applicationContext,
                        onReadinessChanged = {},
                        initialBoostEnabled = AppPreferences.loudnessBoostEnabled(applicationContext)
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationAccess.setListenerConnected(this, false)
        val filter = IntentFilter(SpeechActions.REFRESH_LOCAL_HINDI_VOICE)
            .apply {
                addAction(SpeechActions.STOP_QUEUED_ANNOUNCEMENTS)
                addAction(SpeechActions.SET_LOUDNESS_BOOST)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshVoiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(refreshVoiceReceiver, filter)
        }
        speechController = HindiSpeechController(
            context = applicationContext,
            onReadinessChanged = {},
            initialBoostEnabled = AppPreferences.loudnessBoostEnabled(applicationContext)
        )
        historyRepository = PaymentHistoryRepository(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationAccess.setListenerConnected(this, true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationAccess.setListenerConnected(this, false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Reject every non-allowlisted package before accessing notification content.
        val provider = PaymentProvider.fromPackageName(sbn.packageName) ?: return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val content = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val payment = PaymentNotificationParser.parse(provider.packageName, title, content) ?: return
        val record = PaymentRecord(
            amountPaise = payment.amountPaise,
            receivedAtMillis = sbn.postTime,
            sourceApp = payment.source.displayName
        )
        val inserted = try {
            historyRepository.recordIfUnique(record)
        } catch (_: SQLiteException) {
            false
        }
        if (!inserted) return
        sendBroadcast(Intent(HistoryActions.PAYMENTS_CHANGED).setPackage(packageName))
        if (!AppPreferences.announcementsEnabled(this)) return

        val phrase = HindiPaymentPhrase.received(payment.amountPaise) ?: return
        speechController.speakHindi(phrase)
    }

    override fun onDestroy() {
        NotificationAccess.setListenerConnected(this, false)
        unregisterReceiver(refreshVoiceReceiver)
        if (::speechController.isInitialized) speechController.shutdown()
        if (::historyRepository.isInitialized) historyRepository.close()
        super.onDestroy()
    }
}
