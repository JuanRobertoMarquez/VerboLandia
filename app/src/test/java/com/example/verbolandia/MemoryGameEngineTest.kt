package com.example.verbolandia

import com.example.verbolandia.memory.MemoryCardRole
import com.example.verbolandia.memory.MemoryFlipResult
import com.example.verbolandia.memory.MemoryGameEngine
import com.example.verbolandia.model.Verb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryGameEngineTest {

    private fun sampleVerbs(): List<Verb> {
        return listOf(
            Verb(id = 1, base = "freeze", thirdPerson = "freezes", past = "froze", pastParticiple = "frozen", gerund = "freezing", spanishMeaning = "Congelar"),
            Verb(id = 2, base = "speak", thirdPerson = "speaks", past = "spoke", pastParticiple = "spoken", gerund = "speaking", spanishMeaning = "Hablar"),
            Verb(id = 3, base = "choose", thirdPerson = "chooses", past = "chose", pastParticiple = "chosen", gerund = "choosing", spanishMeaning = "Elegir"),
            Verb(id = 4, base = "write", thirdPerson = "writes", past = "wrote", pastParticiple = "written", gerund = "writing", spanishMeaning = "Escribir"),
            Verb(id = 5, base = "catch", thirdPerson = "catches", past = "caught", pastParticiple = "caught", gerund = "catching", spanishMeaning = "Atrapar"),
            Verb(id = 6, base = "sleep", thirdPerson = "sleeps", past = "slept", pastParticiple = "slept", gerund = "sleeping", spanishMeaning = "Dormir")
        )
    }

    @Test
    fun testBoardInitializationCreatesTwelveCards() {
        val verbs = sampleVerbs()
        val engine = MemoryGameEngine(verbs, "Pasado Simple (V2)", true, seed = 999L)

        assertEquals(12, engine.cards.size)
        assertEquals(0, engine.pairsFound)
        assertEquals(0, engine.streak)
        assertEquals(6, engine.pairsTotal)
    }

    @Test
    fun testMatchingPairDetectionAndStreakIncrement() {
        val verbs = sampleVerbs()
        val engine = MemoryGameEngine(verbs, "Pasado Simple (V2)", true, seed = 999L)

        // Find a matching pair in the board
        val cardAIndex = engine.cards.indexOfFirst { it.pairId == 1 && it.role == MemoryCardRole.BASE_VERB }
        val cardBIndex = engine.cards.indexOfFirst { it.pairId == 1 && it.role == MemoryCardRole.CONJUGATED_FORM }

        assertTrue(cardAIndex >= 0)
        assertTrue(cardBIndex >= 0)

        val firstResult = engine.onCardSelected(cardAIndex)
        assertTrue(firstResult is MemoryFlipResult.FirstCardFlipped)

        val secondResult = engine.onCardSelected(cardBIndex)
        assertTrue("Second flip must find match", secondResult is MemoryFlipResult.MatchFound)
        assertEquals(1, engine.pairsFound)
        assertEquals(1, engine.streak)
        assertEquals(1, engine.maxStreak)
    }

    @Test
    fun testNonMatchingCardsResetStreak() {
        val verbs = sampleVerbs()
        val engine = MemoryGameEngine(verbs, "Pasado Simple (V2)", true, seed = 999L)

        val card1 = engine.cards.indexOfFirst { it.pairId == 1 }
        val card2 = engine.cards.indexOfFirst { it.pairId == 2 }

        engine.onCardSelected(card1)
        val result = engine.onCardSelected(card2)

        assertTrue(result is MemoryFlipResult.NoMatch)
        assertEquals(0, engine.streak)
        assertEquals(0, engine.pairsFound)
    }
}
