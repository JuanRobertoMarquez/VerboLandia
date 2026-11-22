package com.example.verbolandia.wordsearch

import java.io.Serializable

enum class WordSearchDirection(val dRow: Int, val dCol: Int) {
    HORIZONTAL(0, 1),
    VERTICAL(1, 0),
    DIAGONAL_DOWN(1, 1),
    DIAGONAL_UP(-1, 1)
}

data class WordSearchTargetWord(
    val verbId: Int,
    val base: String,
    val targetWord: String,
    val tenseLabel: String,
    val spanishMeaning: String,
    val phoneticTip: String,
    val commonErrorTip: String,
    val startRow: Int,
    val startCol: Int,
    val direction: WordSearchDirection,
    var isFound: Boolean = false
) : Serializable {
    val length: Int get() = targetWord.length

    fun getCells(): List<Pair<Int, Int>> {
        return (0 until length).map { step ->
            Pair(startRow + step * direction.dRow, startCol + step * direction.dCol)
        }
    }
}

data class WordSearchCandidate(
    val verbId: Int,
    val base: String,
    val targetWord: String,
    val tenseLabel: String,
    val spanishMeaning: String,
    val phoneticTip: String,
    val commonErrorTip: String
) : Serializable

data class WordSearchBoard(
    val size: Int = 8,
    val grid: Array<CharArray>,
    val targetWords: List<WordSearchTargetWord>,
    val targetTenseTitle: String
) : Serializable {
    fun isAllFound(): Boolean = targetWords.all { it.isFound }
}
