package com.example.verbolandia.model

import java.io.Serializable

data class VerbTense(
    val id: Int = 0,
    val verbId: Int = 0,
    val tenseCode: String,
    val tenseNameEn: String,
    val tenseNameEs: String,
    val affirmativeEn: String,
    val negativeEn: String,
    val interrogativeEn: String,
    val exampleEs: String
) : Serializable
