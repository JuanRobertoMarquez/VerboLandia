package com.example.verbolandia

import android.annotation.SuppressLint
import android.content.Intent
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
        private const val STREAK_DIFFICULTY_THRESHOLD = 3
    }

    private lateinit var binding: ActivityQuickTriadBinding
    private lateinit var repository: VerbRepository

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Verbs pools for adaptive difficulty
    private val allVerbsList = mutableListOf<Verb>()
    private val basePool = mutableListOf<Verb>()
    private val hardPool = mutableListOf<Verb>()
    private val globalPastFormsPool = mutableListOf<String>()
    private val globalPpFormsPool = mutableListOf<String>()

    private val sessionVerbs = mutableListOf<Verb>()
    private val usedVerbBases = mutableSetOf<String>()

    private var currentIndex = 0
    private var currentStep = 1 // 1 for V2 (Past), 2 for V3 (Participle)

    private var streak = 0
    private var maxStreak = 0
    private var totalExp = 0
    private var correctCount = 0

    // Timing
    private var sessionStartTimeMs = 0L
    private var timerJob: Job? = null

    // Options for current step
    private var currentOptions = mutableListOf<String>()
    private var isStepLocked = false

    // Session Results for the Summary Screen
    private val sessionResults = mutableListOf<TriadVerbResult>()

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
                if (isStepLocked) return@setOnClickListener
                val selectedText = textView.text.toString().trim()
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
                binding.tvTimerCounter.text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                delay(1000)
            }
        }
    }

    private fun loadSessionVerbs() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val catalog = repository.getCatalogVerbs(category = "ALL", level = "ALL")
                val all = catalog.map { v ->
                    repository.getVerbByBase(v.base) ?: v
                }

                allVerbsList.clear()
                allVerbsList.addAll(all)

                globalPastFormsPool.clear()
                globalPpFormsPool.clear()
                for (v in all) {
                    if (v.past.isNotBlank()) globalPastFormsPool.add(v.past.trim())
                    if (v.pastParticiple.isNotBlank()) globalPpFormsPool.add(v.pastParticiple.trim())
                }

                // Base Pool: Random A1 & A2 verbs
                basePool.clear()
                basePool.addAll(
                    all.filter { it.level.equals("A1", ignoreCase = true) || it.level.equals("A2", ignoreCase = true) }.shuffled()
                )

                // Hard Pool: B1 & B2 / C1 verbs (especially irregulars)
                hardPool.clear()
                hardPool.addAll(
                    all.filter {
                        it.level.equals("B1", ignoreCase = true) ||
                                it.level.equals("B2", ignoreCase = true) ||
                                it.level.equals("C1", ignoreCase = true)
                    }.shuffled()
                )
            }

            if (allVerbsList.isNotEmpty()) {
                sessionVerbs.clear()
                usedVerbBases.clear()
                sessionResults.clear()
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

    private fun selectNextVerb(): Verb? {
        // Adaptive difficulty curve driven strictly by live streak:
        // When streak >= 3, scale up to more challenging B1/B2/C1 verbs.
        // If streak < 3, keep verbs random in the accessible A1/A2 pool.
        val candidate: Verb? = if (streak >= STREAK_DIFFICULTY_THRESHOLD) {
            hardPool.firstOrNull { it.base !in usedVerbBases }
                ?: basePool.firstOrNull { it.base !in usedVerbBases }
        } else {
            basePool.firstOrNull { it.base !in usedVerbBases }
                ?: hardPool.firstOrNull { it.base !in usedVerbBases }
        }

        val selected = candidate ?: allVerbsList.firstOrNull { it.base !in usedVerbBases }
        if (selected != null) {
            usedVerbBases.add(selected.base)
            sessionVerbs.add(selected)
            return selected
        }
        return null
    }

    private fun getCurrentVerb(): Verb? {
        if (currentIndex < sessionVerbs.size) {
            return sessionVerbs[currentIndex]
        }
        return selectNextVerb()
    }

    @SuppressLint("SetTextI18n")
    private fun renderCurrentVerb() {
        val verb = getCurrentVerb() ?: return

        currentStep = 1
        isStepLocked = false

        // Progress Header
        binding.progressBar.progress = currentIndex + 1
        binding.tvProgressCounter.text = getString(R.string.triad_progress_format, currentIndex + 1, SESSION_SIZE)
        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Adaptive Subtitle Difficulty Tag
        if (streak >= STREAK_DIFFICULTY_THRESHOLD) {
            binding.tvModeSubtitle.text = getString(R.string.triad_mode_subtitle_streak, verb.level.uppercase())
        } else {
            binding.tvModeSubtitle.text = getString(R.string.triad_mode_subtitle_normal)
        }

        // Hero Card Data
        val catLabel = if (verb.isIrregular) getString(R.string.triad_badge_irregular) else getString(R.string.triad_badge_regular)
        binding.badgeLevel.text = getString(R.string.triad_badge_category_format, verb.level.uppercase(), catLabel)
        binding.tvBaseVerb.text = verb.base.uppercase()
        binding.tvBasePhonetic.text = getString(R.string.triad_v1_infinitive_format, verb.phonetic.ifBlank { "/${verb.base}/" })
        binding.tvSpanishMeaning.text = verb.spanishMeaning.ifBlank { verb.base }

        // Slot 1 (V1 Base - Always Completed)
        binding.tvSlotV1Val.text = verb.base
        binding.tvSlotV1Ipa.text = verb.ipaBase.ifBlank { verb.phonetic.ifBlank { "/${verb.base}/" } }

        // Slot 2 (V2 Pasado - Active Target in Step 1)
        binding.slotV2.setBackgroundResource(R.drawable.bg_triad_slot_active)
        binding.tvSlotV2Val.text = "?"
        binding.tvSlotV2Ipa.text = "--"

        // Slot 3 (V3 Participio - Locked until Step 1 Completed)
        binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_normal)
        binding.tvSlotV3Val.text = "?"
        binding.tvSlotV3Ipa.text = getString(R.string.triad_locked_label)

        // Current Instruction
        binding.tvCurrentInstruction.text = getString(R.string.triad_select_v2)
        binding.cardFeedback.visibility = View.GONE

        // Generate 4 dynamic options for V2
        generateOptions(verb, targetStep = 1)
        renderOptions()
    }

    private fun generateOptions(verb: Verb, targetStep: Int) {
        val options = LinkedHashSet<String>()

        val targetCorrect = if (targetStep == 1) verb.past.trim() else verb.pastParticiple.trim()
        val alternateForm = if (targetStep == 1) verb.pastParticiple.trim() else verb.past.trim()

        // 1. Correct form
        if (targetCorrect.isNotBlank()) {
            options.add(targetCorrect)
        }

        // 2. Alternate triad form (natural distractor if different)
        if (alternateForm.isNotBlank() && alternateForm != targetCorrect) {
            options.add(alternateForm)
        }

        // 3. Morphological distractor trap
        if (verb.isIrregular) {
            val fakeEd = if (verb.base.endsWith("e", ignoreCase = true)) "${verb.base}d" else "${verb.base}ed"
            if (fakeEd != targetCorrect && fakeEd != alternateForm) {
                options.add(fakeEd)
            }
        } else {
            val fakeEn = "${verb.base}en"
            if (fakeEn != targetCorrect) {
                options.add(fakeEn)
            }
        }

        // 4. Gerund as an additional distractor
        if (verb.gerund.isNotBlank() && verb.gerund !in options && verb.gerund != verb.base) {
            options.add(verb.gerund.trim())
        }

        // 5. Fill remaining slots from global pool of real forms to ensure 4 unique choices
        val formsPool = (globalPastFormsPool + globalPpFormsPool).shuffled()
        for (candidate in formsPool) {
            if (options.size >= 4) break
            val clean = candidate.trim()
            if (clean !in options && !clean.equals(verb.base, ignoreCase = true)) {
                options.add(clean)
            }
        }

        currentOptions = options.shuffled().take(4).toMutableList()
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
            // STEP 1: Verifying V2 (Past Simple)
            if (selectedText.equals(verb.past.trim(), ignoreCase = true)) {
                // Correct V2! Fill slot V2 and unlock slot V3
                binding.slotV2.setBackgroundResource(R.drawable.bg_triad_slot_completed)
                binding.tvSlotV2Val.text = verb.past
                binding.tvSlotV2Ipa.text = verb.ipaPast.ifBlank { "/${verb.past}/" }

                // Unlock Slot 3
                binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_active)
                binding.tvSlotV3Val.text = "?"
                binding.tvSlotV3Ipa.text = "--"
                binding.tvCurrentInstruction.text = getString(R.string.triad_select_v3)

                // Advance to Step 2 with fresh options
                currentStep = 2
                generateOptions(verb, targetStep = 2)
                renderOptions()
            } else {
                // SINGLE ATTEMPT RULE: Immediate failure on V2!
                handleSingleAttemptFailure(
                    verb = verb,
                    failedStep = 1,
                    selectedCard = card,
                    selectedText = selectedText,
                    expectedAnswer = verb.past
                )
            }
        } else if (currentStep == 2) {
            // STEP 2: Verifying V3 (Past Participle)
            if (selectedText.equals(verb.pastParticiple.trim(), ignoreCase = true)) {
                // Correct V3! Success on entire triad
                handleTriadSuccess(verb, card)
            } else {
                // SINGLE ATTEMPT RULE: Immediate failure on V3!
                handleSingleAttemptFailure(
                    verb = verb,
                    failedStep = 2,
                    selectedCard = card,
                    selectedText = selectedText,
                    expectedAnswer = verb.pastParticiple
                )
            }
        }
    }

    private fun handleTriadSuccess(verb: Verb, winningCard: MaterialCardView) {
        isStepLocked = true
        disableAllOptionCards()

        // Highlight winning card in success color
        winningCard.strokeColor = ContextCompat.getColor(this, R.color.success)
        winningCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high))

        // Complete slot V3
        binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_completed)
        binding.tvSlotV3Val.text = verb.pastParticiple
        binding.tvSlotV3Ipa.text = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" }

        // Increase streak and score
        streak++
        if (streak > maxStreak) {
            maxStreak = streak
        }
        correctCount++

        val comboBonus = streak * 3
        val gainedExp = 10 + comboBonus
        totalExp += gainedExp

        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Record successful result for the Summary Screen
        sessionResults.add(
            TriadVerbResult(
                verb = verb,
                isCorrect = true,
                wrongSelection = "",
                correctExpected = ""
            )
        )

        // Pronounce correct full triad with TTS
        speakText("${verb.base}, ${verb.past}, ${verb.pastParticiple}")

        // Save progress to database
        lifecycleScope.launch {
            repository.recordPracticeAttempt(verb.base, isCorrect = true, mode = "Tríada Rápida")
        }

        // Configure Success Feedback Card
        binding.ivFeedbackIcon.setImageResource(R.drawable.ic_check)
        binding.ivFeedbackIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))
        binding.tvFeedbackTitle.text = getString(R.string.triad_correct_streak_title, gainedExp)
        binding.tvFeedbackTitle.setTextColor(ContextCompat.getColor(this, R.color.success))

        binding.tvFeedbackComboBadge.visibility = View.VISIBLE
        binding.tvFeedbackComboBadge.text = getString(R.string.triad_combo_format, streak)
        binding.tvFeedbackSubtitle.visibility = View.GONE

        val tipText = if (verb.commonErrorTip.isNotBlank()) {
            verb.commonErrorTip
        } else {
            getString(R.string.triad_correct_forms_tip_format, verb.base.uppercase(), verb.past, verb.pastParticiple)
        }
        binding.tvFeedbackTip.text = tipText

        val exampleSentence = verb.tenses.firstOrNull { it.affirmativeEn.isNotBlank() }?.affirmativeEn
            ?: "They ${verb.past} yesterday."
        binding.tvFeedbackExample.text = getString(R.string.triad_example_format, exampleSentence)

        if (currentIndex == SESSION_SIZE - 1) {
            binding.btnNextVerb.text = getString(R.string.triad_complete_button)
        } else {
            binding.btnNextVerb.text = getString(R.string.triad_next_button)
        }

        binding.cardFeedback.visibility = View.VISIBLE
    }

    private fun handleSingleAttemptFailure(
        verb: Verb,
        failedStep: Int,
        selectedCard: MaterialCardView,
        selectedText: String,
        expectedAnswer: String
    ) {
        isStepLocked = true
        disableAllOptionCards()

        // Highlight selected wrong card with red error
        selectedCard.strokeColor = ContextCompat.getColor(this, R.color.error)
        selectedCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error_container))
        val shake = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        selectedCard.startAnimation(shake)

        // Reveal the correct option card in green if present among options
        highlightCorrectCard(expectedAnswer)

        // Reveal BOTH missing slots V2 and V3 so the user learns the full correct triad
        binding.slotV2.setBackgroundResource(R.drawable.bg_triad_slot_completed)
        binding.tvSlotV2Val.text = verb.past
        binding.tvSlotV2Ipa.text = verb.ipaPast.ifBlank { "/${verb.past}/" }

        binding.slotV3.setBackgroundResource(R.drawable.bg_triad_slot_completed)
        binding.tvSlotV3Val.text = verb.pastParticiple
        binding.tvSlotV3Ipa.text = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" }

        // Reset streak to 0
        streak = 0
        binding.tvStreakCount.text = getString(R.string.triad_streak_format, streak)

        // Record failed result for the Radiography Summary
        sessionResults.add(
            TriadVerbResult(
                verb = verb,
                isCorrect = false,
                wrongSelection = selectedText,
                correctExpected = expectedAnswer
            )
        )

        // Pronounce full correct triad with TTS so user hears proper pronunciation
        speakText("${verb.base}, ${verb.past}, ${verb.pastParticiple}")

        // Save attempt to database
        lifecycleScope.launch {
            repository.recordPracticeAttempt(verb.base, isCorrect = false, mode = "Tríada Rápida")
        }

        // Configure Error Feedback Card
        binding.ivFeedbackIcon.setImageResource(R.drawable.ic_warning)
        binding.ivFeedbackIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error))
        binding.tvFeedbackTitle.text = getString(R.string.triad_incorrect_title)
        binding.tvFeedbackTitle.setTextColor(ContextCompat.getColor(this, R.color.error))

        binding.tvFeedbackComboBadge.visibility = View.GONE
        binding.tvFeedbackSubtitle.visibility = View.VISIBLE
        binding.tvFeedbackSubtitle.text = getString(R.string.triad_incorrect_subtitle, expectedAnswer)

        val tipText = if (verb.commonErrorTip.isNotBlank()) {
            verb.commonErrorTip
        } else {
            getString(R.string.triad_correct_forms_tip_format, verb.base.uppercase(), verb.past, verb.pastParticiple)
        }
        binding.tvFeedbackTip.text = tipText

        val exampleSentence = verb.tenses.firstOrNull { it.affirmativeEn.isNotBlank() }?.affirmativeEn
            ?: "They ${verb.past} yesterday."
        binding.tvFeedbackExample.text = getString(R.string.triad_example_format, exampleSentence)

        if (currentIndex == SESSION_SIZE - 1) {
            binding.btnNextVerb.text = getString(R.string.triad_complete_button)
        } else {
            binding.btnNextVerb.text = getString(R.string.triad_next_button)
        }

        binding.cardFeedback.visibility = View.VISIBLE
    }

    private fun highlightCorrectCard(correctAnswer: String) {
        val optionCards = listOf(
            binding.cardOpt1 to binding.tvOpt1,
            binding.cardOpt2 to binding.tvOpt2,
            binding.cardOpt3 to binding.tvOpt3,
            binding.cardOpt4 to binding.tvOpt4
        )

        for ((card, textView) in optionCards) {
            if (textView.text.toString().trim().equals(correctAnswer.trim(), ignoreCase = true)) {
                card.strokeColor = ContextCompat.getColor(this, R.color.success)
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_container_high))
                break
            }
        }
    }

    private fun disableAllOptionCards() {
        val optionCards = listOf(
            binding.cardOpt1,
            binding.cardOpt2,
            binding.cardOpt3,
            binding.cardOpt4
        )
        optionCards.forEach { it.isEnabled = false }
    }

    private fun advanceToNextVerb() {
        if (currentIndex < SESSION_SIZE - 1) {
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
