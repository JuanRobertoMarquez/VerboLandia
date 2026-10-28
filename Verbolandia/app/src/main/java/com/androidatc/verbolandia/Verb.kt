package com.androidatc.verbolandia

/**
 * Modelo de datos que representa un verbo en la plataforma Verbolandia.
 */
data class Verb(
    val id: Int,
    val base: String,
    val phonetic: String,
    val spanishMeaning: String,
    val pastSimple: String,
    val pastParticiple: String,
    val level: String,
    val isIrregular: Boolean,
    val masteryPercentage: Int,
    val isFrequentReview: Boolean = false
)