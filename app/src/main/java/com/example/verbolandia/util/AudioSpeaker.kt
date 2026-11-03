package com.example.verbolandia.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

object AudioSpeaker {

    private var tts: TextToSpeech? = null
    private var isReady = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        isReady = true
                    }
                }
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        if (isReady && tts != null) {
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
