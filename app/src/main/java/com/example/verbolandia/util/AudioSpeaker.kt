package com.example.verbolandia.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

object AudioSpeaker {

    private var tts: TextToSpeech? = null
    private var isReady = false
    var speechRate: Float = 1.0f
        private set

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        isReady = true
                        tts?.setSpeechRate(speechRate)
                    }
                }
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        if (isReady && tts != null) {
            tts?.setSpeechRate(rate)
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        if (isReady && tts != null) {
            tts?.setSpeechRate(speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verbolandia_audio")
        } else {
            Toast.makeText(context.applicationContext, "Pronunciando: $text", Toast.LENGTH_SHORT).show()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
