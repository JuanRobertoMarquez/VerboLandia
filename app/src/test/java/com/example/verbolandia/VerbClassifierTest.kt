package com.example.verbolandia

import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.VerbClassifier
import com.example.verbolandia.util.VerbTypology
import org.junit.Assert.assertEquals
import org.junit.Test

class VerbClassifierTest {

    @Test
    fun testClassifyRegularVerb() {
        val walk = Verb(base = "walk", thirdPerson = "walks", past = "walked", pastParticiple = "walked", gerund = "walking", isIrregular = false)
        assertEquals(VerbTypology.REGULAR_ED, VerbClassifier.classify(walk))
    }

    @Test
    fun testClassifyInvariableIrregularVerb() {
        val cut = Verb(base = "cut", thirdPerson = "cuts", past = "cut", pastParticiple = "cut", gerund = "cutting", isIrregular = true)
        assertEquals(VerbTypology.IRREGULAR_INVARIABLE, VerbClassifier.classify(cut))
    }

    @Test
    fun testClassifyVowelChangeIrregularVerb() {
        val go = Verb(base = "go", thirdPerson = "goes", past = "went", pastParticiple = "gone", gerund = "going", isIrregular = true)
        assertEquals(VerbTypology.IRREGULAR_VOWEL_CHANGE, VerbClassifier.classify(go))
    }
}
