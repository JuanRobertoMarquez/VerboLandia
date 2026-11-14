package com.example.verbolandia

import com.example.verbolandia.model.PhrasalVerb
import com.example.verbolandia.model.Verb
import com.example.verbolandia.model.VerbTense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerbModelsTest {

    @Test
    fun testVerbDefaultValuesAndTenses() {
        val verb = Verb(
            id = 1,
            base = "break",
            thirdPerson = "breaks",
            past = "broke",
            pastParticiple = "broken",
            gerund = "breaking",
            isIrregular = true,
            spanishMeaning = "Romper, quebrar",
            ipaBase = "/breɪk/",
            ipaPast = "/broʊk/",
            ipaParticiple = "/ˈbroʊkən/",
            morphGroup = "vowel_change_en",
            phoneticGroupId = 2,
            commonErrorTip = "Evita 'broked' o 'have broke'."
        )

        assertEquals("break", verb.base)
        assertTrue(verb.isIrregular)
        assertEquals("/breɪk/", verb.ipaBase)
        assertEquals("vowel_change_en", verb.morphGroup)
        assertTrue(verb.tenses.isEmpty())
        assertTrue(verb.phrasalVerbs.isEmpty())
    }

    @Test
    fun testVerbTenseAndPhrasalVerbAssociation() {
        val tense = VerbTense(
            id = 1,
            verbId = 1,
            tenseCode = "PRESENT_SIMPLE",
            tenseNameEn = "Present Simple",
            tenseNameEs = "Presente Simple",
            affirmativeEn = "She breaks the record.",
            negativeEn = "She doesn't break the record.",
            interrogativeEn = "Does she break the record?",
            exampleEs = "Ella rompe el récord."
        )

        val phrasal = PhrasalVerb(
            id = 1,
            verbId = 1,
            phrasalEn = "break down",
            meaningEs = "Averiarse, descomponerse",
            exampleEn = "The car broke down on the highway.",
            exampleEs = "El coche se averió en la autopista."
        )

        val verb = Verb(
            id = 1,
            base = "break",
            thirdPerson = "breaks",
            past = "broke",
            pastParticiple = "broken",
            gerund = "breaking",
            tenses = listOf(tense),
            phrasalVerbs = listOf(phrasal)
        )

        assertEquals(1, verb.tenses.size)
        assertEquals("PRESENT_SIMPLE", verb.tenses[0].tenseCode)
        assertEquals("break down", verb.phrasalVerbs[0].phrasalEn)
    }
}
