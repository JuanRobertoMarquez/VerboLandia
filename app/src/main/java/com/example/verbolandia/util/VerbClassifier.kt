package com.example.verbolandia.util

import com.example.verbolandia.model.Verb

enum class VerbTypology {
    REGULAR_ED,
    IRREGULAR_INVARIABLE,
    IRREGULAR_VOWEL_CHANGE
}

object VerbClassifier {

    fun classify(verb: Verb): VerbTypology {
        if (!verb.isIrregular) {
            return VerbTypology.REGULAR_ED
        }
        val cleanBase = verb.base.trim().lowercase()
        val cleanPast = verb.past.trim().lowercase()
        val cleanParticiple = verb.pastParticiple.trim().lowercase()

        return if (cleanBase == cleanPast && cleanPast == cleanParticiple) {
            VerbTypology.IRREGULAR_INVARIABLE
        } else {
            VerbTypology.IRREGULAR_VOWEL_CHANGE
        }
    }

    fun generateCommonError(verb: Verb): String? {
        if (!verb.isIrregular) return null
        val regularizedPast = if (verb.base.endsWith("e")) "${verb.base}d" else "${verb.base}ed"
        return "NUNCA digas \"$regularizedPast\" ni \"have ${verb.past}\". Con los auxiliares have/has/had la forma obligatoria siempre es ${verb.pastParticiple}."
    }
}
