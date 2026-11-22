package com.example.verbolandia

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
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
    private var currentActiveSlot = 2 // 2 for V2 Pasado, 3 for V3 Participio
    private var comboCount = 1
    private var completedTriads = 0

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

        currentActiveSlot = 2

        // Top progress
        binding.chipProgress.text = getString(
            R.string.battery_round_progress_format,
            currentVerbIndex + 1,
            targetVerbs.size
        )

        // Verb Card Info
        binding.badgeVerbType.text = if (verb.isIrregular) "IRREGULAR" else "REGULAR"
        binding.badgeVerbLevel.text = "NIVEL ${verb.level.uppercase()}"
        binding.tvTargetVerbBase.text = verb.base.uppercase()
        binding.tvTargetSpanish.text = verb.spanishMeaning

        // Reset Capsules
        resetCapsulesUI(verb)

        // Generate Conveyor Cards
        val cards = generateConveyorCards(verb)
        cardAdapter.submitList(cards)

        // Reset circuit status card
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        binding.ivCircuitIcon.setImageResource(R.drawable.ic_target)
        binding.ivCircuitIcon.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.tvCircuitStatusTitle.text = getString(R.string.battery_circuit_stable_title)
        binding.tvCircuitStatusDesc.text = getString(R.string.battery_circuit_stable_desc)

        // Instruction
        binding.tvConveyorInstruction.text = getString(R.string.battery_instruction_v2_format)

        // Pronounce base verb
        AudioSpeaker.speak(this, verb.base, binding.btnSpeaker)
    }

    private fun resetCapsulesUI(verb: Verb) {
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val outlineColor = ContextCompat.getColor(this, R.color.outline_variant)
        val onSurfaceVariant = ContextCompat.getColor(this, R.color.on_surface_variant)

        // Capsule 1 (V1 Base - preloaded)
        binding.tvV1Word.text = verb.base
        binding.tvV1Ipa.text = verb.ipaBase.ifBlank { "/${verb.base}/" }
        binding.tvV1BottomStatus.text = getString(R.string.battery_absorbed)
        binding.ivV1Status.setImageResource(R.drawable.ic_check)
        binding.ivV1Status.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))

        // Capsule 2 (V2 Pasado - Active open slot)
        binding.capsule2.strokeColor = primaryColor
        binding.tagV2.background = ContextCompat.getDrawable(this, R.drawable.bg_focus_pill)
        binding.tagV2.setTextColor(primaryColor)
        binding.ivV2Status.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV2Status.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
        binding.ivV2SlotIcon.visibility = View.VISIBLE
        binding.ivV2SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV2SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.tvV2SlotText.text = getString(R.string.battery_insert_v2)
        binding.tvV2SlotSubtext.text = getString(R.string.battery_slot_open)
        binding.tvV2BottomStatus.text = getString(R.string.battery_waiting)

        // Capsule 3 (V3 Participio - Locked on standby)
        binding.capsule3.strokeColor = outlineColor
        binding.tagV3.background = ContextCompat.getDrawable(this, R.drawable.bg_icon_button_soft)
        binding.tagV3.setTextColor(onSurfaceVariant)
        binding.ivV3Status.setImageResource(R.drawable.ic_lock)
        binding.ivV3Status.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
        binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_normal)
        binding.ivV3SlotIcon.visibility = View.VISIBLE
        binding.ivV3SlotIcon.setImageResource(R.drawable.ic_lock)
        binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
        binding.tvV3SlotText.text = getString(R.string.battery_receptor_v3)
        binding.tvV3SlotSubtext.text = getString(R.string.battery_on_standby)
        binding.tvV3BottomStatus.text = getString(R.string.battery_vacant)
    }

    private fun generateConveyorCards(verb: Verb): List<BatteryCardItem> {
        val list = mutableListOf<BatteryCardItem>()

        // 1. Correct V2 Pasado
        list.add(
            BatteryCardItem(
                id = "v2_${verb.past}",
                word = verb.past,
                ipa = verb.ipaPast.ifBlank { "/${verb.past}/" },
                tag = getString(R.string.battery_tag_past),
                role = "Pasado Simple",
                targetTense = 2
            )
        )

        // 2. Correct V3 Participio
        list.add(
            BatteryCardItem(
                id = "v3_${verb.pastParticiple}",
                word = verb.pastParticiple,
                ipa = verb.ipaParticiple.ifBlank { "/${verb.pastParticiple}/" },
                tag = getString(R.string.battery_tag_participle),
                role = "Participio",
                targetTense = 3
            )
        )

        // 3. Morphological Trap (-ed for irregulars, -en for regulars)
        if (verb.isIrregular) {
            val trapWord = if (verb.base.endsWith("e", true)) "${verb.base}d" else "${verb.base}ed"
            list.add(
                BatteryCardItem(
                    id = "trap_ed",
                    word = trapWord,
                    ipa = "/${trapWord}/",
                    tag = getString(R.string.battery_tag_trap),
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
                    id = "trap_en",
                    word = trapWord,
                    ipa = "/${trapWord}/",
                    tag = getString(R.string.battery_tag_trap),
                    role = "Trampa -en",
                    isTrap = true,
                    trapReason = "¡Cuidado! '${verb.base}' es regular y termina en '-ed', no en '-en'.",
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
                tag = getString(R.string.battery_tag_gerund),
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
                tag = getString(R.string.battery_tag_form),
                role = "Forma Alterna",
                isTrap = true,
                trapReason = getString(R.string.battery_circuit_error_wrong_time),
                targetTense = 0
            )
        )

        // 6. Real lexical distractor from other catalog verbs
        val distractorVerb = allCatalogVerbs
            .filter { !it.base.equals(verb.base, ignoreCase = true) }
            .shuffled()
            .firstOrNull()

        if (distractorVerb != null) {
            val distWord = if (distractorVerb.isIrregular) distractorVerb.past else distractorVerb.pastParticiple
            list.add(
                BatteryCardItem(
                    id = "dist_$distWord",
                    word = distWord,
                    ipa = distractorVerb.ipaPast.ifBlank { "/$distWord/" },
                    tag = getString(R.string.battery_tag_distractor),
                    role = "Distractor Léxico",
                    trapReason = "La palabra '$distWord' pertenece a otro verbo ('${distractorVerb.base}').",
                    targetTense = 0
                )
            )
        }

        return list.distinctBy { it.word.lowercase() }.shuffled()
    }

    private fun handleCardSelected(item: BatteryCardItem, position: Int) {
        val verb = getCurrentVerb() ?: return

        if (item.targetTense == currentActiveSlot) {
            // SUCCESSFUL INSERTION!
            item.isUsed = true
            cardAdapter.notifyItemChanged(position)

            if (currentActiveSlot == 2) {
                // Completed V2 Pasado!
                fillV2Capsule(item.word, item.ipa)
                AudioSpeaker.speak(this, item.word, binding.capsule2)

                // Unlock Slot 3 (V3 Participio)
                unlockV3Capsule()
            } else if (currentActiveSlot == 3) {
                // Completed V3 Participio!
                fillV3Capsule(item.word, item.ipa)
                completeRound(verb)
            }
        } else {
            // INCORRECT CARD (Multiple attempts allowed!)
            val holder = binding.rvOptionCards.findViewHolderForAdapterPosition(position)
                    as? BatteryCardAdapter.ViewHolder
            holder?.shakeAndHighlightError()

            comboCount = 1
            binding.tvStreakFooter.text = getString(R.string.battery_streak_format, 0)

            val errorColor = ContextCompat.getColor(this, R.color.error)
            binding.ivCircuitIcon.setImageResource(R.drawable.ic_warning)
            binding.ivCircuitIcon.imageTintList = ColorStateList.valueOf(errorColor)
            binding.tvCircuitStatusTitle.text = "¡Interferencia de Circuito!"
            binding.tvCircuitStatusDesc.text = if (item.trapReason.isNotBlank()) {
                item.trapReason
            } else {
                getString(R.string.battery_circuit_error_wrong_time)
            }

            // Record practice error in DB without kicking user out
            lifecycleScope.launch(Dispatchers.IO) {
                verbRepo.recordPracticeAttempt(verb.base, false, "Batería de Refuerzo")
            }
        }
    }

    private fun fillV2Capsule(word: String, ipa: String) {
        binding.ivV2SlotIcon.visibility = View.GONE
        binding.tvV2SlotText.text = word
        binding.tvV2SlotText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvV2SlotText.textSize = 16f
        binding.tvV2SlotSubtext.text = ipa
        binding.boxV2.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)
        binding.ivV2Status.setImageResource(R.drawable.ic_check)
        binding.ivV2Status.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))
        binding.tvV2BottomStatus.text = getString(R.string.battery_absorbed)
    }

    private fun unlockV3Capsule() {
        currentActiveSlot = 3
        val primaryColor = ContextCompat.getColor(this, R.color.primary)

        binding.capsule3.strokeColor = primaryColor
        binding.tagV3.background = ContextCompat.getDrawable(this, R.drawable.bg_focus_pill)
        binding.tagV3.setTextColor(primaryColor)
        binding.ivV3Status.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV3Status.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.ivV3SlotIcon.setImageResource(R.drawable.ic_arrow_downward)
        binding.ivV3SlotIcon.imageTintList = ColorStateList.valueOf(primaryColor)
        binding.tvV3SlotText.text = getString(R.string.battery_insert_v3)
        binding.tvV3SlotSubtext.text = getString(R.string.battery_slot_open)
        binding.tvV3BottomStatus.text = getString(R.string.battery_waiting)

        binding.tvConveyorInstruction.text = getString(R.string.battery_instruction_v3_format)
        binding.tvCircuitStatusTitle.text = "Ranura V2 Absorbida"
        binding.tvCircuitStatusDesc.text = "¡Excelente! Ahora selecciona el Participio V3 para estabilizar el circuito."
    }

    private fun fillV3Capsule(word: String, ipa: String) {
        binding.ivV3SlotIcon.visibility = View.GONE
        binding.tvV3SlotText.text = word
        binding.tvV3SlotText.setTextColor(ContextCompat.getColor(this, R.color.primary))
        binding.tvV3SlotText.textSize = 16f
        binding.tvV3SlotSubtext.text = ipa
        binding.boxV3.background = ContextCompat.getDrawable(this, R.drawable.bg_triad_slot_completed)
        binding.ivV3Status.setImageResource(R.drawable.ic_check)
        binding.ivV3Status.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success))
        binding.tvV3BottomStatus.text = getString(R.string.battery_absorbed)
    }

    private fun completeRound(verb: Verb) {
        completedTriads++
        comboCount++

        val streakText = getString(R.string.battery_streak_format, comboCount)
        binding.tvStreakFooter.text = streakText

        val chargePct = ((currentVerbIndex + 1) * 20).coerceAtMost(100)
        binding.progressBattery.setProgressCompat(chargePct, true)
        binding.tvChargePct.text = "$chargePct%"

        val successColor = ContextCompat.getColor(this, R.color.success)
        binding.ivCircuitIcon.setImageResource(R.drawable.ic_check)
        binding.ivCircuitIcon.imageTintList = ColorStateList.valueOf(successColor)
        binding.tvCircuitStatusTitle.text = "¡Sobrecarga de Ronda Exitosa!"
        binding.tvCircuitStatusDesc.text = getString(
            R.string.battery_circuit_success_round_format,
            verb.base,
            verb.past,
            verb.pastParticiple
        )

        // Pronounce full triad
        AudioSpeaker.speak(this, "${verb.base}, ${verb.past}, ${verb.pastParticiple}", binding.cardTargetVerb)

        // Record success attempt in DB
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
        }, 1500)
    }

    private fun showMnemonicHint() {
        val verb = getCurrentVerb() ?: return
        val tip = verb.commonErrorTip.ifBlank {
            "Formas de este verbo:\nBase: ${verb.base}\nPasado: ${verb.past}\nParticipio: ${verb.pastParticiple}"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.battery_hint_label) + " • " + verb.base.uppercase())
            .setMessage(tip)
            .setPositiveButton("Entendido", null)
            .show()
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
    }
}
