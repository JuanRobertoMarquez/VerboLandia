package com.example.verbolandia

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.verbolandia.VerbBilliardsActivity
import com.example.verbolandia.databinding.FragmentPracticeBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.util.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Calendar

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    private var selectedLevelMode: String = "BASIC" // "BASIC", "INTERMEDIATE", "ADVANCED"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLevelSelectors()
        setupPracticeModalities()
        setupDateTimePicker()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }

    private fun setupLevelSelectors() {
        binding.btnLevelBasic.setOnClickListener {
            selectedLevelMode = "BASIC"
            updateLevelPillsUI()
        }

        binding.btnLevelInter.setOnClickListener {
            selectedLevelMode = "INTERMEDIATE"
            updateLevelPillsUI()
        }

        binding.btnLevelAdv.setOnClickListener {
            selectedLevelMode = "ADVANCED"
            updateLevelPillsUI()
        }

        updateLevelPillsUI()
    }

    private fun updateLevelPillsUI() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.on_surface_variant)

        val isBasic = selectedLevelMode == "BASIC"
        val isInter = selectedLevelMode == "INTERMEDIATE"
        val isAdv = selectedLevelMode == "ADVANCED"

        binding.btnLevelBasic.setTextColor(if (isBasic) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnLevelInter.setTextColor(if (isInter) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnLevelAdv.setTextColor(if (isAdv) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)

        binding.btnLevelBasic.setBackgroundColor(if (isBasic) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnLevelInter.setBackgroundColor(if (isInter) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnLevelAdv.setBackgroundColor(if (isAdv) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
    }

    private fun setupDateTimePicker() {
        binding.btnScheduleReminder.setOnClickListener {
            val c = Calendar.getInstance()
            val year = c.get(Calendar.YEAR)
            val month = c.get(Calendar.MONTH)
            val day = c.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->
                    val hour = c.get(Calendar.HOUR_OF_DAY)
                    val minute = c.get(Calendar.MINUTE)

                    val timePickerDialog = TimePickerDialog(
                        requireContext(),
                        { _, selectedHour, selectedMinute ->
                            val reminderText = "Recordatorio: $selectedDay/${selectedMonth + 1}/$selectedYear a las $selectedHour:${if (selectedMinute < 10) "0$selectedMinute" else selectedMinute}"
                            binding.tvReminderResult.text = reminderText

                            val randomVerb = viewModel.dailyVerbs.value.randomOrNull() ?: Verb(base = "practice", thirdPerson = "practices", past = "practiced", pastParticiple = "practiced", gerund = "practicing", spanishMeaning = "Practicar")
                            NotificationHelper.showVerbOfDayNotification(requireContext(), randomVerb)

                            Snackbar.make(binding.root, "¡Recordatorio programado con éxito!", Snackbar.LENGTH_SHORT).show()
                        },
                        hour,
                        minute,
                        true // 24-hour format
                    )
                    timePickerDialog.show()
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressSummary.collect { summary ->
                    val todayCount = summary.todayCompletedCount
                    binding.tvGoalStatus.text = "$todayCount de 10 ejercicios completados"
                    val progressPct = ((todayCount.toDouble() / 10) * 100).coerceAtMost(100.0).toInt()
                    binding.progressGoal.progress = progressPct
                    binding.tvGoalPct.text = "$progressPct%"
                    binding.tvGoalRemaining.text = if (todayCount >= 10) "¡Meta diaria cumplida!" else "¡Faltan ${10 - todayCount} ejercicios!"
                }
            }
        }
    }

    private fun setupPracticeModalities() {
        binding.btnStartGuidedReview.setOnClickListener {
            startGuidedReviewSession()
        }

        binding.cardModality1.setOnClickListener {
            startQuizModal("Tríada Rápida")
        }

        binding.cardModality2.setOnClickListener {
            startQuizModal("Completar en Contexto")
        }

        // Modality 3: Billar de Verbos opens dedicated interactive screen!
        binding.cardModality3.setOnClickListener {
            val intent = Intent(requireContext(), VerbBilliardsActivity::class.java)
            startActivity(intent)
        }

        binding.cardModality4.setOnClickListener {
            startQuizModal("Caza de Errores")
        }
    }

    private fun filterVerbsByPedagogicalLevel(verbs: List<Verb>): List<Verb> {
        return when (selectedLevelMode) {
            "BASIC" -> verbs.filter { !it.isIrregular || it.isCommon }
            "INTERMEDIATE" -> verbs.filter { it.isIrregular }
            "ADVANCED" -> verbs.filter { it.isIrregular && !it.isCommon }.ifEmpty { verbs.filter { it.isIrregular } }
            else -> verbs
        }
    }

    private fun startGuidedReviewSession() {
        val summary = viewModel.progressSummary.value
        val weakBases = summary.topWeakVerbs.map { it.base }
        val dailyVerbs = viewModel.dailyVerbs.value

        val reviewQueue = if (weakBases.isNotEmpty()) {
            dailyVerbs.filter { it.base in weakBases } + dailyVerbs.filter { it.base !in weakBases }
        } else {
            dailyVerbs
        }

        if (reviewQueue.isEmpty()) return

        val targetVerb = reviewQueue.first()
        val titleText = "Repaso Guiado PASO 1 • '${targetVerb.base.uppercase()}' (${targetVerb.spanishMeaning})"

        val options = listOf(
            targetVerb.past,
            targetVerb.gerund,
            if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
        ).distinct().shuffled().toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                val selectedOption = options[which]
                val isCorrect = selectedOption == targetVerb.past

                viewModel.recordPracticeAttempt(targetVerb.base, isCorrect, "Repaso Guiado")

                val msg = if (isCorrect) {
                    NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    "¡Excelente paso de repaso! ${targetVerb.base} ➔ ${targetVerb.past}"
                } else {
                    "Repaso: El pasado de ${targetVerb.base} es ${targetVerb.past}"
                }

                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction("NOTIFICACIÓN") {
                        NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    }
                    .show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun startQuizModal(modalityName: String) {
        val allVerbs = viewModel.dailyVerbs.value
        val levelVerbs = filterVerbsByPedagogicalLevel(allVerbs).ifEmpty { allVerbs }

        if (levelVerbs.isEmpty()) return

        val targetVerb = levelVerbs.random()
        val correctAnswer = targetVerb.past

        val distractor1 = targetVerb.gerund
        val distractor2 = if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
        val distractor3 = targetVerb.thirdPerson

        val options = listOf(correctAnswer, distractor1, distractor2, distractor3)
            .distinct()
            .shuffled()
            .take(3)

        val optionsArray = options.toTypedArray()

        val titleText = "[$selectedLevelMode] ¿Pasado simple de '${targetVerb.base.uppercase()}'? (${targetVerb.spanishMeaning})"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setItems(optionsArray) { dialog, which ->
                dialog.dismiss()
                val selectedOption = optionsArray[which]
                val isCorrect = selectedOption == correctAnswer

                viewModel.recordPracticeAttempt(targetVerb.base, isCorrect, modalityName)

                val msg = if (isCorrect) {
                    NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    "¡Correcto! ${targetVerb.base} ➔ $correctAnswer"
                } else {
                    "Incorrecto. El pasado de ${targetVerb.base} es $correctAnswer (seleccionaste $selectedOption)"
                }

                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction("NOTIFICACIÓN") {
                        NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    }
                    .show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
