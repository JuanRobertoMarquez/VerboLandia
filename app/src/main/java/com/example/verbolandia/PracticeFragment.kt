package com.example.verbolandia

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import com.example.verbolandia.databinding.FragmentPracticeBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.example.verbolandia.ui.RightAction
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.util.NotificationHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Calendar

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    private var selectedLevelMode: String = "BASIC" // "BASIC", "INTERMEDIATE", "ADVANCED"
    private var dailyGoalTarget: Int = 10

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

        dailyGoalTarget = loadDailyGoal()

        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Logo,
            eyebrow = getString(R.string.app_name),
            title = getString(R.string.practice_title),
            rightActions = listOf(RightAction.Avatar())
        )

        setupDailyGoalInteraction()
        setupLevelSelectors()
        setupPracticeModalities()
        setupDateTimePicker()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
        updateDailyGoalUI()
    }

    private fun loadDailyGoal(): Int {
        val prefs = requireContext().getSharedPreferences("practice_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("daily_goal_target", 10)
    }

    private fun saveDailyGoal(goal: Int) {
        dailyGoalTarget = goal
        val prefs = requireContext().getSharedPreferences("practice_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("daily_goal_target", goal).apply()
        updateDailyGoalUI()
        Snackbar.make(binding.root, getString(R.string.toast_goal_updated, goal), Snackbar.LENGTH_SHORT).show()
    }

    private fun setupDailyGoalInteraction() {
        val listener = View.OnClickListener { showDailyGoalDialog() }
        binding.cardGoal.setOnClickListener(listener)
        binding.btnEditGoal.setOnClickListener(listener)
    }

    private fun showDailyGoalDialog() {
        val options = intArrayOf(5, 10, 15, 20, 30)
        val optionStrings = options.map { getString(R.string.goal_option_format, it) }.toTypedArray()
        val currentIndex = options.indexOf(dailyGoalTarget).let { if (it >= 0) it else 1 }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_daily_goal_title)
            .setSingleChoiceItems(optionStrings, currentIndex) { dialog, which ->
                saveDailyGoal(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateDailyGoalUI() {
        val summary = viewModel.progressSummary.value
        val todayCount = summary.todayCompletedCount
        binding.tvGoalStatus.text = getString(R.string.goal_status_format, todayCount, dailyGoalTarget)
        val progressPct = ((todayCount.toDouble() / dailyGoalTarget) * 100).coerceAtMost(100.0).toInt()
        binding.progressGoal.progress = progressPct
        binding.tvGoalPct.text = getString(R.string.goal_pct_format, progressPct)
        binding.tvGoalRemaining.text = if (todayCount >= dailyGoalTarget) {
            getString(R.string.goal_completed)
        } else {
            getString(R.string.goal_remaining_format, dailyGoalTarget - todayCount)
        }
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
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
        val surfaceColor = ContextCompat.getColor(requireContext(), R.color.surface_container_low)
        val outlineColor = ContextCompat.getColor(requireContext(), R.color.outline_variant)

        val isBasic = selectedLevelMode == "BASIC"
        val isInter = selectedLevelMode == "INTERMEDIATE"
        val isAdv = selectedLevelMode == "ADVANCED"

        styleLevelButton(binding.btnLevelBasic, isBasic, primaryColor, surfaceColor, whiteColor, mutedColor, outlineColor)
        styleLevelButton(binding.btnLevelInter, isInter, primaryColor, surfaceColor, whiteColor, mutedColor, outlineColor)
        styleLevelButton(binding.btnLevelAdv, isAdv, primaryColor, surfaceColor, whiteColor, mutedColor, outlineColor)
    }

    private fun styleLevelButton(
        button: MaterialButton,
        isSelected: Boolean,
        primaryColor: Int,
        surfaceColor: Int,
        whiteColor: Int,
        mutedColor: Int,
        outlineColor: Int
    ) {
        if (isSelected) {
            button.backgroundTintList = ColorStateList.valueOf(primaryColor)
            button.setTextColor(whiteColor)
            button.strokeColor = ColorStateList.valueOf(primaryColor)
            button.strokeWidth = 0
        } else {
            button.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            button.setTextColor(mutedColor)
            button.strokeColor = ColorStateList.valueOf(outlineColor)
            button.strokeWidth = (1 * resources.displayMetrics.density).toInt()
        }
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
                            val timeStr = String.format("%02d:%02d", selectedHour, selectedMinute)
                            val dateStr = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                            binding.tvReminderResult.text = getString(R.string.reminder_scheduled_format, dateStr, timeStr)

                            val randomVerb = viewModel.dailyVerbs.value.randomOrNull() ?: Verb(
                                base = "practice",
                                thirdPerson = "practices",
                                past = "practiced",
                                pastParticiple = "practiced",
                                gerund = "practicing",
                                spanishMeaning = "Practicar"
                            )
                            NotificationHelper.showVerbOfDayNotification(requireContext(), randomVerb)

                            Snackbar.make(binding.root, getString(R.string.reminder_scheduled_format, dateStr, timeStr), Snackbar.LENGTH_SHORT).show()
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
                viewModel.progressSummary.collect {
                    updateDailyGoalUI()
                }
            }
        }
    }

    private fun setupPracticeModalities() {
        binding.btnStartGuidedReview.setOnClickListener {
            startGuidedReviewSession()
        }

        binding.cardModality1.setOnClickListener {
            startQuizModal(getString(R.string.mod_1_title))
        }

        binding.cardModality2.setOnClickListener {
            startQuizModal(getString(R.string.mod_2_title))
        }

        // Modality 3: Billar de Verbos opens dedicated interactive screen!
        binding.cardModality3.setOnClickListener {
            val intent = Intent(requireContext(), VerbBilliardsActivity::class.java)
            startActivity(intent)
        }

        binding.cardModality4.setOnClickListener {
            startQuizModal(getString(R.string.mod_4_title))
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
        val titleText = getString(R.string.guided_review_title_format, targetVerb.base.uppercase(), targetVerb.spanishMeaning)

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
                    getString(R.string.guided_review_success_format, targetVerb.base, targetVerb.past)
                } else {
                    getString(R.string.guided_review_error_format, targetVerb.base, targetVerb.past)
                }

                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.action_notification)) {
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

        val titleText = getString(R.string.quiz_modal_title_format, selectedLevelMode, targetVerb.base.uppercase(), targetVerb.spanishMeaning)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleText)
            .setItems(optionsArray) { dialog, which ->
                dialog.dismiss()
                val selectedOption = optionsArray[which]
                val isCorrect = selectedOption == correctAnswer

                viewModel.recordPracticeAttempt(targetVerb.base, isCorrect, modalityName)

                val msg = if (isCorrect) {
                    NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    getString(R.string.quiz_success_format, targetVerb.base, correctAnswer)
                } else {
                    getString(R.string.quiz_error_format, targetVerb.base, correctAnswer)
                }

                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.action_notification)) {
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
