package com.example.verbolandia

import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VerbBilliardsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerbBilliardsBinding
    private lateinit var repository: VerbRepository
    private var currentVerb: Verb? = null

    private var scoreCount = 0
    private var streakCount = 0
    private var isAnswered = false

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

        binding.btnNextVerb.setOnClickListener {
            loadNextBilliardsRound()
        }

        loadNextBilliardsRound()
    }

    private fun loadNextBilliardsRound() {
        isAnswered = false
        try {
            binding.tvBilliardsFeedback.text = "Tira a la tronera con el pasado del verbo."
            binding.tvBilliardsFeedback.setTextColor(ContextCompat.getColor(this, R.color.white))

            val darkBg = ContextCompat.getColor(this, R.color.inverse_surface)
            binding.cardPocketTopLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketTopRight.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomLeft.setCardBackgroundColor(darkBg)
            binding.cardPocketBottomRight.setCardBackgroundColor(darkBg)

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
                val option1 = correctAnswer
                val option2 = targetVerb.gerund
                val option3 = if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
                val option4 = targetVerb.thirdPerson

                val pocketList = mutableListOf(option1)
                val distractors = listOf(option2, option3, option4, "${targetVerb.base}ing", "${targetVerb.base}s")
                for (d in distractors) {
                    if (d !in pocketList && pocketList.size < 4) {
                        pocketList.add(d)
                    }
                }
                while (pocketList.size < 4) {
                    pocketList.add("opcion_${pocketList.size + 1}")
                }

                val shuffledPockets = pocketList.shuffled()

                binding.tvPocketTopLeftText.text = shuffledPockets[0]
                binding.tvPocketTopRightText.text = shuffledPockets[1]
                binding.tvPocketBottomLeftText.text = shuffledPockets[2]
                binding.tvPocketBottomRightText.text = shuffledPockets[3]

                binding.cardPocketTopLeft.setOnClickListener { onPocketShot(shuffledPockets[0], correctAnswer, targetVerb, binding.cardPocketTopLeft) }
                binding.cardPocketTopRight.setOnClickListener { onPocketShot(shuffledPockets[1], correctAnswer, targetVerb, binding.cardPocketTopRight) }
                binding.cardPocketBottomLeft.setOnClickListener { onPocketShot(shuffledPockets[2], correctAnswer, targetVerb, binding.cardPocketBottomLeft) }
                binding.cardPocketBottomRight.setOnClickListener { onPocketShot(shuffledPockets[3], correctAnswer, targetVerb, binding.cardPocketBottomRight) }
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
                binding.tvScore.text = "Aciertos: $scoreCount | 🔥 $streakCount"
                selectedCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                binding.tvBilliardsFeedback.text = "¡TIRO PERFECTO! ${targetVerb.base} ➔ $correctAnswer"

                NotificationHelper.showVerbOfDayNotification(this, targetVerb)

                Snackbar.make(binding.root, "¡Encestaste en la tronera correcta! +10 pts", Snackbar.LENGTH_LONG)
                    .setAction("NOTIFICACIÓN") {
                        NotificationHelper.showVerbOfDayNotification(this, targetVerb)
                    }
                    .show()
            } else {
                streakCount = 0
                binding.tvScore.text = "Aciertos: $scoreCount | 🔥 0"
                selectedCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error))
                binding.tvBilliardsFeedback.text = "¡Fallaste la tronera! El pasado de ${targetVerb.base} es $correctAnswer"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
