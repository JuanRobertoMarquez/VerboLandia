package com.example.verbolandia.model

import java.io.Serializable

data class Verb(
    val id: Int = 0,
    val base: String,
    val thirdPerson: String,
    val past: String,
    val pastParticiple: String,
    val gerund: String,
    val isIrregular: Boolean = false,
    val isCommon: Boolean = false,
    val spanishMeaning: String = "",
    val phonetic: String = "",
    val level: String = "A1",
    var mastery: Int = 0,
    var isFavorite: Boolean = false,
) : Serializable
