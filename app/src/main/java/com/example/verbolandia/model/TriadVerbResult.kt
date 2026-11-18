package com.example.verbolandia.model

import java.io.Serializable

data class TriadVerbResult(
    val verb: Verb,
    val isCorrect: Boolean,
    val wrongSelection: String = "",
    val correctExpected: String = "",
    val timeSpentSeconds: Int = 0
) : Serializable
