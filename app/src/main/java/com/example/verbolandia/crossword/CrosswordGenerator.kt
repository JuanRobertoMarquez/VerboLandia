package com.example.verbolandia.crossword

import kotlin.random.Random

object CrosswordGenerator {

    const val DEFAULT_GRID_SIZE = 8
    private const val MAX_RETRIES = 60

    fun generate(
        candidates: List<CrosswordClueCandidate>,
        targetCount: Int = 4,
        gridSize: Int = DEFAULT_GRID_SIZE,
        seed: Long? = null
    ): CrosswordBoard {
        val rng = if (seed != null) Random(seed) else Random.Default
        val validCandidates = candidates.filter {
            it.targetWord.length in 3..6 && it.targetWord.all { ch -> ch in 'A'..'Z' }
        }

        if (validCandidates.size < targetCount) {
            return generateFallback(candidates.take(targetCount), gridSize)
        }

        for (attempt in 0 until MAX_RETRIES) {
            val shuffled = validCandidates.shuffled(rng)
            val board = tryBuildInterlockingBoard(shuffled, targetCount, gridSize, rng)
            if (board != null) {
                return board
            }
        }

        // Guaranteed fallback if no 4-way interlocking layout succeeded in time
        return generateFallback(validCandidates.take(targetCount), gridSize)
    }

    private data class PlacedInternal(
        val candidate: CrosswordClueCandidate,
        val word: String,
        val startRow: Int,
        val startCol: Int,
        val isVertical: Boolean
    )

    private fun tryBuildInterlockingBoard(
        candidates: List<CrosswordClueCandidate>,
        targetCount: Int,
        gridSize: Int,
        rng: Random
    ): CrosswordBoard? {
        val tempGrid = Array(gridSize) { Array(gridSize) { ' ' } }
        val placedList = mutableListOf<PlacedInternal>()

        // 1. Place first word horizontally near top-center
        val first = candidates[0]
        val firstWord = first.targetWord
        val firstRow = rng.nextInt(1, 3.coerceAtMost(gridSize - 1))
        val maxStartCol = gridSize - firstWord.length
        if (maxStartCol < 0) return null
        val firstCol = rng.nextInt(0, maxStartCol + 1)

        for (i in firstWord.indices) {
            tempGrid[firstRow][firstCol + i] = firstWord[i]
        }
        placedList.add(PlacedInternal(first, firstWord, firstRow, firstCol, false))

        // 2. Try placing remaining candidates intersecting placed words
        for (candIdx in 1 until candidates.size) {
            if (placedList.size >= targetCount) break

            val cand = candidates[candIdx]
            val candWord = cand.targetWord

            var placed = false
            val currentPlaced = placedList.shuffled(rng)

            for (parent in currentPlaced) {
                val parentWord = parent.word
                val parentRow = parent.startRow
                val parentCol = parent.startCol
                val candIsVertical = !parent.isVertical

                // Find matching letters
                for (pIdx in parentWord.indices) {
                    for (cIdx in candWord.indices) {
                        if (parentWord[pIdx] == candWord[cIdx]) {
                            val startR = if (candIsVertical) parentRow - cIdx else parentRow + pIdx
                            val startC = if (candIsVertical) parentCol + pIdx else parentCol - cIdx

                            // Bounds check
                            if (candIsVertical) {
                                if (startR < 0 || startR + candWord.length > gridSize || startC < 0 || startC >= gridSize) {
                                    continue
                                }
                            } else {
                                if (startR < 0 || startR >= gridSize || startC < 0 || startC + candWord.length > gridSize) {
                                    continue
                                }
                            }

                            // Conflict & collision check
                            var conflict = false
                            for (step in candWord.indices) {
                                val r = startR + (if (candIsVertical) step else 0)
                                val c = startC + (if (candIsVertical) 0 else step)
                                val existing = tempGrid[r][c]
                                if (existing != ' ' && existing != candWord[step]) {
                                    conflict = true
                                    break
                                }
                            }

                            if (!conflict) {
                                // Place candWord
                                for (step in candWord.indices) {
                                    val r = startR + (if (candIsVertical) step else 0)
                                    val c = startC + (if (candIsVertical) 0 else step)
                                    tempGrid[r][c] = candWord[step]
                                }
                                placedList.add(PlacedInternal(cand, candWord, startR, startC, candIsVertical))
                                placed = true
                                break
                            }
                        }
                    }
                    if (placed) break
                }
                if (placed) break
            }
        }

        if (placedList.size < targetCount) {
            return null
        }

        return buildFinalBoard(placedList, gridSize)
    }

    private fun generateFallback(
        candidates: List<CrosswordClueCandidate>,
        gridSize: Int
    ): CrosswordBoard {
        val placedList = mutableListOf<PlacedInternal>()
        var curRow = 1
        var number = 1

        for (cand in candidates.take(4)) {
            val word = cand.targetWord.take(gridSize - 1)
            val isVert = number % 2 == 0
            val startR = if (isVert) 1 else curRow
            val startC = if (isVert) curRow else 1

            val safeStartR = startR.coerceIn(0, gridSize - word.length)
            val safeStartC = startC.coerceIn(0, gridSize - word.length)

            placedList.add(PlacedInternal(cand, word, safeStartR, safeStartC, isVert))
            curRow = (curRow + 2).coerceAtMost(gridSize - 2)
            number++
        }

        return buildFinalBoard(placedList, gridSize)
    }

    private fun buildFinalBoard(
        placedList: List<PlacedInternal>,
        gridSize: Int
    ): CrosswordBoard {
        val cellGrid = Array(gridSize) { Array<CrosswordCell?>(gridSize) { null } }
        val finalWords = mutableListOf<CrosswordWord>()

        var wordNumber = 1
        for (p in placedList) {
            val cw = CrosswordWord(
                number = wordNumber,
                word = p.word,
                isVertical = p.isVertical,
                startRow = p.startRow,
                startCol = p.startCol,
                verbBase = p.candidate.base,
                tenseLabel = p.candidate.tenseLabel,
                exampleSentenceWithBlank = p.candidate.exampleSentence,
                spanishMeaning = p.candidate.spanishMeaning,
                phoneticChange = p.candidate.phoneticChange,
                verbId = p.candidate.verbId
            )
            finalWords.add(cw)

            for (step in p.word.indices) {
                val r = p.startRow + if (p.isVertical) step else 0
                val c = p.startCol + if (p.isVertical) 0 else step
                val ch = p.word[step]

                var cell = cellGrid[r][c]
                if (cell == null) {
                    cell = CrosswordCell(
                        row = r,
                        col = c,
                        expectedChar = ch,
                        enteredChar = ' '
                    )
                    cellGrid[r][c] = cell
                }
                cell.wordNumbers.add(wordNumber)
                if (step == 0) {
                    cell.isStartingCell = true
                    if (cell.cellNumber == 0) {
                        cell.cellNumber = wordNumber
                    }
                }
            }
            wordNumber++
        }

        return CrosswordBoard(gridSize, finalWords, cellGrid)
    }
}
