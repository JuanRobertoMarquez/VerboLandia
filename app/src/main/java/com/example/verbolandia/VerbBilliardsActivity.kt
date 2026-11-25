package com.example.verbolandia

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityVerbBilliardsBinding
import com.example.verbolandia.model.TriadVerbResult
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.AudioSpeaker
import com.example.verbolandia.util.NotificationHelper
import com.example.verbolandia.view.PoolTableView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VerbBilliardsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ERROR_MODE = "extra_error_mode"
        private const val MAX_ROUNDS = 15
    }

    private lateinit var binding: ActivityVerbBilliardsBinding
    private lateinit var repository: VerbRepository
    private var currentVerb: Verb? = null

    private var scoreCount = 0
    private var streakCount = 0
    private var maxStreakCount = 0
    private var roundCount = 0
    private var isAnswered = false
    private var sessionStartTimeMs = 0L

    private var isErrorMode = false
    private var errorVerbsPool = listOf<Verb>()

    private val resultsList = arrayListOf<TriadVerbResult>()
    private var currentShuffledPockets = listOf<String>()
    private var currentCorrectAnswer = ""

    private enum class TenseType {
        PAST,           // Past Simple
        PARTICIPLE,     // Past Participle
        GERUND,         // Present Continuous / gerund
        THIRD_PERSON    // 3ra persona singular
    }

    private var currentTense: TenseType = TenseType.PAST

    private val activePocketCount: Int
        get() = if (roundCount > 5) 6 else 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVerbBilliardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionStartTimeMs = System.currentTimeMillis()
        isErrorMode = intent.getBooleanExtra(EXTRA_ERROR_MODE, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        repository = VerbRepository.getInstance(this)
        AudioSpeaker.init(this)

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.poolTableView.onPocketSunkListener = object : PoolTableView.OnPocketSunkListener {
            override fun onPocketSunk(pocketIndex: Int) {
                if (isAnswered || currentShuffledPockets.isEmpty()) return

                val selectedText = currentShuffledPockets.getOrElse(pocketIndex) { "" }
                val cards = getPocketCards()
                val selectedCard = cards.getOrElse(pocketIndex) { binding.cardPocketTopLeft }

                currentVerb?.let { verb ->
                    onPocketShot(selectedText, currentCorrectAnswer, verb, selectedCard)
                }
            }
        }

        updateBilliardsHeader()
        loadNextBilliardsRound()
    }

    private fun pickRandomTense(): TenseType {
        return TenseType.entries.toTypedArray().random()
    }

    private fun getCorrectAnswer(verb: Verb, tense: TenseType): String {
        return when (tense) {
            TenseType.PAST -> verb.past
            TenseType.PARTICIPLE -> verb.pastParticiple
            TenseType.GERUND -> verb.gerund
            TenseType.THIRD_PERSON -> verb.thirdPerson
        }
    }

    private fun getQuestionText(verb: Verb, tense: TenseType): String {
        return when (tense) {
            TenseType.PAST -> getString(R.string.billiards_question_past, verb.base.uppercase())
            TenseType.PARTICIPLE -> getString(R.string.billiards_question_participle, verb.base.uppercase())
            TenseType.GERUND -> getString(R.string.billiards_question_gerund, verb.base.uppercase())
            TenseType.THIRD_PERSON -> getString(R.string.billiards_question_third, verb.base.uppercase())
        }
    }

    private fun generateDistractors(verb: Verb, tense: TenseType): List<String> {
        val allForms = listOf(
            verb.past,
            verb.pastParticiple,
            verb.gerund,
            verb.thirdPerson,
        )
        val synthetic = listOf(
            if (verb.base.endsWith("e")) "${verb.base}d" else "${verb.base}ed",
            "${verb.base}ing",
            "${verb.base}s",
            "was ${verb.base}",
            "has ${verb.base}",
            "${verb.base}es"
        )

        val correctAnswer = getCorrectAnswer(verb, tense)
        val distractors = mutableListOf<String>()

        for (form in allForms) {
            if (form != correctAnswer && form !in distractors) {
                distractors.add(form)
            }
        }
        for (form in synthetic) {
            if (form != correctAnswer && form !in distractors) {
                distractors.add(form)
            }
        }

        return distractors
    }

    private fun getPocketCards(): List<MaterialCardView> {
        val cards = mutableListOf(
            binding.cardPocketTopLeft,
            binding.cardPocketTopRight,
            binding.cardPocketBottomLeft,
            binding.cardPocketBottomRight
        )
        if (activePocketCount >= 6) {
            cards.add(binding.cardPocketMidLeft)
            cards.add(binding.cardPocketMidRight)
        }
        return cards
    }

    private fun getPocketTextViews(): List<android.widget.TextView> {
        val tvs = mutableListOf(
            binding.tvPocketTopLeftText,
            binding.tvPocketTopRightText,
            binding.tvPocketBottomLeftText,
            binding.tvPocketBottomRightText
        )
        if (activePocketCount >= 6) {
            tvs.add(binding.tvPocketMidLeftText)
            tvs.add(binding.tvPocketMidRightText)
        }
        return tvs
    }

    private fun loadNextBilliardsRound() {
        if (roundCount >= MAX_ROUNDS) {
            finishSessionAndShowSummary()
            return
        }

        isAnswered = false
        roundCount++

        try {
            binding.cardFeedbackOverlay.visibility = View.GONE
            binding.poolTableView.resetCueBall()
            binding.poolTableView.activePocketCount = activePocketCount

            currentTense = pickRandomTense()

            val darkBg = ContextCompat.getColor(this, R.color.inverse_surface)
            binding.cardPocketTopLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketTopRight.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomRight.setCardBackgroundColor(darkBg)
            binding.cardPocketMidLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketMidRight.setCardBackgroundColor(darkBg)

            if (activePocketCount >= 6) {
                binding.cardPocketMidLeft.visibility = View.VISIBLE
                binding.cardPocketMidRight.visibility = View.VISIBLE
            } else {
                binding.cardPocketMidLeft.visibility = View.GONE
                binding.cardPocketMidRight.visibility = View.GONE
            }

            updateBilliardsHeader()

            lifecycleScope.launch {
                val candidateVerbs = try {
                    if (isErrorMode) {
                        if (errorVerbsPool.isEmpty()) {
                            errorVerbsPool = repository.getErrorVerbs()
                        }
                        if (errorVerbsPool.isNotEmpty()) {
                            errorVerbsPool
                        } else {
                            Toast.makeText(this@VerbBilliardsActivity, getString(R.string.snackbar_generating_test_errors), Toast.LENGTH_SHORT).show()
                            repository.getDailyVerbs("ALL")
                        }
                    } else {
                        repository.getDailyVerbs("ALL")
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                val targetVerb = if (candidateVerbs.isNotEmpty()) {
                    candidateVerbs.random()
                } else {
                    Verb(
                        base = "freeze",
                        thirdPerson = "freezes",
                        past = "froze",
                        pastParticiple = "frozen",
                        gerund = "freezing",
                        spanishMeaning = "Congelar"
                    )
                }
                currentVerb = targetVerb

                // Muestra la tarjeta del verbo y pregunta al inicio de la ronda
                binding.tvTargetVerbBase.text = targetVerb.base.uppercase()
                binding.tvTargetVerbPrompt.text =
                    getString(R.string.meaning_prompt_format, targetVerb.spanishMeaning)

                val promptQuestion = getQuestionText(targetVerb, currentTense)
                val prefix = if (isErrorMode) "Test de Errores" else "Pregunta"
                binding.tvQuestionBanner.text = "($roundCount/$MAX_ROUNDS) $prefix · $promptQuestion"

                val correctAnswer = getCorrectAnswer(targetVerb, currentTense)
                currentCorrectAnswer = correctAnswer
                val distractors = generateDistractors(targetVerb, currentTense)
                val numPockets = activePocketCount

                val pocketList = mutableListOf(correctAnswer)
                for (d in distractors) {
                    if (d !in pocketList && pocketList.size < numPockets) {
                        pocketList.add(d)
                    }
                }
                var fillIdx = 1
                while (pocketList.size < numPockets) {
                    val filler = "opcion_$fillIdx"
                    if (filler !in pocketList) {
                        pocketList.add(filler)
                    }
                    fillIdx++
                }

                val shuffledPockets = pocketList.shuffled()
                currentShuffledPockets = shuffledPockets

                val textViews = getPocketTextViews()
                val cards = getPocketCards()

                for (i in 0 until numPockets) {
                    if (i < textViews.size) {
                        textViews[i].text = shuffledPockets[i]
                    }
                    if (i < cards.size) {
                        cards[i].setOnClickListener {
                            onPocketShot(
                                shuffledPockets[i],
                                correctAnswer,
                                targetVerb,
                                cards[i]
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onPocketShot(
        selectedText: String,
        correctAnswer: String,
        targetVerb: Verb,
        selectedCard: MaterialCardView
    ) {
        if (isAnswered) return
        isAnswered = true

        try {
            val isCorrect = selectedText.trim().equals(correctAnswer.trim(), ignoreCase = true)
            AudioSpeaker.speak(this, targetVerb.base)

            lifecycleScope.launch {
                repository.recordPracticeAttempt(targetVerb.base, isCorrect, "Billar de Verbos")
            }

            resultsList.add(
                TriadVerbResult(
                    verb = targetVerb,
                    isCorrect = isCorrect,
                    wrongSelection = if (!isCorrect) selectedText else "",
                    correctExpected = correctAnswer,
                    timeSpentSeconds = 3
                )
            )

            if (isCorrect) {
                scoreCount++
                streakCount++
                maxStreakCount = maxOf(maxStreakCount, streakCount)
                updateBilliardsHeader()
                selectedCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.primary)
                )

                binding.ivFeedbackIcon.setImageResource(R.drawable.ic_check)
                binding.ivFeedbackIcon.setColorFilter(Color.parseColor("#22C55E"))
                binding.tvFeedbackTitle.text = getString(R.string.billiards_feedback_correct)
                binding.tvFeedbackTitle.setTextColor(Color.parseColor("#22C55E"))
                binding.cardFeedbackOverlay.setStrokeColor(Color.parseColor("#22C55E"))
                binding.tvFeedbackBody.text = getString(R.string.phonetic_transition_format, targetVerb.base.uppercase(), correctAnswer)

                NotificationHelper.showVerbOfDayNotification(this, targetVerb)
            } else {
                streakCount = 0
                updateBilliardsHeader()
                selectedCard.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.error)
                )

                binding.ivFeedbackIcon.setImageResource(R.drawable.ic_warning)
                binding.ivFeedbackIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvFeedbackTitle.text = getString(R.string.billiards_feedback_incorrect)
                binding.tvFeedbackTitle.setTextColor(Color.parseColor("#EF4444"))
                binding.cardFeedbackOverlay.setStrokeColor(Color.parseColor("#EF4444"))
                binding.tvFeedbackBody.text = getString(R.string.triad_summary_verb_subtitle_format, correctAnswer, selectedText)
            }

            binding.cardFeedbackOverlay.visibility = View.VISIBLE
            binding.cardFeedbackOverlay.alpha = 0f
            binding.cardFeedbackOverlay.animate().alpha(1f).setDuration(220).start()

            lifecycleScope.launch {
                delay(1800)
                if (!isFinishing && !isDestroyed) {
                    loadNextBilliardsRound()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBilliardsHeader() {
        val pointsStr = getString(R.string.billiards_points_format, scoreCount)
        binding.tvScoreBadge.text = pointsStr

        currentVerb?.let { verb ->
            val promptQuestion = getQuestionText(verb, currentTense)
            val currentRoundDisplay = if (roundCount == 0) 1 else roundCount
            val prefix = if (isErrorMode) "Test de Errores" else "Pregunta"
            binding.tvQuestionBanner.text = "($currentRoundDisplay/$MAX_ROUNDS) $prefix · $promptQuestion"
        }
    }

    private fun finishSessionAndShowSummary() {
        val sessionTimeSeconds = maxOf(1, ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt())
        val comboBonus = maxStreakCount * 5
        val totalExp = (scoreCount * 15) + comboBonus

        val intent = Intent(this, QuickTriadSummaryActivity::class.java).apply {
            putExtra(QuickTriadSummaryActivity.EXTRA_EXP, totalExp)
            putExtra(QuickTriadSummaryActivity.EXTRA_CORRECT, scoreCount)
            putExtra(QuickTriadSummaryActivity.EXTRA_TOTAL, MAX_ROUNDS)
            putExtra(QuickTriadSummaryActivity.EXTRA_MAX_STREAK, maxStreakCount)
            putExtra(QuickTriadSummaryActivity.EXTRA_TIME_SECONDS, sessionTimeSeconds)
            putExtra(QuickTriadSummaryActivity.EXTRA_COMBO_BONUS, comboBonus)
            putExtra(QuickTriadSummaryActivity.EXTRA_RESULTS, resultsList)
        }
        startActivity(intent)
        finish()
    }
}
