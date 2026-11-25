package com.example.verbolandia

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.verbolandia.adapter.BatteryCardAdapter
import com.example.verbolandia.adapter.BatteryCardItem
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityReinforcementBatteryBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.AudioSpeaker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ReinforcementBatteryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReinforcementBatteryBinding
    private lateinit var verbRepo: VerbRepository
    private lateinit var cardAdapter: BatteryCardAdapter

    private var targetVerbs: List<Verb> = emptyList()
    private var allCatalogVerbs: List<Verb> = emptyList()
    private var currentVerbIndex = 0

    // Round state: V2 and V3 status
    private var isV2Filled = false
    private var isV3Filled = false

    // Currently focused container slot (2 = V2 Pasado, 3 = V3 Participio)
    private var selectedSlot = 2

    private var comboCount = 0
    private var completedTriads = 0
    private var hintsRemaining = 3

    private var timer: CountDownTimer? = null
    private var timeRemainingMs = 180_000L // 3 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReinforcementBatteryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        verbRepo = VerbRepository.getInstance(this)
        AudioSpeaker.init(this)

        setupUI()
        setupListeners()
        setupBackHandler()
        loadVerbsAndStart()
    }

    private fun setupUI() {
        cardAdapter = BatteryCardAdapter { item, position ->
            handleCardSelected(item, position)
        }

        binding.rvOptionCards.apply {
            layoutManager = GridLayoutManager(this@ReinforcementBatteryActivity, 2)
            adapter = cardAdapter
        }

        // Dynamically compute item height so the 3 rows fill the vertical space
        binding.rvOptionCards.post {
            updateOptionCardHeights()
        }

        binding.chipStreak.text = getString(R.string.battery_streak_short_format, comboCount)
        updateHintsButtonText()
    }

    private fun updateOptionCardHeights() {
        val totalHeight = binding.rvOptionCards.height
        if (totalHeight > 0) {
            val marginPx = (resources.displayMetrics.density * 6).toInt()
            val calculatedHeight = (totalHeight / 3) - marginPx
            if (calculatedHeight > 0) {
                cardAdapter.updateItemHeight(calculatedHeight)
            }
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            showQuitConfirmationDialog()
        }

        binding.btnSpeaker.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            getCurrentVerb()?.let { verb ->
                AudioSpeaker.speak(this, verb.base, it)
            }
        }

        binding.btnMnemonicHint.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showMnemonicHint()
        }

        // Tap on Capsule 2 to select V2 as the active target
        binding.capsule2.setOnClickListener {
            if (!isV2Filled) {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedSlot = 2
                updateSlotSelectionUI()
            }
        }

        // Tap on Capsule 3 to select V3 as the active target
        binding.capsule3.setOnClickListener {
            if (!isV3Filled) {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                selectedSlot = 3
                updateSlotSelectionUI()
            }
        }
    }

    private fun updateSlotSelectionUI() {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val outlineColor = ContextCompat.getColor(this, R.color.outline_variant)
        val onSurfaceVariant = ContextCompat.getColor(this, R.color.on_surface_variant)

        // Capsule 2 (V2 Pasado)
        if (!isV2Filled) {
            if (selectedSlot == 2) {
                binding.capsule2.strokeColor = primaryColor
                binding.capsule2.strokeWidth = dpToPx(2.5f)
                binding.capsule2.cardElevation = dpToPx(3f).toFloat()
                binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_active)
                binding.ivV2SlotIcon.visibility = View.VISIBLE
                binding.ivV2SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
                binding.ivV2SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                binding.tvV2SlotText.text = getString(R.string.battery_insert_v2)
                binding.tvV2SlotText.setTextColor(primaryColor)
                binding.tvV2SlotSubtext.text = getString(R.string.battery_slot_selected)
                binding.tvV2SlotSubtext.setTextColor(primaryColor)
            } else {
                binding.capsule2.strokeColor = outlineColor
                binding.capsule2.strokeWidth = dpToPx(1f)
                binding.capsule2.cardElevation = dpToPx(0f).toFloat()
                binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
                binding.ivV2SlotIcon.visibility = View.VISIBLE
                binding.ivV2SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
                binding.ivV2SlotIcon.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
                binding.tvV2SlotText.text = getString(R.string.battery_v2_placeholder)
                binding.tvV2SlotText.setTextColor(onSurfaceVariant)
                binding.tvV2SlotSubtext.text = getString(R.string.battery_slot_tap_to_select)
                binding.tvV2SlotSubtext.setTextColor(onSurfaceVariant)
            }
        }

        // Capsule 3 (V3 Participio)
        if (!isV3Filled) {
            if (selectedSlot == 3) {
                binding.capsule3.strokeColor = primaryColor
                binding.capsule3.strokeWidth = dpToPx(2.5f)
                binding.capsule3.cardElevation = dpToPx(3f).toFloat()
                binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_active)
                binding.ivV3SlotIcon.visibility = View.VISIBLE
                binding.ivV3SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
                binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
                binding.tvV3SlotText.text = getString(R.string.battery_insert_v3)
                binding.tvV3SlotText.setTextColor(primaryColor)
                binding.tvV3SlotSubtext.text = getString(R.string.battery_slot_selected)
                binding.tvV3SlotSubtext.setTextColor(primaryColor)
            } else {
                binding.capsule3.strokeColor = outlineColor
                binding.capsule3.strokeWidth = dpToPx(1f)
                binding.capsule3.cardElevation = dpToPx(0f).toFloat()
                binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
                binding.ivV3SlotIcon.visibility = View.VISIBLE
                binding.ivV3SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
                binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
                binding.tvV3SlotText.text = getString(R.string.battery_v3_placeholder)
                binding.tvV3SlotText.setTextColor(onSurfaceVariant)
                binding.tvV3SlotSubtext.text = getString(R.string.battery_slot_tap_to_select)
                binding.tvV3SlotSubtext.setTextColor(onSurfaceVariant)
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showQuitConfirmationDialog()
            }
        })
    }

    private fun loadVerbsAndStart() {
        lifecycleScope.launch {
            val weakVerbs = verbRepo.getWeakOrChallengingVerbs(5)
            val catalog = verbRepo.getCatalogVerbs()

            withContext(Dispatchers.Main) {
                targetVerbs = weakVerbs
                allCatalogVerbs = catalog

                if (targetVerbs.isEmpty()) {
                    showNoVerbsDialog()
                } else {
                    startTimer()
                    loadCurrentVerbRound()
                }
            }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeRemainingMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingMs = millisUntilFinished
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.chipTimer.text = getString(R.string.battery_timer_format, minutes, seconds)
            }

            override fun onFinish() {
                binding.chipTimer.text = getString(R.string.battery_timer_format, 0, 0)
                showTimeoutDialog()
            }
        }.start()
    }

    private fun getCurrentVerb(): Verb? {
        return if (targetVerbs.isNotEmpty() && currentVerbIndex < targetVerbs.size) {
            targetVerbs[currentVerbIndex]
        } else null
    }

    private fun loadCurrentVerbRound() {
        val verb = getCurrentVerb() ?: return

        isV2Filled = false
        isV3Filled = false
        selectedSlot = 2

        // Top progress count
        binding.chipProgressCount.text = getString(
            R.string.battery_round_progress_format,
            currentVerbIndex + 1,
            targetVerbs.size
        )

        // Target Verb Card
        binding.badgeVerbType.text = if (verb.isIrregular) getString(R.string.badge_irregular) else getString(R.string.badge_regular)
        binding.badgeVerbLevel.text = getString(R.string.battery_badge_level_format, verb.level.uppercase(Locale.ROOT))
        binding.tvTargetVerbBase.text = verb.base.uppercase(Locale.ROOT)
        binding.tvTargetSpanish.text = verb.spanishMeaning

        // Reset Containers UI
        resetContainersUI(verb)

        // Generate Conveyor Cards without distinctBy so identical V2/V3 (found/found) both exist
        val cards = generateConveyorCards(verb)
        cardAdapter.submitList(cards)

        binding.rvOptionCards.post {
            updateOptionCardHeights()
        }

        // Pronounce base verb
        AudioSpeaker.speak(this, verb.base, binding.btnSpeaker)
    }

    private fun resetContainersUI(verb: Verb) {
        // Container 1 (V1 Base - preloaded, centered)
        binding.tvV1Word.text = verb.base
        binding.tvV1Ipa.text = verb.ipaBase.ifBlank { "/${verb.base}/" }
        binding.ivV1Status.visibility = View.VISIBLE
        binding.ivV1Status.setImageResource(R.drawable.ic_check)
        binding.ivV1Status.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))

        // Update selection UI for containers 2 and 3
        selectedSlot = 2
        updateSlotSelectionUI()
    }

    private fun generateConveyorCards(verb: Verb): List<BatteryCardItem> {
        val list = mutableListOf<BatteryCardItem>()

        // 1. Correct V2 Pasado Card
        list.add(
            BatteryCardItem(
                id = "v2_${verb.past}_${System.currentTimeMillis()}_1",
                word = verb.past,
                ipa = verb.ipaPast.ifBlank { "/${verb.past}/" },
                tag = "Pasado",
                role = "Pasado Simple",
                targetTense = 2
            )
        )

        // 2. Correct V3 Participio Card (Unique ID even if verb.past == verb.pastParticiple!)
        list.add(
            BatteryCardItem(
                id = "v3_${verb.pastParticiple}_${System.currentTimeMillis()}_2",
                word = verb.pastParticiple,
                ipa = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" },
                tag = "Participio",
                role = "Participio",
                targetTense = 3
            )
        )

        // 3. Morphological Trap (-ed for irregulars, -en for regulars)
        if (verb.isIrregular) {
            val trapWord = if (verb.base.endsWith("e", true)) "${verb.base}d" else "${verb.base}ed"
            list.add(
                BatteryCardItem(
                    id = "trap_ed_${verb.base}",
                    word = trapWord,
                    ipa = "/$trapWord/",
                    tag = "Trampa",
                    role = "Trampa Morfológica",
                    isTrap = true,
                    trapReason = getString(R.string.battery_circuit_error_trap_format, trapWord, verb.base, verb.past),
                    targetTense = 0
                )
            )
        } else {
            val trapWord = "${verb.base}en"
            list.add(
                BatteryCardItem(
                    id = "trap_en_${verb.base}",
                    word = trapWord,
                    ipa = "/$trapWord/",
                    tag = "Trampa",
                    role = "Trampa -en",
                    isTrap = true,
                    trapReason = getString(R.string.battery_circuit_error_trap_regular, verb.base),
                    targetTense = 0
                )
            )
        }

        // 4. Gerund form
        list.add(
            BatteryCardItem(
                id = "gerund_${verb.gerund}",
                word = verb.gerund,
                ipa = "/${verb.gerund}/",
                tag = "Gerundio",
                role = "Gerundio",
                trapReason = getString(R.string.battery_circuit_error_gerund),
                targetTense = 0
            )
        )

        // 5. False irregular vowel / alternative form
        val altWord = if (verb.isIrregular && !verb.past.equals(verb.pastParticiple, ignoreCase = true)) {
            "${verb.past}n"
        } else {
            "${verb.base}s"
        }
        list.add(
            BatteryCardItem(
                id = "alt_$altWord",
                word = altWord,
                ipa = "/$altWord/",
                tag = "Alterna",
                role = "Forma Alterna",
                isTrap = true,
                trapReason = getString(R.string.battery_circuit_error_alternative),
                targetTense = 0
            )
        )

        // 6. Real lexical distractor from other catalog verbs
        val distractorVerb = allCatalogVerbs
            .filter { !it.base.equals(verb.base, ignoreCase = true) && !it.past.equals(verb.past, ignoreCase = true) }
            .shuffled()
            .firstOrNull()

        if (distractorVerb != null) {
            val distWord = if (distractorVerb.isIrregular) distractorVerb.past else distractorVerb.pastParticiple
            list.add(
                BatteryCardItem(
                    id = "dist_$distWord",
                    word = distWord,
                    ipa = distractorVerb.ipaPast.ifBlank { "/$distWord/" },
                    tag = "Distractor",
                    role = "Distractor Léxico",
                    trapReason = getString(R.string.battery_circuit_error_distractor_format, distWord, distractorVerb.base),
                    targetTense = 0
                )
            )
        }

        return list.shuffled()
    }

    /**
     * Handles card click (Tap). Places word in the CURRENTLY SELECTED CONTAINER (selectedSlot).
     * If correct, fills that container and automatically advances selectedSlot to the remaining empty container.
     */
    private fun handleCardSelected(item: BatteryCardItem, position: Int) {
        val verb = getCurrentVerb() ?: return

        if (selectedSlot == 2) {
            val matchesV2 = item.word.equals(verb.past, ignoreCase = true)
            if (matchesV2) {
                fillV2Container(item)
                if (!isV3Filled) {
                    selectedSlot = 3
                    updateSlotSelectionUI()
                }
                checkRoundCompletion(verb)
            } else {
                handleErrorCard(item, position, verb)
            }
        } else if (selectedSlot == 3) {
            val matchesV3 = item.word.equals(verb.pastParticiple, ignoreCase = true)
            if (matchesV3) {
                fillV3Container(item)
                if (!isV2Filled) {
                    selectedSlot = 2
                    updateSlotSelectionUI()
                }
                checkRoundCompletion(verb)
            } else {
                handleErrorCard(item, position, verb)
            }
        }
    }

    private fun fillV2Container(item: BatteryCardItem) {
        isV2Filled = true
        cardAdapter.markUsedById(item.id)

        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        binding.capsule2.strokeColor = primaryColor
        binding.capsule2.strokeWidth = dpToPx(1.5f)
        binding.capsule2.cardElevation = dpToPx(1f).toFloat()
        binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)

        binding.ivV2SlotIcon.visibility = View.VISIBLE
        binding.ivV2SlotIcon.setImageResource(R.drawable.ic_check)
        binding.ivV2SlotIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))

        binding.tvV2SlotText.text = item.word
        binding.tvV2SlotText.setTextColor(primaryColor)
        binding.tvV2SlotText.textSize = 17f

        binding.tvV2SlotSubtext.text = item.ipa
        binding.tvV2SlotSubtext.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))

        AudioSpeaker.speak(this, item.word, binding.capsule2)
    }

    private fun fillV3Container(item: BatteryCardItem) {
        isV3Filled = true
        cardAdapter.markUsedById(item.id)

        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        binding.capsule3.strokeColor = primaryColor
        binding.capsule3.strokeWidth = dpToPx(1.5f)
        binding.capsule3.cardElevation = dpToPx(1f).toFloat()
        binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)

        binding.ivV3SlotIcon.visibility = View.VISIBLE
        binding.ivV3SlotIcon.setImageResource(R.drawable.ic_check)
        binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))

        binding.tvV3SlotText.text = item.word
        binding.tvV3SlotText.setTextColor(primaryColor)
        binding.tvV3SlotText.textSize = 17f

        binding.tvV3SlotSubtext.text = item.ipa
        binding.tvV3SlotSubtext.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))

        AudioSpeaker.speak(this, item.word, binding.capsule3)
    }

    private fun handleErrorCard(item: BatteryCardItem, position: Int, verb: Verb) {
        val holder = binding.rvOptionCards.findViewHolderForAdapterPosition(position)
                as? BatteryCardAdapter.ViewHolder
        holder?.shakeAndHighlightError()

        comboCount = 0
        binding.chipStreak.text = getString(R.string.battery_streak_short_format, comboCount)

        val errorReason = if (item.trapReason.isNotBlank()) {
            item.trapReason
        } else {
            getString(R.string.battery_circuit_error_wrong_time)
        }
        Snackbar.make(binding.root, errorReason, Snackbar.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            verbRepo.recordPracticeAttempt(verb.base, false, "Batería de Refuerzo")
        }
    }

    private fun checkRoundCompletion(verb: Verb) {
        if (isV2Filled && isV3Filled) {
            completedTriads++
            comboCount++

            binding.chipStreak.text = getString(R.string.battery_streak_short_format, comboCount)

            val chargePct = ((currentVerbIndex + 1) * 20).coerceAtMost(100)
            binding.progressBattery.setProgressCompat(chargePct, true)
            binding.tvChargePct.text = getString(R.string.battery_charge_pct_format, chargePct)

            // Pronounce full triad
            AudioSpeaker.speak(this, "${verb.base}, ${verb.past}, ${verb.pastParticiple}", binding.cardTargetVerb)

            // Record success in DB
            lifecycleScope.launch(Dispatchers.IO) {
                verbRepo.recordPracticeAttempt(verb.base, true, "Batería de Refuerzo")
            }

            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    currentVerbIndex++
                    if (currentVerbIndex < targetVerbs.size) {
                        loadCurrentVerbRound()
                    } else {
                        showVictoryDialog()
                    }
                }
            }, 1200)
        }
    }

    private fun showMnemonicHint() {
        if (hintsRemaining <= 0) {
            Snackbar.make(binding.root, getString(R.string.battery_hints_no_more), Snackbar.LENGTH_SHORT).show()
            return
        }

        val verb = getCurrentVerb() ?: return

        hintsRemaining--
        updateHintsButtonText()

        val tip = verb.commonErrorTip.ifBlank {
            getString(R.string.battery_hint_default_format, verb.base, verb.past, verb.pastParticiple)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.battery_hint_title_format, getString(R.string.battery_hint_label), verb.base.uppercase(Locale.ROOT)))
            .setMessage(tip)
            .setPositiveButton(R.string.dialog_understood, null)
            .show()
    }

    private fun updateHintsButtonText() {
        if (hintsRemaining > 0) {
            binding.btnMnemonicHint.text = getString(R.string.battery_hints_remaining_format, hintsRemaining)
            binding.btnMnemonicHint.alpha = 1.0f
        } else {
            binding.btnMnemonicHint.text = getString(R.string.battery_hints_exhausted)
            binding.btnMnemonicHint.alpha = 0.5f
        }
    }

    private fun showQuitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_dialog_quit_title)
            .setMessage(R.string.battery_dialog_quit_desc)
            .setPositiveButton(R.string.battery_dialog_quit_btn) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.battery_dialog_stay_btn, null)
            .show()
    }

    private fun showTimeoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_timeout_title)
            .setMessage(getString(R.string.battery_timeout_desc, completedTriads, targetVerbs.size))
            .setCancelable(false)
            .setPositiveButton(R.string.battery_btn_finish) { _, _ ->
                finish()
            }
            .show()
    }

    private fun showVictoryDialog() {
        timer?.cancel()
        val minutesRemaining = (timeRemainingMs / 1000) / 60
        val secondsRemaining = (timeRemainingMs / 1000) % 60
        val timeFormatted = getString(R.string.battery_timer_format, minutesRemaining, secondsRemaining)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_win_title)
            .setMessage(getString(R.string.battery_win_desc, targetVerbs.size, timeFormatted, completedTriads))
            .setCancelable(false)
            .setPositiveButton(R.string.battery_btn_finish) { _, _ ->
                finish()
            }
            .show()
    }

    private fun showNoVerbsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_no_verbs_title)
            .setMessage(R.string.battery_no_verbs_desc)
            .setCancelable(false)
            .setPositiveButton(R.string.battery_btn_finish) { _, _ ->
                finish()
            }
            .show()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }
}