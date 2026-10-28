package com.androidatc.verbolandia

import com.google.mlkit.nl.translate.*
import com.google.mlkit.common.model.DownloadConditions

class VerbTranslator {

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    )

    fun translateVerb(base: String, onResult: (String) -> Unit, onError: (Exception) -> Unit) {
        val conditions = DownloadConditions.Builder().requireWifi().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                // "to run" en vez de "run" para que lo traduzca como verbo
                translator.translate("to $base")
                    .addOnSuccessListener { onResult(it) }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun close() = translator.close()
}