package com.example.verbolandia.crossword

import java.io.Serializable

data class CrosswordClueCandidate(
    val verbId: Int,
    val base: String,
    val targetWord: String,
    val tenseCode: String,
    val tenseLabel: String,
    val exampleSentence: String,
    val spanishMeaning: String,
    val phoneticChange: String,
    val level: String
) : Serializable

data class CrosswordWord(
    val number: Int,
    val word: String,
    val isVertical: Boolean,
    val startRow: Int,
    val startCol: Int,
    val verbBase: String,
    val tenseLabel: String,
    val exampleSentenceWithBlank: String,
    val spanishMeaning: String,
    val phoneticChange: String,
    val verbId: Int,
    var isSolved: Boolean = false
) : Serializable {
    val length: Int get() = word.length
    val directionCode: String get() = if (isVertical) "V" else "H"
}

data class CrosswordCell(
    val row: Int,
    val col: Int,
    val expectedChar: Char,
    var enteredChar: Char = ' ',
    val wordNumbers: MutableList<Int> = mutableListOf(),
    var isStartingCell: Boolean = false,
    var cellNumber: Int = 0
) : Serializable {
    val isFilled: Boolean get() = enteredChar != ' '
    val isCorrect: Boolean get() = enteredChar.uppercaseChar() == expectedChar.uppercaseChar()
}

data class CrosswordBoard(
    val size: Int = 8,
    val words: List<CrosswordWord>,
    val grid: Array<Array<CrosswordCell?>>
) : Serializable {
    fun getCell(r: Int, c: Int): CrosswordCell? {
        if (r !in 0 until size || c !in 0 until size) return null
        return grid[r][c]
    }

    fun isWordSolved(word: CrosswordWord): Boolean {
        for (i in 0 until word.length) {
            val r = word.startRow + if (word.isVertical) i else 0
            val c = word.startCol + if (word.isVertical) 0 else i
            val cell = getCell(r, c) ?: return false
            if (!cell.isCorrect) return false
        }
        return true
    }

    fun isFullySolved(): Boolean {
        return words.all { isWordSolved(it) }
    }
}
