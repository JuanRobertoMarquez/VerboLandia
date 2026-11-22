package com.example.verbolandia.memory

import com.example.verbolandia.model.Verb
import kotlin.random.Random

class MemoryGameEngine(
    private val verbs: List<Verb>,
    val targetTenseLabel: String,
    val isPastTense: Boolean,
    seed: Long? = null
) {
    private val rng = if (seed != null) Random(seed) else Random.Default
    val cards: MutableList<MemoryCard> = mutableListOf()

    var pairsFound: Int = 0
        private set
    val pairsTotal: Int = 6

    var streak: Int = 0
        private set
    var maxStreak: Int = 0
        private set

    private var firstFlippedIndex: Int? = null

    val isGameFinished: Boolean get() = pairsFound >= pairsTotal

    init {
        initializeBoard()
    }

    fun initializeBoard() {
        cards.clear()
        pairsFound = 0
        streak = 0
        firstFlippedIndex = null

        val selectedVerbs = verbs.shuffled(rng).take(pairsTotal)
        var cardId = 0

        for (verb in selectedVerbs) {
            val conjugatedWord = if (isPastTense) verb.past.uppercase() else verb.pastParticiple.uppercase()
            val conjugatedPhonetic = if (isPastTense) verb.ipaPast else verb.ipaParticiple
            val badgeTarget = if (isPastTense) "V2 PASADO" else "V3 PART."

            // Card 1: Base verb
            cards.add(
                MemoryCard(
                    id = cardId++,
                    pairId = verb.id,
                    verbBase = verb.base,
                    role = MemoryCardRole.BASE_VERB,
                    displayText = verb.base.uppercase(),
                    phonetic = verb.ipaBase.ifEmpty { verb.phonetic },
                    badgeLabel = "V1 BASE",
                    meaning = verb.spanishMeaning
                )
            )

            // Card 2: Conjugated form + meaning
            cards.add(
                MemoryCard(
                    id = cardId++,
                    pairId = verb.id,
                    verbBase = verb.base,
                    role = MemoryCardRole.CONJUGATED_FORM,
                    displayText = conjugatedWord,
                    phonetic = conjugatedPhonetic,
                    badgeLabel = badgeTarget,
                    meaning = verb.spanishMeaning
                )
            )
        }

        cards.shuffle(rng)
    }

    fun onCardSelected(index: Int): MemoryFlipResult {
        if (index !in cards.indices) return MemoryFlipResult.Ignored
        val card = cards[index]
        if (card.isMatched || card.isFaceUp) return MemoryFlipResult.Ignored

        card.isFaceUp = true

        val firstIdx = firstFlippedIndex
        if (firstIdx == null) {
            firstFlippedIndex = index
            return MemoryFlipResult.FirstCardFlipped(card)
        }

        // Second card flipped
        val firstCard = cards[firstIdx]
        firstFlippedIndex = null

        return if (firstCard.pairId == card.pairId && firstCard.role != card.role) {
            // Match found!
            firstCard.isMatched = true
            card.isMatched = true
            pairsFound++
            streak++
            if (streak > maxStreak) {
                maxStreak = streak
            }
            MemoryFlipResult.MatchFound(firstCard, card, streak)
        } else {
            // No match
            streak = 0
            MemoryFlipResult.NoMatch(firstCard, card)
        }
    }

    fun turnCardsFaceDown(index1: Int, index2: Int) {
        if (index1 in cards.indices && !cards[index1].isMatched) {
            cards[index1].isFaceUp = false
        }
        if (index2 in cards.indices && !cards[index2].isMatched) {
            cards[index2].isFaceUp = false
        }
    }

    fun shuffleUnmatchedCards() {
        val unmatchedCards = cards.filter { !it.isMatched }.shuffled(rng)
        var uIdx = 0
        for (i in cards.indices) {
            if (!cards[i].isMatched) {
                cards[i] = unmatchedCards[uIdx++]
                cards[i].isFaceUp = false
            }
        }
        firstFlippedIndex = null
    }

    fun getUnmatchedPairVerb(): Verb? {
        val unmatchedPairId = cards.find { !it.isMatched }?.pairId ?: return null
        return verbs.find { it.id == unmatchedPairId }
    }
}
