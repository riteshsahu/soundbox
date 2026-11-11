package com.soundbox.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.ArrayDeque

/** Uses only an already installed, offline Hindi voice. */
class HindiSpeechController(
    context: Context,
    private val onReadinessChanged: (Boolean) -> Unit,
    private val onBoostAvailabilityChanged: (Boolean?) -> Unit = {},
    initialBoostEnabled: Boolean = false,
    initialBoostDb: Int = 12,
    initialSpeechRatePercent: Int = 85
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioSessionId = try {
        audioManager.generateAudioSessionId()
    } catch (_: RuntimeException) {
        AudioManager.ERROR
    }
    private val pending = ArrayDeque<String>()
    private var textToSpeech: TextToSpeech? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var boostGainDb = if (initialBoostEnabled) initialBoostDb.coerceIn(6, 48) else 0
    private var speechRatePercent = initialSpeechRatePercent.coerceIn(70, 100)
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private var waitingForFocus = false
    private var outstandingUtterances = 0
    private var nextUtteranceId = 0

    var isReady: Boolean = false
        private set

    var isBoostAvailable: Boolean? = null
        private set

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusHeld = true
                waitingForFocus = false
                drainQueue()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                textToSpeech?.stop()
                outstandingUtterances = 0
                pending.clear()
                abandonFocus()
            }
        }
    }

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            val engine = textToSpeech
            val voice = engine?.voices
                ?.filter { it.locale.language == "hi" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { it.quality }

            val available = status == TextToSpeech.SUCCESS && voice != null
            if (available) {
                val localeStatus = engine!!.setLanguage(voice!!.locale)
                if (localeStatus >= TextToSpeech.LANG_AVAILABLE) {
                    engine.voice = voice
                    engine.setSpeechRate(speechRatePercent / 100f)
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) = finishUtterance()

                        @Deprecated("Deprecated by the Android TTS API")
                        override fun onError(utteranceId: String?) = finishUtterance()

                        override fun onError(utteranceId: String?, errorCode: Int) = finishUtterance()
                    })
                    isReady = true
                    if (boostGainDb > 0) applyBoost()
                }
            }
            onReadinessChanged(isReady)
            if (!isReady && boostGainDb > 0) setBoostAvailability(false)
            if (isReady) drainQueue() else pending.clear()
        }
    }

    fun speakHindi(phrase: String): Boolean {
        if (phrase.isBlank() || phrase.any { it in 'A'..'Z' || it in 'a'..'z' }) return false
        if (pending.size >= MAX_PENDING_UTTERANCES) pending.removeFirst()
        pending.addLast(phrase)
        drainQueue()
        return true
    }

    fun stopAll() {
        pending.clear()
        textToSpeech?.stop()
        outstandingUtterances = 0
        abandonFocus()
    }

    fun setBoostDb(db: Int): Boolean? {
        boostGainDb = (db.coerceIn(0, 48) / 6) * 6
        if (boostGainDb == 0) {
            releaseLoudnessEnhancer()
            return isBoostAvailable
        }
        if (!isReady) return null
        return applyBoost()
    }

    fun setSpeechRatePercent(percent: Int) {
        speechRatePercent = percent.coerceIn(70, 100)
        textToSpeech?.setSpeechRate(speechRatePercent / 100f)
    }

    fun shutdown() {
        stopAll()
        releaseLoudnessEnhancer()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
    }

    private fun drainQueue() {
        if (!isReady || pending.isEmpty() || waitingForFocus) return
        if (!focusHeld) {
            when (requestFocus()) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> focusHeld = true
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    waitingForFocus = true
                    return
                }
                else -> {
                    pending.clear()
                    return
                }
            }
        }

        val engine = textToSpeech ?: return
        while (pending.isNotEmpty()) {
            val phrase = pending.removeFirst()
            val utteranceId = "soundbox-${nextUtteranceId++}"
            if (boostGainDb > 0) applyBoost()
            val parameters = Bundle().apply {
                if (audioSessionId > 0) {
                    putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, audioSessionId)
                }
            }
            val result = engine.speak(phrase, TextToSpeech.QUEUE_ADD, parameters, utteranceId)
            if (result == TextToSpeech.SUCCESS) outstandingUtterances++
        }
        if (outstandingUtterances == 0) abandonFocus()
    }

    private fun requestFocus(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = request
            return audioManager.requestAudioFocus(request)
        }
        @Suppress("DEPRECATION")
        return audioManager.requestAudioFocus(
            focusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun finishUtterance() {
        if (outstandingUtterances > 0) outstandingUtterances--
        if (outstandingUtterances == 0 && pending.isEmpty()) abandonFocus()
    }

    private fun applyBoost(): Boolean {
        if (audioSessionId <= 0) {
            setBoostAvailability(false)
            return false
        }

        var effect: LoudnessEnhancer? = loudnessEnhancer
        return try {
            if (effect == null) {
                effect = LoudnessEnhancer(audioSessionId)
                if (!effect.hasControl()) throw UnsupportedOperationException("Audio effect control unavailable")
            }
            effect.setTargetGain(boostGainDb * 100)
            if (effect.setEnabled(true) != AudioEffect.SUCCESS) {
                throw UnsupportedOperationException("Audio effect could not be enabled")
            }
            loudnessEnhancer = effect
            setBoostAvailability(true)
            true
        } catch (_: RuntimeException) {
            effect?.let { runCatching { it.release() } }
            loudnessEnhancer = null
            setBoostAvailability(false)
            false
        }
    }

    private fun releaseLoudnessEnhancer() {
        loudnessEnhancer?.let { effect ->
            runCatching { effect.setEnabled(false) }
            runCatching { effect.release() }
        }
        loudnessEnhancer = null
    }

    private fun setBoostAvailability(available: Boolean) {
        if (isBoostAvailable == available) return
        isBoostAvailable = available
        onBoostAvailabilityChanged(available)
    }

    private fun abandonFocus() {
        if (!focusHeld && !waitingForFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        focusHeld = false
        waitingForFocus = false
    }

    private companion object {
        const val MAX_PENDING_UTTERANCES = 5
    }
}
