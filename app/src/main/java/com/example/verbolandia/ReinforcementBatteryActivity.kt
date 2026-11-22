package com.example.verbolandia

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

    // Round state: V2 and V3 are both free to fill in any order
    private var isV2Filled = false
    private var isV3Filled = false

    private var comboCount = 0
    private var completedTriads = 0
    private var hintsRemaining = 3

    private var timer: CountDownTimer? = null
    private var timeRemainingMs = 180_000L // 3 minutes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReinforcementBatteryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verbRepo = VerbRepository.getInstance(this)
        AudioSpeaker.init(this)

        setupUI()
        setupListeners()
        setupDragAndDrop()
        setupBackHandler()
        loadVerbsAndStart()
    }

    private fun setupUI() {
        cardAdapter = BatteryCardAdapter { item, position ->
            handleCardSelected(item, position)
        }

        binding.rvOptionCards.apply {
            layoutManager = LinearLayoutManager(
                this@ReinforcementBatteryActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = cardAdapter
        }

        binding.chipStreak.text = getString(R.string.battery_streak_short_format, comboCount)
        updateHintsButtonText()
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
    }

    private fun setupDragAndDrop() {
        // Drag listener for Capsule 2 (V2 Pasado)
        binding.capsule2.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!isV2Filled) {
                        binding.capsule2.strokeWidth = dpToPx(3f)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    binding.capsule2.strokeWidth = dpToPx(1.5f)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val item = event.localState as? BatteryCardItem
                    if (item != null) {
                        handleDropOnSlot(item, targetSlot = 2)
                    }
                    true
                }
                else -> false
            }
        }

        // Drag listener for Capsule 3 (V3 Participio)
        binding.capsule3.setOnDragListener { view, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    if (!isV3Filled) {
                        binding.capsule3.strokeWidth = dpToPx(3f)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    binding.capsule3.strokeWidth = dpToPx(1.5f)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val item = event.localState as? BatteryCardItem
                    if (item != null) {
                        handleDropOnSlot(item, targetSlot = 3)
                    }
                    true
                }
                else -> false
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

        // Top progress pill
        binding.chipProgressCount.text = getString(
            R.string.battery_round_progress_format,
            currentVerbIndex + 1,
            targetVerbs.size
        )

        // Verb Card Info
        binding.badgeVerbType.text = if (verb.isIrregular) getString(R.string.badge_irregular) else getString(R.string.badge_regular)
        binding.badgeVerbLevel.text = getString(R.string.battery_badge_level_format, verb.level.uppercase(Locale.ROOT))
        binding.tvTargetVerbBase.text = verb.base.uppercase(Locale.ROOT)
        binding.tvTargetSpanish.text = verb.spanishMeaning

        // Reset Containers UI
        resetContainersUI(verb)

        // Generate Conveyor Cards without distinctBy so identical V2/V3 (found/found) both exist!
        val cards = generateConveyorCards(verb)
        cardAdapter.submitList(cards)

        // Pronounce base verb
        AudioSpeaker.speak(this, verb.base, binding.btnSpeaker)
    }

    private fun resetContainersUI(verb: Verb) {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)

        // Container 1 (V1 Base - preloaded)
        binding.tvV1Word.text = verb.base
        binding.tvV1Ipa.text = verb.ipaBase.ifBlank { "/${verb.base}/" }
        binding.ivV1Status.visibility = View.VISIBLE
        binding.ivV1Status.setImageResource(R.drawable.ic_check)
        binding.ivV1Status.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))

        // Container 2 (V2 Pasado - Open, unlocked)
        binding.capsule2.strokeColor = primaryColor
        binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
        binding.ivV2SlotIcon.visibility = View.VISIBLE
        binding.ivV2SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV2SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.tvV2SlotText.text = getString(R.string.battery_insert_v2)
        binding.tvV2SlotText.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
        binding.tvV2SlotText.textSize = 13f
        binding.tvV2SlotSubtext.text = ""

        // Container 3 (V3 Participio - Also open and unlocked for free choice!)
        binding.capsule3.strokeColor = primaryColor
        binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
        binding.ivV3SlotIcon.visibility = View.VISIBLE
        binding.ivV3SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.tvV3SlotText.text = getString(R.string.battery_insert_v3)
        binding.tvV3SlotText.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
        binding.tvV3SlotText.textSize = 13f
        binding.tvV3SlotSubtext.text = ""
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

        // Shuffle cards so correct options are distributed
        return list.shuffled()
    }

    /**
     * Handles card click (Tap). User has free choice: if it matches V2 (and V2 is empty),
     * fills V2; if it matches V3 (and V3 is empty), fills V3!
     */
    private fun handleCardSelected(item: BatteryCardItem, position: Int) {
        val verb = getCurrentVerb() ?: return

        val matchesV2 = item.word.equals(verb.past, ignoreCase = true)
        val matchesV3 = item.word.equals(verb.pastParticiple, ignoreCase = true)

        if (!isV2Filled && matchesV2) {
            // Fill V2
            fillV2Container(item)
            checkRoundCompletion(verb)
        } else if (!isV3Filled && matchesV3) {
            // Fill V3
            fillV3Container(item)
            checkRoundCompletion(verb)
        } else {
            // Error: Card does not match any open container
            handleErrorCard(item, position, verb)
        }
    }

    /**
     * Handles dropping a card onto a specific container (Drag & Drop).
     */
    private fun handleDropOnSlot(item: BatteryCardItem, targetSlot: Int) {
        val verb = getCurrentVerb() ?: return

        if (targetSlot == 2) {
            if (!isV2Filled && item.word.equals(verb.past, ignoreCase = true)) {
                fillV2Container(item)
                checkRoundCompletion(verb)
            } else {
                handleErrorDropped(item, verb)
            }
        } else if (targetSlot == 3) {
            if (!isV3Filled && item.word.equals(verb.pastParticiple, ignoreCase = true)) {
                fillV3Container(item)
                checkRoundCompletion(verb)
            } else {
                handleErrorDropped(item, verb)
            }
        }
    }

    private fun fillV2Container(item: BatteryCardItem) {
        isV2Filled = true
        cardAdapter.markUsedById(item.id)

        binding.ivV2SlotIcon.visibility = View.GONE
        binding.tvV2SlotText.text = item.word
        binding.tvV2SlotText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvV2SlotText.textSize = 15f
        binding.tvV2SlotSubtext.text = item.ipa
        binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)

        AudioSpeaker.speak(this, item.word, binding.capsule2)
    }

    private fun fillV3Container(item: BatteryCardItem) {
        isV3Filled = true
        cardAdapter.markUsedById(item.id)

        binding.ivV3SlotIcon.visibility = View.GONE
        binding.tvV3SlotText.text = item.word
        binding.tvV3SlotText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvV3SlotText.textSize = 15f
        binding.tvV3SlotSubtext.text = item.ipa
        binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)

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

    private fun handleErrorDropped(item: BatteryCardItem, verb: Verb) {
        binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
