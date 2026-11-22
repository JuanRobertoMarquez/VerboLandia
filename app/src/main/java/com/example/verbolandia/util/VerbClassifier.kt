package com.example.verbolandia.util

import com.example.verbolandia.model.Verb

enum class VerbTypology {
    REGULAR_ED,
    IRREGULAR_INVARIABLE,
    IRREGULAR_VOWEL_CHANGE
}

object VerbClassifier {

    private val A1_VERBS = setOf(
        "be", "have", "do", "go", "say", "get", "make", "see", "come", "take",
        "know", "think", "look", "want", "give", "use", "find", "tell", "ask",
        "work", "seem", "feel", "try", "leave", "call", "wait", "talk", "play",
        "help", "start", "live", "hear", "read", "open", "close", "love", "like",
        "walk", "dance", "listen", "watch", "stop", "cook", "clean", "run", "sit",
        "stand", "eat", "sleep", "drink", "buy", "pay", "meet", "speak"
    )

    private val A2_VERBS = setOf(
        "begin", "bring", "catch", "choose", "cut", "draw", "drive", "fall",
        "fly", "forget", "happen", "keep", "learn", "lose", "put", "remain",
        "remember", "send", "sing", "spend", "swim", "teach", "understand",
        "win", "write", "build", "sell", "continue", "show", "arrive"
    )

    private val B1_VERBS = setOf(
        "break", "achieve", "accept", "admit", "agree", "allow", "appear",
        "become", "believe", "bite", "blow", "consider", "create", "decide",
        "develop", "expect", "explain", "fight", "grow", "hide", "hit", "hold",
        "hope", "hurt", "lead", "mean", "move", "offer", "pass", "reach",
        "serve", "stay", "suggest", "tend"
    )

    fun getLevel(base: String, isIrregular: Boolean): String {
        val clean = base.trim().lowercase()
        return when {
            A1_VERBS.contains(clean) -> "A1"
            A2_VERBS.contains(clean) -> "A2"
            B1_VERBS.contains(clean) -> "B1"
            isIrregular -> "B1"
            else -> "B2"
        }
    }

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

    fun generateCommonError(context: android.content.Context, verb: Verb): String? {
        if (!verb.isIrregular) return null
        val regularizedPast = if (verb.base.endsWith("e")) "${verb.base}d" else "${verb.base}ed"
        return context.getString(
            com.example.verbolandia.R.string.error_warning_format,
            regularizedPast,
            verb.past,
            verb.pastParticiple
        )
    }
}
