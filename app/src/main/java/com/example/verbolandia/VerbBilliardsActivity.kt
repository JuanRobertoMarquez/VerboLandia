package com.example.verbolandia

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityVerbBilliardsBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.AudioSpeaker
import com.example.verbolandia.util.NotificationHelper
import com.example.verbolandia.view.PoolTableView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VerbBilliardsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerbBilliardsBinding
    private lateinit var repository: VerbRepository
    private var currentVerb: Verb? = null

    private var roundNumber = 1
    private var scoreCount = 0
    private var streakCount = 0
    private var isAnswered = false
    private var currentShuffledPockets = listOf<String>()
    private var currentCorrectAnswer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVerbBilliardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.poolTableView.onPocketSunkListener = object : PoolTableView.OnPocketSunkListener {
            override fun onPocketSunk(pocketIndex: Int) {
                if (isAnswered || currentShuffledPockets.isEmpty()) return

                val selectedText = currentShuffledPockets.getOrElse(pocketIndex) { "" }
                val selectedCard = when (pocketIndex) {
                    0 -> binding.cardPocketTopLeft
                    1 -> binding.cardPocketTopRight
                    2 -> binding.cardPocketBottomLeft
                    3 -> binding.cardPocketBottomRight
                    4 -> binding.cardPocketMidLeft
                    else -> binding.cardPocketMidRight
                }
                currentVerb?.let { verb ->
                    onPocketShot(selectedText, currentCorrectAnswer, verb, selectedCard)
                }
            }
        }

        loadNextBilliardsRound()
    }

    private fun loadNextBilliardsRound() {
        isAnswered = false
        try {
            binding.cardFeedbackOverlay.visibility = View.GONE
            binding.poolTableView.resetCueBall()

            // Progressive Difficulty: 4 pockets (rounds 1-5) vs 6 pockets (round 6+)
            val isAdvancedDifficulty = roundNumber > 5
            val pocketCount = if (isAdvancedDifficulty) 6 else 4

            binding.poolTableView.activePocketCount = pocketCount

            if (isAdvancedDifficulty) {
                binding.cardPocketMidLeft.visibility = View.VISIBLE
                binding.cardPocketMidRight.visibility = View.VISIBLE
                binding.badgeDifficultyLevel.text = "Nivel $roundNumber • 6 Troneras ⭐"
            } else {
                binding.cardPocketMidLeft.visibility = View.GONE
                binding.cardPocketMidRight.visibility = View.GONE
                binding.badgeDifficultyLevel.text = "Nivel $roundNumber/5 • 4 Troneras"
            }

            val darkBg = ContextCompat.getColor(this, R.color.inverse_surface)
            binding.cardPocketTopLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketTopRight.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomRight.setCardBackgroundColor(darkBg)
            binding.cardPocketMidLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketMidRight.setCardBackgroundColor(darkBg)

            lifecycleScope.launch {
                val verbs = try {
                    repository.getDailyVerbs("ALL")
                } catch (e: Exception) {
                    emptyList()
                }

                val targetVerb = if (verbs.isNotEmpty()) {
                    verbs.random()
                } else {
                    Verb(base = "freeze", thirdPerson = "freezes", past = "froze", pastParticiple = "frozen", gerund = "freezing", spanishMeaning = "Congelar")
                }
                currentVerb = targetVerb

                binding.tvTargetVerbBase.text = targetVerb.base.uppercase()
                binding.tvTargetVerbPrompt.text = "Significado: ${targetVerb.spanishMeaning}"

                val correctAnswer = targetVerb.past
                currentCorrectAnswer = correctAnswer

                val option1 = correctAnswer
                val option2 = targetVerb.gerund
                val option3 = if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
                val option4 = targetVerb.thirdPerson
                val option5 = "${targetVerb.base}ing"
                val option6 = "was ${targetVerb.base}"

                val pocketList = mutableListOf(option1)
                val distractors = listOf(option2, option3, option4, option5, option6, "${targetVerb.base}s")

                for (d in distractors) {
                    if (d !in pocketList && pocketList.size < pocketCount) {
                        pocketList.add(d)
                    }
                }
                while (pocketList.size < pocketCount) {
                    pocketList.add("opcion_${pocketList.size + 1}")
                }

                val shuffledPockets = pocketList.shuffled()
                currentShuffledPockets = shuffledPockets

                binding.tvPocketTopLeftText.text = shuffledPockets[0]
                binding.tvPocketTopRightText.text = shuffledPockets[1]
                binding.tvPocketBottomLeftText.text = shuffledPockets[2]
                binding.tvPocketBottomRightText.text = shuffledPockets[3]

                if (pocketCount >= 6) {
                    binding.tvPocketMidLeftText.text = shuffledPockets.getOrElse(4) { "" }
                    binding.tvPocketMidRightText.text = shuffledPockets.getOrElse(5) { "" }
                }

                // Direct tap for accessibility
                binding.cardPocketTopLeft.setOnClickListener { onPocketShot(shuffledPockets[0], correctAnswer, targetVerb, binding.cardPocketTopLeft) }
                binding.cardPocketTopRight.setOnClickListener { onPocketShot(shuffledPockets[1], correctAnswer, targetVerb, binding.cardPocketTopRight) }
                binding.cardPocketBottomLeft.setOnClickListener { onPocketShot(shuffledPockets[2], correctAnswer, targetVerb, binding.cardPocketBottomLeft) }
                binding.cardPocketBottomRight.setOnClickListener { onPocketShot(shuffledPockets[3], correctAnswer, targetVerb, binding.cardPocketBottomRight) }

                if (pocketCount >= 6) {
                    binding.cardPocketMidLeft.setOnClickListener { onPocketShot(shuffledPockets[4], correctAnswer, targetVerb, binding.cardPocketMidLeft) }
                    binding.cardPocketMidRight.setOnClickListener { onPocketShot(shuffledPockets[5], correctAnswer, targetVerb, binding.cardPocketMidRight) }
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
        selectedCard: com.google.android.material.card.MaterialCardView
    ) {
        if (isAnswered) return
        isAnswered = true

        try {
            val isCorrect = selectedText == correctAnswer
            AudioSpeaker.speak(this, targetVerb.base)

            lifecycleScope.launch {
                repository.recordPracticeAttempt(targetVerb.base, isCorrect, "Billar de Verbos")
            }

            if (isCorrect) {
                scoreCount++
                streakCount++
                selectedCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary))

                binding.tvFeedbackEmoji.text = "🎯"
                binding.tvFeedbackTitle.text = "¡TIRO PERFECTO!"
                binding.tvFeedbackTitle.setTextColor(Color.parseColor("#22C55E"))
                binding.cardFeedbackOverlay.setStrokeColor(Color.parseColor("#22C55E"))
                binding.tvFeedbackBody.text = "${targetVerb.base} ➔ $correctAnswer"

                NotificationHelper.showVerbOfDayNotification(this, targetVerb)
            } else {
                streakCount = 0
                selectedCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error))

                binding.tvFeedbackEmoji.text = "❌"
                binding.tvFeedbackTitle.text = "¡TIRO INCORRECTO!"
                binding.tvFeedbackTitle.setTextColor(Color.parseColor("#EF4444"))
                binding.cardFeedbackOverlay.setStrokeColor(Color.parseColor("#EF4444"))
                binding.tvFeedbackBody.text = "El pasado de ${targetVerb.base} es $correctAnswer (elegiste $selectedText)"
            }

            binding.cardFeedbackOverlay.visibility = View.VISIBLE
            binding.cardFeedbackOverlay.alpha = 0f
            binding.cardFeedbackOverlay.animate().alpha(1f).setDuration(250).start()

            roundNumber++

            // Automatic next round transition after 1.8 seconds delay
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
}
