package com.example.verbolandia

import android.annotation.SuppressLint
import android.content.res.ColorStateList
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
import com.example.verbolandia.model.Verb
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
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

    // Options for current verb
    private var currentOptions = mutableListOf<String>()
    private var hasMistakeOnCurrentVerb = false

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

        optionCards.forEachIndexed { index, (card, textView) ->
            card.setOnClickListener {
                val selectedText = textView.text.toString()
                onOptionSelected(selectedText, card)
            }
        }

        binding.btnNextVerb.setOnClickListener {
            advanceToNextVerb()
        }
    }

    private fun loadSessionVerbs() {
        val level = intent.getStringExtra(EXTRA_LEVEL) ?: "ALL"

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                // Fetch catalog verbs and prioritize irregular or common ones
                val all = repository.getCatalogVerbs(category = "IRREGULAR", level = level)
                val pool = if (all.size >= SESSION_SIZE) all else repository.getCatalogVerbs(category = "ALL", level = level)
                pool.shuffled().take(SESSION_SIZE)
            }

            if (loaded.isNotEmpty()) {
                sessionVerbs.clear()
                // Enrich verbs with their tenses from DB
                for (v in loaded) {
                    val fullVerb = repository.getVerbByBase(v.base) ?: v
                    sessionVerbs.add(fullVerb)
                }
                currentIndex = 0
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

        // Update Progress & Streak Header
        binding.progressBar.progress = currentIndex + 1
        binding.tvProgressCounter.text = getString(R.string.triad_progress_format, currentIndex + 1, sessionVerbs.size)
        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

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

        // Reset Instruction
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

        // 3. Fake Regular Form (-ed mistake)
        val fakeEd = if (verb.base.endsWith("e")) "${verb.base}d" else "${verb.base}ed"
        if (fakeEd != verb.past && fakeEd != verb.pastParticiple) {
            options.add(fakeEd)
        }

        // 4. Common mistake or Gerund or typo
        if (verb.gerund.isNotBlank() && verb.gerund != verb.past && verb.gerund != verb.pastParticiple) {
            options.add(verb.gerund)
        }

        // Fill with random other verbs' past if we still don't have 4 unique options
        var attempts = 0
        while (options.size < 4 && attempts < sessionVerbs.size) {
            val randomPast = sessionVerbs[attempts].past
            if (randomPast != verb.past && randomPast != verb.pastParticiple) {
                options.add(randomPast)
            }
            attempts++
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
            // Looking for V2 (Past Simple)
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
                handleMistake(card, verb.base)
            }
        } else if (currentStep == 2) {
            // Looking for V3 (Past Participle)
            if (selectedText.equals(verb.pastParticiple, ignoreCase = true)) {
                // Correct V3! Triad Complete!
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high))
                card.strokeColor = ContextCompat.getColor(this, R.color.success)

                binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_completed)
                binding.tvSlotV3Val.text = verb.pastParticiple
                binding.tvSlotV3Ipa.text = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" }

                onTriadCompleted(verb)
            } else {
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
        if (!hasMistakeOnCurrentVerb) {
            streak++
            correctCount++
            if (streak > maxStreak) {
                maxStreak = streak
            }
        }

        val gainedExp = 10 + (streak * 2)
        totalExp += gainedExp

        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Pronounce full triad: "choose... chose... chosen"
        speakText("${verb.base}, ${verb.past}, ${verb.pastParticiple}")

        // Save progress to database
        lifecycleScope.launch {
            repository.recordPracticeAttempt(verb.base, isCorrect = !hasMistakeOnCurrentVerb, mode = "Tríada Rápida")
        }

        // Display pedagogical feedback
        binding.tvFeedbackTitle.text = "+$gainedExp EXP"
        binding.tvFeedbackComboBadge.text = getString(R.string.triad_combo_format, if (streak > 0) streak else 1)

        val tipText = if (verb.commonErrorTip.isNotBlank()) {
            verb.commonErrorTip
        } else {
            "Formas de ${verb.base.uppercase()}: Pasado (${verb.past}) y Participio (${verb.pastParticiple})."
        }
        binding.tvFeedbackTip.text = tipText

        // Natural sentence example from tenses if available
        val exampleSentence = verb.tenses.firstOrNull { it.affirmativeEn.isNotBlank() }?.affirmativeEn
            ?: "Ejemplo: They ${verb.past} yesterday."
        binding.tvFeedbackExample.text = "💡 $exampleSentence"

        // Next or Finish Button text
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
            showSessionSummaryDialog()
        }
    }

    private fun showSessionSummaryDialog() {
        val accuracy = if (sessionVerbs.isNotEmpty()) (correctCount * 100) / sessionVerbs.size else 0
        val msg = """
            ${getString(R.string.triad_score_format, totalExp)}
            ${getString(R.string.triad_accuracy_format, correctCount, sessionVerbs.size, accuracy)}
            Racha Máxima: 🔥 x$maxStreak
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.triad_finished_title))
            .setMessage(msg)
            .setPositiveButton(getString(R.string.triad_finish_btn)) { d, _ ->
                d.dismiss()
                finish()
            }
            .setNegativeButton(getString(R.string.triad_retry_btn)) { d, _ ->
                d.dismiss()
                loadSessionVerbs()
            }
            .setCancelable(false)
            .show()
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
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
