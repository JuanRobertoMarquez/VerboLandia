package com.example.verbolandia

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityVerbBilliardsBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.NotificationHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class VerbBilliardsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityVerbBilliardsBinding
    private lateinit var repository: VerbRepository
    private var currentVerb: Verb? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

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
        tts = TextToSpeech(this, this)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnNextVerb.setOnClickListener {
            loadNextBilliardsRound()
        }

        loadNextBilliardsRound()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    private fun speakText(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "billiards_speech")
        } else {
            Toast.makeText(this, "Pronunciando: $text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNextBilliardsRound() {
        isAnswered = false
        binding.tvBilliardsFeedback.text = "Tira a la tronera con el pasado del verbo."
        binding.tvBilliardsFeedback.setTextColor(ContextCompat.getColor(this, R.color.white))

        val darkBg = ContextCompat.getColor(this, R.color.inverse_surface)
        binding.cardPocketTopLeft.setCardBackgroundColor(darkBg)
        binding.cardPocketTopRight.setCardBackgroundColor(darkBg)
        binding.cardPocketBottomLeft.setCardBackgroundColor(darkBg)
        binding.cardPocketBottomRight.setCardBackgroundColor(darkBg)

        lifecycleScope.launch {
            val verbs = repository.getDailyVerbs("ALL")
            if (verbs.isEmpty()) return@launch

            val targetVerb = verbs.random()
            currentVerb = targetVerb

            binding.tvTargetVerbBase.text = targetVerb.base.uppercase()
            binding.tvTargetVerbPrompt.text = "Significado: ${targetVerb.spanishMeaning}"

            val correctAnswer = targetVerb.past
            val distractor1 = targetVerb.gerund
            val distractor2 = if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
            val distractor3 = targetVerb.thirdPerson

            val options = listOf(correctAnswer, distractor1, distractor2, distractor3)
                .distinct()
                .shuffled()

            val pocket1 = options.getOrElse(0) { correctAnswer }
            val pocket2 = options.getOrElse(1) { distractor1 }
            val pocket3 = options.getOrElse(2) { distractor2 }
            val pocket4 = options.getOrElse(3) { distractor3 }

            binding.tvPocketTopLeftText.text = pocket1
            binding.tvPocketTopRightText.text = pocket2
            binding.tvPocketBottomLeftText.text = pocket3
            binding.tvPocketBottomRightText.text = pocket4

            binding.cardPocketTopLeft.setOnClickListener { onPocketShot(pocket1, correctAnswer, targetVerb, binding.cardPocketTopLeft) }
            binding.cardPocketTopRight.setOnClickListener { onPocketShot(pocket2, correctAnswer, targetVerb, binding.cardPocketTopRight) }
            binding.cardPocketBottomLeft.setOnClickListener { onPocketShot(pocket3, correctAnswer, targetVerb, binding.cardPocketBottomLeft) }
            binding.cardPocketBottomRight.setOnClickListener { onPocketShot(pocket4, correctAnswer, targetVerb, binding.cardPocketBottomRight) }
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

        val isCorrect = selectedText == correctAnswer
        speakText(targetVerb.base)

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
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}
