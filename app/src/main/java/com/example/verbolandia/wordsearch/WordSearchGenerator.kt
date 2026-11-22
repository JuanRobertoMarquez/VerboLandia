package com.example.verbolandia.wordsearch

import kotlin.random.Random

object WordSearchGenerator {

    const val DEFAULT_GRID_SIZE = 8
    private const val MAX_RETRIES = 60
    private const val FILLER_LETTERS = "EEEEAAAIIIOOOUUUNNNRRRTTTSSSDDLLLCCCMMMWWWFFGGYYPPBBVK"

    fun generate(
        candidates: List<WordSearchCandidate>,
        targetCount: Int = 4,
        targetTenseTitle: String,
        gridSize: Int = DEFAULT_GRID_SIZE,
        seed: Long? = null
    ): WordSearchBoard {
        val rng = if (seed != null) Random(seed) else Random.Default
        val validCandidates = candidates.filter {
            it.targetWord.length in 3..6 && it.targetWord.all { ch -> ch in 'A'..'Z' }
        }

        val pool = if (validCandidates.size >= targetCount) {
            validCandidates
        } else {
            candidates
        }

        for (attempt in 0 until MAX_RETRIES) {
            val shuffled = pool.shuffled(rng).take(targetCount)
            val board = tryPlaceWords(shuffled, targetTenseTitle, gridSize, rng)
            if (board != null) {
                return board
            }
        }

        return generateFallback(pool.take(targetCount), targetTenseTitle, gridSize, rng)
    }

    private fun tryPlaceWords(
        selected: List<WordSearchCandidate>,
        targetTenseTitle: String,
        gridSize: Int,
        rng: Random
    ): WordSearchBoard? {
        val grid = Array(gridSize) { CharArray(gridSize) { ' ' } }
        val placedWords = mutableListOf<WordSearchTargetWord>()

        val directions = WordSearchDirection.values()

        for (cand in selected) {
            val word = cand.targetWord
            val len = word.length

            val validPlacements = mutableListOf<Triple<Int, Int, WordSearchDirection>>()

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    for (dir in directions) {
                        val endR = r + (len - 1) * dir.dRow
                        val endC = c + (len - 1) * dir.dCol

                        if (endR in 0 until gridSize && endC in 0 until gridSize) {
                            var conflict = false
                            for (step in 0 until len) {
                                val curR = r + step * dir.dRow
                                val curC = c + step * dir.dCol
                                val cellChar = grid[curR][curC]
                                if (cellChar != ' ' && cellChar != word[step]) {
                                    conflict = true
                                    break
                                }
                            }
                            if (!conflict) {
                                validPlacements.add(Triple(r, c, dir))
                            }
                        }
                    }
                }
            }

            if (validPlacements.isEmpty()) {
                return null // Try another arrangement
            }

            val (startR, startC, chosenDir) = validPlacements[rng.nextInt(validPlacements.size)]
            for (step in 0 until len) {
                grid[startR + step * chosenDir.dRow][startC + step * chosenDir.dCol] = word[step]
            }

            placedWords.add(
                WordSearchTargetWord(
                    verbId = cand.verbId,
                    base = cand.base,
                    targetWord = word,
                    tenseLabel = cand.tenseLabel,
                    spanishMeaning = cand.spanishMeaning,
                    phoneticTip = cand.phoneticTip,
                    commonErrorTip = cand.commonErrorTip,
                    startRow = startR,
                    startCol = startC,
                    direction = chosenDir
                )
            )
        }

        // Fill remaining spaces with random balanced English characters
        fillEmptyCells(grid, rng)

        return WordSearchBoard(
            size = gridSize,
            grid = grid,
            targetWords = placedWords,
            targetTenseTitle = targetTenseTitle
        )
    }

    private fun generateFallback(
        selected: List<WordSearchCandidate>,
        targetTenseTitle: String,
        gridSize: Int,
        rng: Random
    ): WordSearchBoard {
        val grid = Array(gridSize) { CharArray(gridSize) { ' ' } }
        val placedWords = mutableListOf<WordSearchTargetWord>()

        var row = 0
        for (cand in selected.take(4)) {
            val word = cand.targetWord.take(gridSize)
            val startCol = (gridSize - word.length) / 2
            for (i in word.indices) {
                grid[row][startCol + i] = word[i]
            }
            placedWords.add(
                WordSearchTargetWord(
                    verbId = cand.verbId,
                    base = cand.base,
                    targetWord = word,
                    tenseLabel = cand.tenseLabel,
                    spanishMeaning = cand.spanishMeaning,
                    phoneticTip = cand.phoneticTip,
                    commonErrorTip = cand.commonErrorTip,
                    startRow = row,
                    startCol = startCol,
                    direction = WordSearchDirection.HORIZONTAL
                )
            )
            row = (row + 2).coerceAtMost(gridSize - 1)
        }

        fillEmptyCells(grid, rng)

        return WordSearchBoard(
            size = gridSize,
            grid = grid,
            targetWords = placedWords,
            targetTenseTitle = targetTenseTitle
        )
    }

    private fun fillEmptyCells(grid: Array<CharArray>, rng: Random) {
        val size = grid.size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (grid[r][c] == ' ') {
                    grid[r][c] = FILLER_LETTERS[rng.nextInt(FILLER_LETTERS.length)]
                }
            }
        }
    }
}
