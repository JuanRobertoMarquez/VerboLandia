package com.example.verbolandia.model

import java.io.Serializable

data class PhrasalVerb(
    val id: Int = 0,
    val verbId: Int = 0,
    val phrasalEn: String,
    val meaningEs: String,
    val exampleEn: String,
    val exampleEs: String
) : Serializable
