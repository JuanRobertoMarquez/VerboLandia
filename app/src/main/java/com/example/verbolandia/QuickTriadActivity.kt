package com.example.verbolandia

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityQuickTriadBinding
import com.example.verbolandia.model.TriadVerbResult
import com.example.verbolandia.model.Verb
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class QuickTriadActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_LEVEL = "extra_level"
        private const val SESSION_SIZE = 10
    }

    private lateinit var binding: ActivityQuickTriadBinding
    private lateinit var repository: VerbRepository

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var sessionVerbs = mutableListOf<Verb>()
    private var currentIndex = 0
    private var currentStep = 1 // 1 for V2 (Past), 2 for V3 (Participle)

    private var streak = 0
    private var maxStreak = 0
    private var totalExp = 0
    private var correctCount = 0

    // Timing
    private var sessionStartTimeMs = 0L
    private var timerJob: Job? = null

    // Options for current verb
    private var currentOptions = mutableListOf<String>()
    private var hasMistakeOnCurrentVerb = false
    private var lastWrongSelection = ""

    // Session Results for the Summary Screen
    private val sessionResults = mutableListOf<TriadVerbResult>()

    // Global pool of real verb forms for dynamic non-repeating distractors
    private val globalPastFormsPool = mutableListOf<String>()
    private val globalPpFormsPool = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuickTriadBinding.inflate(layoutInflater)
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

        setupListeners()
        loadSessionVerbs()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnTtsAudio.setOnClickListener {
            val currentVerb = getCurrentVerb() ?: return@setOnClickListener
            speakText(currentVerb.base)
        }

        val optionCards = listOf(
            binding.cardOpt1 to binding.tvOpt1,
            binding.cardOpt2 to binding.tvOpt2,
            binding.cardOpt3 to binding.tvOpt3,
            binding.cardOpt4 to binding.tvOpt4
        )

        optionCards.forEach { (card, textView) ->
            card.setOnClickListener {
                val selectedText = textView.text.toString()
                onOptionSelected(selectedText, card)
            }
        }

        binding.btnNextVerb.setOnClickListener {
            advanceToNextVerb()
        }
    }

    private fun startSessionTimer() {
        sessionStartTimeMs = System.currentTimeMillis()
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (isActive) {
                val elapsedSec = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt()
                val mins = elapsedSec / 60
                val secs = elapsedSec % 60
                binding.tvTimerCounter.text = String.format("⏱️ %02d:%02d", mins, secs)
                delay(1000)
            }
        }
    }

    private fun loadSessionVerbs() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // Progressive Difficulty:
                // 1-3: A1, 4-6: A2, 7-8: B1, 9-10: B2/C1
                val allVerbs = repository.getCatalogVerbs(category = "ALL", level = "ALL")

                // Cache all forms for smart distractors
                globalPastFormsPool.clear()
                globalPpFormsPool.clear()
                for (v in allVerbs) {
                    if (v.past.isNotBlank()) globalPastFormsPool.add(v.past)
                    if (v.pastParticiple.isNotBlank()) globalPpFormsPool.add(v.pastParticiple)
                }

                val a1Verbs = allVerbs.filter { it.level.equals("A1", ignoreCase = true) }.shuffled()
                val a2Verbs = allVerbs.filter { it.level.equals("A2", ignoreCase = true) }.shuffled()
                val b1Verbs = allVerbs.filter { it.level.equals("B1", ignoreCase = true) }.shuffled()
                val b2Verbs = allVerbs.filter { it.level.equals("B2", ignoreCase = true) || it.level.equals("C1", ignoreCase = true) }.shuffled()

                val progressiveList = mutableListOf<Verb>()
                progressiveList.addAll(a1Verbs.take(3))
                progressiveList.addAll(a2Verbs.take(3))
                progressiveList.addAll(b1Verbs.take(2))
                progressiveList.addAll(b2Verbs.take(2))

                // Fallback if any tier didn't have enough
                if (progressiveList.size < SESSION_SIZE) {
                    val remaining = allVerbs.filter { it !in progressiveList }.shuffled().take(SESSION_SIZE - progressiveList.size)
                    progressiveList.addAll(remaining)
                }

                progressiveList
            }

            if (loaded.isNotEmpty()) {
                sessionVerbs.clear()
                sessionResults.clear()
                for (v in loaded) {
                    val fullVerb = repository.getVerbByBase(v.base) ?: v
                    sessionVerbs.add(fullVerb)
                }
                currentIndex = 0
                streak = 0
                maxStreak = 0
                totalExp = 0
                correctCount = 0

                startSessionTimer()
                renderCurrentVerb()
            } else {
                Toast.makeText(this@QuickTriadActivity, "No se encontraron verbos para la sesión", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun getCurrentVerb(): Verb? {
        return sessionVerbs.getOrNull(currentIndex)
    }

    @SuppressLint("SetTextI18n")
    private fun renderCurrentVerb() {
        val verb = getCurrentVerb() ?: return

        currentStep = 1
        hasMistakeOnCurrentVerb = false
        lastWrongSelection = ""

        // Progress Header
        binding.progressBar.progress = currentIndex + 1
        binding.tvProgressCounter.text = getString(R.string.triad_progress_format, currentIndex + 1, sessionVerbs.size)
        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Subtitle Difficulty Tag
        binding.tvModeSubtitle.text = "• MODO RÁPIDO • NIVEL ${verb.level.uppercase()} (${currentIndex + 1}/10)"

        // Hero Card Data
        val catLabel = if (verb.isIrregular) "IRREGULAR" else "REGULAR"
        binding.badgeLevel.text = "${verb.level.uppercase()} • $catLabel"
        binding.tvBaseVerb.text = verb.base.uppercase()
        binding.tvBasePhonetic.text = "${verb.phonetic} • V1 Infinitivo"
        binding.tvSpanishMeaning.text = verb.spanishMeaning.ifBlank { "Toca para practicar" }

        // Slot 1 (V1)
        binding.tvSlotV1Val.text = verb.base
        binding.tvSlotV1Ipa.text = verb.ipaBase.ifBlank { verb.phonetic }

        // Slot 2 (V2) - Active target
        binding.slotV2.setBackgroundResource(R.drawable.bg_triad_slot_active)
        binding.tvSlotV2Val.text = "?"
        binding.tvSlotV2Ipa.text = "--"

        // Slot 3 (V3) - Locked
        binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_normal)
        binding.tvSlotV3Val.text = "?"
        binding.tvSlotV3Ipa.text = "Bloqueado"

        // Instruction
        binding.tvCurrentInstruction.text = getString(R.string.triad_select_v2)
        binding.cardFeedback.visibility = View.GONE

        // Generate 4 dynamic options
        generateOptions(verb)
        renderOptions()
    }

    private fun generateOptions(verb: Verb) {
        val options = mutableSetOf<String>()

        // 1. Correct Past (V2)
        options.add(verb.past)

        // 2. Correct Participle (V3)
        options.add(verb.pastParticiple)

        // 3. Regularization Trap (-ed mistake for irregulars, or irregular variation for regulars)
        if (verb.isIrregular) {
            val fakeEd = if (verb.base.endsWith("e")) "${verb.base}d" else "${verb.base}ed"
            if (fakeEd != verb.past && fakeEd != verb.pastParticiple) {
                options.add(fakeEd)
            }
        } else {
            // For regulars, add a trap ending in -en or vowel change if possible
            options.add("${verb.base}en")
        }

        // 4. Sample clever non-repeating distractor from global pool
        val candidatePool = (globalPastFormsPool + globalPpFormsPool).shuffled()
        for (candidate in candidatePool) {
            if (options.size >= 4) break
            if (candidate !in options && !candidate.equals(verb.base, ignoreCase = true)) {
                options.add(candidate)
            }
        }

        // Fallback
        if (options.size < 4 && verb.gerund.isNotBlank() && verb.gerund !in options) {
            options.add(verb.gerund)
        }

        currentOptions = options.shuffled().toMutableList()
    }

    private fun renderOptions() {
        val optionCards = listOf(
            binding.cardOpt1 to binding.tvOpt1,
            binding.cardOpt2 to binding.tvOpt2,
            binding.cardOpt3 to binding.tvOpt3,
            binding.cardOpt4 to binding.tvOpt4
        )

        val defaultBg = ContextCompat.getColor(this, R.color.surface_container_low)
        val defaultText = ContextCompat.getColor(this, R.color.on_surface)
        val defaultStroke = ContextCompat.getColor(this, R.color.outline_variant)

        optionCards.forEachIndexed { i, (card, textView) ->
            if (i < currentOptions.size) {
                card.visibility = View.VISIBLE
                card.isEnabled = true
                textView.text = currentOptions[i]
                card.setCardBackgroundColor(defaultBg)
                card.strokeColor = defaultStroke
                textView.setTextColor(defaultText)
            } else {
                card.visibility = View.INVISIBLE
            }
        }
    }

    private fun onOptionSelected(selectedText: String, card: MaterialCardView) {
        val verb = getCurrentVerb() ?: return

        if (currentStep == 1) {
            // Target: V2 (Past Simple)
            if (selectedText.equals(verb.past, ignoreCase = true)) {
                // Correct V2!
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high))
                card.strokeColor = ContextCompat.getColor(this, R.color.success)

                binding.slotV2.setBackgroundResource(R.drawable.bg_triad_slot_completed)
                binding.tvSlotV2Val.text = verb.past
                binding.tvSlotV2Ipa.text = verb.ipaPast.ifBlank { "/${verb.past}/" }

                // Unlock Slot 3
                binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_active)
                binding.tvSlotV3Ipa.text = "--"
                binding.tvCurrentInstruction.text = getString(R.string.triad_select_v3)

                currentStep = 2
            } else {
                lastWrongSelection = selectedText
                handleMistake(card, verb.base)
            }
        } else if (currentStep == 2) {
            // Target: V3 (Past Participle)
            if (selectedText.equals(verb.pastParticiple, ignoreCase = true)) {
                // Correct V3!
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high))
                card.strokeColor = ContextCompat.getColor(this, R.color.success)

                binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_completed)
                binding.tvSlotV3Val.text = verb.pastParticiple
                binding.tvSlotV3Ipa.text = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" }

                onTriadCompleted(verb)
            } else {
                lastWrongSelection = selectedText
                handleMistake(card, verb.base)
            }
        }
    }

    private fun handleMistake(card: MaterialCardView, verbBase: String) {
        hasMistakeOnCurrentVerb = true
        streak = 0
        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        card.strokeColor = ContextCompat.getColor(this, R.color.error)
        val shake = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        card.startAnimation(shake)

        lifecycleScope.launch {
            repository.recordPracticeAttempt(verbBase, isCorrect = false, mode = "Tríada Rápida")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onTriadCompleted(verb: Verb) {
        val isFirstTryClean = !hasMistakeOnCurrentVerb
        if (isFirstTryClean) {
            streak++
            correctCount++
            if (streak > maxStreak) {
                maxStreak = streak
            }
        }

        val comboBonus = streak * 3
        val gainedExp = 10 + comboBonus
        totalExp += gainedExp

        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Record verb result for the Summary Radiography Screen
        sessionResults.add(
            TriadVerbResult(
                verb = verb,
                isCorrect = isFirstTryClean,
                wrongSelection = lastWrongSelection,
                correctExpected = if (currentStep == 1) verb.past else verb.pastParticiple
            )
        )

        // TTS Pronunciation
        speakText("${verb.base}, ${verb.past}, ${verb.pastParticiple}")

        // Save practice attempt to database
        lifecycleScope.launch {
            repository.recordPracticeAttempt(verb.base, isCorrect = isFirstTryClean, mode = "Tríada Rápida")
        }

        // Feedback Card
        binding.tvFeedbackTitle.text = "+$gainedExp EXP"
        binding.tvFeedbackComboBadge.text = getString(R.string.triad_combo_format, if (streak > 0) streak else 1)

        val tipText = if (verb.commonErrorTip.isNotBlank()) {
            verb.commonErrorTip
        } else {
            "Formas de ${verb.base.uppercase()}: Pasado (${verb.past}) y Participio (${verb.pastParticiple})."
        }
        binding.tvFeedbackTip.text = tipText

        val exampleSentence = verb.tenses.firstOrNull { it.affirmativeEn.isNotBlank() }?.affirmativeEn
            ?: "Ejemplo: They ${verb.past} yesterday."
        binding.tvFeedbackExample.text = "💡 $exampleSentence"

        if (currentIndex == sessionVerbs.size - 1) {
            binding.btnNextVerb.text = getString(R.string.triad_complete_button)
        } else {
            binding.btnNextVerb.text = getString(R.string.triad_next_button)
        }

        binding.cardFeedback.visibility = View.VISIBLE
    }

    private fun advanceToNextVerb() {
        if (currentIndex < sessionVerbs.size - 1) {
            currentIndex++
            renderCurrentVerb()
        } else {
            showSessionSummaryScreen()
        }
    }

    private fun showSessionSummaryScreen() {
        timerJob?.cancel()
        val elapsedSec = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt()

        val intent = Intent(this, QuickTriadSummaryActivity::class.java).apply {
            putExtra(QuickTriadSummaryActivity.EXTRA_EXP, totalExp)
            putExtra(QuickTriadSummaryActivity.EXTRA_CORRECT, correctCount)
            putExtra(QuickTriadSummaryActivity.EXTRA_TOTAL, sessionVerbs.size)
            putExtra(QuickTriadSummaryActivity.EXTRA_MAX_STREAK, maxStreak)
            putExtra(QuickTriadSummaryActivity.EXTRA_TIME_SECONDS, elapsedSec)
            putExtra(QuickTriadSummaryActivity.EXTRA_COMBO_BONUS, maxStreak * 5)
            putExtra(QuickTriadSummaryActivity.EXTRA_RESULTS, ArrayList(sessionResults))
        }
        startActivity(intent)
        finish()
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
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "triad_tts")
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
