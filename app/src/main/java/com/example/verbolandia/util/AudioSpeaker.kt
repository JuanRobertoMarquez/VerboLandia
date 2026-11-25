package com.example.verbolandia.util

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.verbolandia.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

object AudioSpeaker {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingSpeech: String? = null
    private var pendingView: View? = null
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
                        pendingSpeech?.let { text ->
                            val v = pendingView
                            pendingSpeech = null
                            pendingView = null
                            speak(context, text, v)
                        }
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

    fun speak(context: Context, text: String, view: View? = null) {
        init(context)

        // 1. Feedback visual inmediato en el icono de bocina (animación elástica y cambio de color)
        view?.let { animateSpeakerIcon(it) }

        // 2. Comprobar si el teléfono está en silencio o con volumen en cero
        checkVolumeAndNotify(context, view)

        // 3. Ejecutar TextToSpeech sin Toasts invasivos
        if (isReady && tts != null) {
            tts?.setSpeechRate(speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verbolandia_audio")
        } else {
            pendingSpeech = text
            pendingView = view
        }
    }

    private fun checkVolumeAndNotify(context: Context, anchorView: View?) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Únicamente alertar si el volumen multimedia está en cero (no por modo vibración/silencio de llamadas)
        val isMuted = musicVolume <= 0

        if (isMuted) {
            val targetView = anchorView ?: (context as? Activity)?.findViewById(android.R.id.content)
            if (targetView != null) {
                Snackbar.make(
                    targetView,
                    context.getString(R.string.audio_volume_muted_warning),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun animateSpeakerIcon(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        // Animación elástica de pulsación
        view.animate()
            .scaleX(1.35f)
            .scaleY(1.35f)
            .setDuration(130)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
            .start()

        // Cambio temporal de tinte a dorado / acento activo durante la reproducción
        val context = view.context
        val activeColor = ContextCompat.getColor(context, R.color.accent_gold)
        val defaultColor = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)

        when (view) {
            is ImageButton -> {
                view.imageTintList = ColorStateList.valueOf(activeColor)
                view.postDelayed({
                    view.imageTintList = ColorStateList.valueOf(defaultColor)
                }, 500)
            }
            is ImageView -> {
                view.imageTintList = ColorStateList.valueOf(activeColor)
                view.postDelayed({
                    view.imageTintList = ColorStateList.valueOf(defaultColor)
                }, 500)
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        pendingSpeech = null
        pendingView = null
    }
}
