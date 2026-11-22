package com.example.verbolandia

import com.example.verbolandia.crossword.CrosswordClueCandidate
import com.example.verbolandia.crossword.CrosswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrosswordGeneratorTest {

    private fun sampleCandidates(): List<CrosswordClueCandidate> {
        return listOf(
            CrosswordClueCandidate(
                verbId = 1,
                base = "choose",
                targetWord = "CHOSEN",
                tenseCode = "PAST_PARTICIPLE",
                tenseLabel = "Participio Pasado (V3) de CHOOSE",
                exampleSentence = "They have _______ the best option.",
                spanishMeaning = "Elegir",
                phoneticChange = "/tʃuːz/ → /ˈtʃoʊzn/",
                level = "B1"
            ),
            CrosswordClueCandidate(
                verbId = 2,
                base = "speak",
                targetWord = "SPOKE",
                tenseCode = "PAST_SIMPLE",
                tenseLabel = "Pasado Simple (V2) de SPEAK",
                exampleSentence = "She _______ clearly to the audience.",
                spanishMeaning = "Hablar",
                phoneticChange = "/spiːk/ → /spoʊk/",
                level = "A2"
            ),
            CrosswordClueCandidate(
                verbId = 3,
                base = "blow",
                targetWord = "BLEW",
                tenseCode = "PAST_SIMPLE",
                tenseLabel = "Pasado Simple (V2) de BLOW",
                exampleSentence = "The wind _______ strongly yesterday.",
                spanishMeaning = "Soplar",
                phoneticChange = "/bloʊ/ → /bluː/",
                level = "A2"
            ),
            CrosswordClueCandidate(
                verbId = 4,
                base = "sleep",
                targetWord = "SLEPT",
                tenseCode = "PAST_SIMPLE",
                tenseLabel = "Pasado Simple (V2) de SLEEP",
                exampleSentence = "He _______ peacefully all night.",
                spanishMeaning = "Dormir",
                phoneticChange = "/sliːp/ → /slɛpt/",
                level = "A1"
            ),
            CrosswordClueCandidate(
                verbId = 5,
                base = "write",
                targetWord = "WROTE",
                tenseCode = "PAST_SIMPLE",
                tenseLabel = "Pasado Simple (V2) de WRITE",
                exampleSentence = "I _______ a long letter to my friend.",
                spanishMeaning = "Escribir",
                phoneticChange = "/raɪt/ → /roʊt/",
                level = "A1"
            ),
            CrosswordClueCandidate(
                verbId = 6,
                base = "take",
                targetWord = "TOOK",
                tenseCode = "PAST_SIMPLE",
                tenseLabel = "Pasado Simple (V2) de TAKE",
                exampleSentence = "He _______ the train at noon.",
                spanishMeaning = "Tomar",
                phoneticChange = "/teɪk/ → /tʊk/",
                level = "A1"
            )
        )
    }

    @Test
    fun testGenerateCrosswordBoardProducesValidGrid() {
        val candidates = sampleCandidates()
        val board = CrosswordGenerator.generate(candidates, targetCount = 4, gridSize = 8, seed = 42L)

        assertNotNull(board)
        assertEquals(8, board.size)
        assertTrue("Board must contain at least 4 words", board.words.size >= 4)

        // Verify each word is positioned within bounds and cells match letters
        for (word in board.words) {
            assertTrue("Word must fit in board", word.startRow in 0..7)
            assertTrue("Word must fit in board", word.startCol in 0..7)
            if (word.isVertical) {
                assertTrue(word.startRow + word.length <= 8)
            } else {
                assertTrue(word.startCol + word.length <= 8)
            }

            for (i in 0 until word.length) {
                val r = word.startRow + if (word.isVertical) i else 0
                val c = word.startCol + if (word.isVertical) 0 else i
                val cell = board.getCell(r, c)
                assertNotNull("Cell must exist", cell)
                assertEquals("Cell expected letter must match word letter", word.word[i], cell?.expectedChar)
                assertTrue("Cell must contain word number", cell?.wordNumbers?.contains(word.number) == true)
            }
        }
    }

    @Test
    fun testBoardSolvedDetection() {
        val candidates = sampleCandidates()
        val board = CrosswordGenerator.generate(candidates, targetCount = 4, gridSize = 8, seed = 42L)

        // Initially unsolved
        assertTrue(!board.isFullySolved())

        // Fill all cells with correct characters
        for (word in board.words) {
            for (i in 0 until word.length) {
                val r = word.startRow + if (word.isVertical) i else 0
                val c = word.startCol + if (word.isVertical) 0 else i
                val cell = board.getCell(r, c)
                if (cell != null) {
                    cell.enteredChar = cell.expectedChar
                }
            }
        }

        assertTrue("Board should be solved when all cells match", board.isFullySolved())
    }
}
