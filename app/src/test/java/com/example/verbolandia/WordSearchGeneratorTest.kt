package com.example.verbolandia

import com.example.verbolandia.wordsearch.WordSearchCandidate
import com.example.verbolandia.wordsearch.WordSearchGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSearchGeneratorTest {

    private fun sampleCandidates(): List<WordSearchCandidate> {
        return listOf(
            WordSearchCandidate(1, "speak", "SPOKE", "Pasado Simple (V2)", "Hablar", "/spiːk/ → /spoʊk/", "No agregar -ed"),
            WordSearchCandidate(2, "catch", "CAUGHT", "Pasado Simple (V2)", "Atrapar", "/kætʃ/ → /kɔːt/", "gh muda"),
            WordSearchCandidate(3, "blow", "BLEW", "Pasado Simple (V2)", "Soplar", "/bloʊ/ → /bluː/", "Terminación en -ew"),
            WordSearchCandidate(4, "sleep", "SLEPT", "Pasado Simple (V2)", "Dormir", "/sliːp/ → /slɛpt/", "Cambio de vocal /iː/ a /ɛ/"),
            WordSearchCandidate(5, "choose", "CHOSE", "Pasado Simple (V2)", "Elegir", "/tʃuːz/ → /tʃoʊz/", "Una sola \'o\' en pasado"),
            WordSearchCandidate(6, "write", "WROTE", "Pasado Simple (V2)", "Escribir", "/raɪt/ → /roʊt/", "w muda")
        )
    }

    @Test
    fun testGenerateWordSearchBoardProducesValidGrid() {
        val candidates = sampleCandidates()
        val board = WordSearchGenerator.generate(
            candidates = candidates,
            targetCount = 4,
            targetTenseTitle = "Pasado Simple (V2)",
            gridSize = 8,
            seed = 12345L
        )

        assertNotNull(board)
        assertEquals(8, board.size)
        assertEquals(4, board.targetWords.size)

        // Verify that every letter of each target word exists in the grid at the exact coordinates
        for (target in board.targetWords) {
            val cells = target.getCells()
            assertEquals("Word cells length must match targetWord", target.length, cells.size)

            val extractedLetters = StringBuilder()
            for ((r, c) in cells) {
                assertTrue("Row must be within grid bounds", r in 0..7)
                assertTrue("Col must be within grid bounds", c in 0..7)
                extractedLetters.append(board.grid[r][c])
            }

            assertEquals("Letters in grid must spell target word", target.targetWord, extractedLetters.toString())
        }

        // Verify that no cell is empty
        for (r in 0..7) {
            for (c in 0..7) {
                val ch = board.grid[r][c]
                assertTrue("Cell must have valid uppercase letter", ch in 'A'..'Z')
            }
        }
    }
}
