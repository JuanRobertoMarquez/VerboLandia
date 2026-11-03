package com.example.verbolandia.util

import android.content.Context
import com.example.verbolandia.data.VerbRepository
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TranslationManager(private val context: Context) {

    private val repository = VerbRepository.getInstance(context)

    private val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.SPANISH)
        .build()

    private val translator = Translation.getClient(options)

    suspend fun translateAndSave(verbBase: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            translator.downloadModelIfNeeded(conditions).await()
            val translatedText = translator.translate(verbBase).await()

            if (translatedText.isNotBlank()) {
                repository.saveTranslation(verbBase, translatedText, source = "mlkit")
                withContext(Dispatchers.Main) {
                    onSuccess(translatedText)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError(Exception("Traducción vacía"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }

    fun close() {
        translator.close()
    }
}
