package com.example.verbolandia.memory

import java.io.Serializable

enum class MemoryCardRole {
    BASE_VERB,
    CONJUGATED_FORM
}

data class MemoryCard(
    val id: Int,
    val pairId: Int,
    val verbBase: String,
    val role: MemoryCardRole,
    val displayText: String,
    val phonetic: String,
    val badgeLabel: String,
    val meaning: String,
    var isFaceUp: Boolean = false,
    var isMatched: Boolean = false
) : Serializable

sealed class MemoryFlipResult {
    object Ignored : MemoryFlipResult()
    data class FirstCardFlipped(val card: MemoryCard) : MemoryFlipResult()
    data class MatchFound(val card1: MemoryCard, val card2: MemoryCard, val streak: Int) : MemoryFlipResult()
    data class NoMatch(val card1: MemoryCard, val card2: MemoryCard) : MemoryFlipResult()
}
