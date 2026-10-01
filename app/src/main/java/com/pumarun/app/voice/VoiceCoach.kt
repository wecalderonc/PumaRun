package com.pumarun.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pumarun.app.domain.Announcement
import com.pumarun.app.domain.CoachStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Speaks announcements through TextToSpeech, ducking other audio (e.g. music) while talking. */
@Singleton
class VoiceCoach @Inject constructor(
    @ApplicationContext private val context: Context,
    private val phrases: Phrases,
) {
    enum class Status { NotStarted, Initializing, Ready, MissingData, Unavailable }

    private val _status = MutableStateFlow(Status.NotStarted)
    val status: StateFlow<Status> = _status.asStateFlow()

    var locale: Locale = preferredLocale()
        private set

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    private var tts: TextToSpeech? = null
    private val pending = mutableListOf<String>()
    private val activeUtterances = AtomicInteger(0)
    private val utteranceIds = AtomicInteger(0)

    fun ensureInitialized() {
        if (tts != null) return
        _status.value = Status.Initializing
        tts = TextToSpeech(context) { result -> onInit(result) }
    }

    fun announce(announcement: Announcement) = speak(phrases.text(announcement, locale))

    fun speakTest(style: CoachStyle) {
        ensureInitialized()
        speak(phrases.testPhrase(locale))
        phrases.coachSample(style, locale)?.let(::speak)
    }

    fun speak(text: String) {
        ensureInitialized()
        synchronized(pending) {
            if (_status.value != Status.Ready && _status.value != Status.MissingData) {
                if (_status.value != Status.Unavailable) pending += text
                return
            }
        }
        doSpeak(text)
    }

    fun stop() {
        tts?.stop()
        activeUtterances.set(0)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onInit(result: Int) {
        val engine = tts
        if (result != TextToSpeech.SUCCESS || engine == null) {
            _status.value = Status.Unavailable
            return
        }
        engine.setAudioAttributes(audioAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onUtteranceFinished()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onUtteranceFinished()
            override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceFinished()
        })

        val status = if (engine.isSupported(locale)) {
            Status.Ready
        } else if (engine.isSupported(Locale.US)) {
            // Preferred language voice is missing; speak English meanwhile and let the UI offer an install.
            locale = Locale.US
            Status.MissingData
        } else {
            Status.Unavailable
        }
        if (status != Status.Unavailable) engine.language = locale

        val queued = synchronized(pending) {
            _status.value = status
            pending.toList().also { pending.clear() }
        }
        if (status != Status.Unavailable) queued.forEach(::doSpeak)
    }

    private fun TextToSpeech.isSupported(l: Locale): Boolean =
        isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE

    private fun doSpeak(text: String) {
        val engine = tts ?: return
        if (activeUtterances.getAndIncrement() == 0) {
            audioManager.requestAudioFocus(focusRequest)
        }
        Log.i(TAG, "[${locale.toLanguageTag()}] $text")
        val id = "puma-${utteranceIds.incrementAndGet()}"
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (result != TextToSpeech.SUCCESS) onUtteranceFinished()
    }

    private fun onUtteranceFinished() {
        if (activeUtterances.decrementAndGet() <= 0) {
            activeUtterances.set(0)
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    companion object {
        private const val TAG = "PumaVoice"

        fun preferredLocale(default: Locale = Locale.getDefault()): Locale =
            when (default.language) {
                "es", "en" -> default
                else -> Locale.US
            }
    }
}
