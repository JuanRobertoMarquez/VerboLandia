package com.example.verbolandia

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.verbolandia.databinding.FragmentProgressBinding
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.example.verbolandia.ui.RightAction
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.ui.VerbPracticeBottomSheetFragment
import com.example.verbolandia.util.ProgressSummary
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val localeEs = Locale.forLanguageTag("es-ES")
        Locale.setDefault(localeEs)
        val config = resources.configuration
        config.setLocale(localeEs)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Logo,
            eyebrow = getString(R.string.app_name),
            title = getString(R.string.domain_metrics_title),
            rightActions = listOf(RightAction.Avatar())
        )

        setupButtons()
        setupCalendarView()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
        val today = Calendar.getInstance()
        queryAndDisplayDaySummary(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun setupButtons() {
        binding.btnGenerateTestErrors.setOnClickListener {
            val summary = viewModel.progressSummary.value
            val weakVerbsCount = summary.topWeakVerbs.size

            val message = if (weakVerbsCount > 0) {
                getString(R.string.btn_test_subtext_format, weakVerbsCount)
            } else {
                getString(R.string.snackbar_generating_test_errors)
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()

            // Iniciar el quiz adaptativo centrado en errores
            val intent = android.content.Intent(requireContext(), VerbBilliardsActivity::class.java).apply {
                putExtra(VerbBilliardsActivity.EXTRA_ERROR_MODE, true)
            }
            startActivity(intent)
        }

        binding.btnGenerateTestErrors.setOnLongClickListener {
            showConfirmResetDialog()
            true
        }
    }

    private fun showConfirmResetDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_reset_title)
            .setMessage(R.string.dialog_reset_msg)
            .setPositiveButton(R.string.dialog_reset_positive) { dialog, _ ->
                dialog.dismiss()
                viewModel.resetProgress()
                Snackbar.make(binding.root, R.string.snackbar_progress_reset, Snackbar.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun setupCalendarView() {
        binding.myCalendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            queryAndDisplayDaySummary(year, month, dayOfMonth)
        }
    }

    private fun queryAndDisplayDaySummary(year: Int, month: Int, dayOfMonth: Int) {
        val dateFormatted = String.format(Locale.forLanguageTag("es-ES"), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
        binding.tvDaySummaryDate.text = dateFormatted

        viewLifecycleOwner.lifecycleScope.launch {
            val attempts = viewModel.getAttemptsForDate(year, month, dayOfMonth)
            if (!isAdded) return@launch

            if (attempts.isNotEmpty()) {
                binding.layoutDayActivity.visibility = View.VISIBLE
                binding.tvDayNoActivity.visibility = View.GONE

                val total = attempts.size
                val correct = attempts.count { it.correct }
                val accuracy = if (total > 0) ((correct.toDouble() / total) * 100).toInt() else 0

                binding.tvDayExercisesCount.text = getString(R.string.day_exercises_count_format, total)
                binding.tvDayAccuracy.text = getString(R.string.day_accuracy_format, accuracy)

                val uniqueVerbs = attempts.map { it.base.replaceFirstChar { c -> c.uppercase() } }.distinct().take(6)
                val verbsJoined = uniqueVerbs.joinToString(", ")
                binding.tvDayVerbsPracticed.text = getString(R.string.day_verbs_practiced_format, verbsJoined)
            } else {
                binding.layoutDayActivity.visibility = View.GONE
                binding.tvDayNoActivity.visibility = View.VISIBLE
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progressSummary.collect { summary ->
                    bindProgressSummary(summary)
                }
            }
        }
    }

    private fun bindProgressSummary(summary: ProgressSummary) {
        // Metric 1: Retención Global 30 días
        binding.tvMetric1Val.text = getString(R.string.retention_pct_format, summary.globalRetention30DaysPct)
        binding.badgeMetric1Diff.text = when {
            summary.globalRetentionDiffPct > 0 -> getString(R.string.diff_pct_up, summary.globalRetentionDiffPct)
            summary.globalRetentionDiffPct < 0 -> getString(R.string.diff_pct_down, -summary.globalRetentionDiffPct)
            else -> getString(R.string.diff_pct_equal)
        }

        // Metric 2: Verbos Dominados
        val totalVerbs = viewModel.categoryCounts.value.first.let { if (it > 0) it else 180 }
        binding.tvMetric2Val.text = getString(R.string.mastered_count_format, summary.masteredVerbsCount, totalVerbs)
        binding.badgeMetric2Pct.text = getString(R.string.mastered_pct_format, summary.masteredVerbsPct)

        // Metric 3: Zona Crítica
        binding.tvMetric3Val.text = getString(R.string.critical_zone_format, summary.criticalZoneCount)

        // Metric 4: Práctica Diaria (Ejercicios vs Meta)
        val prefs = requireContext().getSharedPreferences("practice_prefs", Context.MODE_PRIVATE)
        val dailyGoalTarget = prefs.getInt("daily_goal_target", 10)
        val goalPct = if (dailyGoalTarget > 0) {
            ((summary.todayCompletedCount.toDouble() / dailyGoalTarget) * 100).coerceAtMost(100.0).toInt()
        } else {
            0
        }
        binding.tvMetric4Val.text = getString(R.string.daily_practice_format, summary.todayCompletedCount, dailyGoalTarget)
        binding.badgeMetric4Goal.text = getString(R.string.daily_practice_goal_badge_format, goalPct)

        // Weekly Accuracy Chart
        binding.badgeChartMax.text = getString(R.string.chart_max_format, summary.weeklyMaxPct)
        bindWeeklyChart(summary.weeklyAccuracyPctList)

        // Typology Accuracy
        binding.tvTypology1Val.text = getString(R.string.retention_pct_format, summary.regularAccuracyPct)
        binding.progressTypology1.progress = summary.regularAccuracyPct

        binding.tvTypology2Val.text = getString(R.string.retention_pct_format, summary.invariantAccuracyPct)
        binding.progressTypology2.progress = summary.invariantAccuracyPct

        binding.tvTypology3Val.text = getString(R.string.retention_pct_format, summary.vowelChangeAccuracyPct)
        binding.progressTypology3.progress = summary.vowelChangeAccuracyPct

        // Weak Points Cards
        bindWeakCards(summary)

        // Bottom Action Subtitle (Dynamic based on critical verbs count)
        val criticalCount = summary.criticalZoneCount
        if (criticalCount > 0) {
            binding.tvTestSubtext.text = getString(R.string.btn_test_subtext_format, criticalCount)
        } else {
            binding.tvTestSubtext.text = getString(R.string.btn_test_subtext_empty)
        }
    }

    private fun bindWeeklyChart(pctList: List<Int>) {
        if (pctList.size < 7) return
        val bars = listOf(
            binding.bar1, binding.bar2, binding.bar3,
            binding.bar4, binding.bar5, binding.bar6, binding.bar7
        )

        val maxHeightDp = 100
        val density = resources.displayMetrics.density

        bars.forEachIndexed { i, barView ->
            val pct = pctList[i]
            val heightDp = (pct * (maxHeightDp / 100.0)).coerceAtLeast(6.0).toInt()
            val params = barView.layoutParams
            params.height = (heightDp * density).toInt()
            barView.layoutParams = params
        }
    }

    private fun bindWeakCards(summary: ProgressSummary) {
        val weakVerbs = summary.topWeakVerbs

        if (weakVerbs.isEmpty()) {
            binding.cardWeak1.visibility = View.GONE
            binding.cardWeak2.visibility = View.GONE
            binding.cardWeak3.visibility = View.GONE
            binding.cardWeak4.visibility = View.GONE
            binding.badgeWeakCritical.text = getString(R.string.weak_critical_count_format, 0)
            return
        }

        binding.badgeWeakCritical.text = getString(R.string.weak_critical_count_format, weakVerbs.size)

        val cards = listOf(binding.cardWeak1, binding.cardWeak2, binding.cardWeak3, binding.cardWeak4)
        val names = listOf(binding.tvWeak1Name, binding.tvWeak2Name, binding.tvWeak3Name, binding.tvWeak4Name)
        val phonetics = listOf(binding.tvWeak1Phonetic, binding.tvWeak2Phonetic, binding.tvWeak3Phonetic, binding.tvWeak4Phonetic)
        val errs = listOf(binding.badgeWeak1Err, binding.badgeWeak2Err, binding.badgeWeak3Err, binding.badgeWeak4Err)
        val forms = listOf(binding.tvWeak1Forms, binding.tvWeak2Forms, binding.tvWeak3Forms, binding.tvWeak4Forms)
        val notes = listOf(binding.tvWeak1Note, binding.tvWeak2Note, binding.tvWeak3Note, binding.tvWeak4Note)
        val lasts = listOf(binding.tvWeak1Last, binding.tvWeak2Last, binding.tvWeak3Last, binding.tvWeak4Last)
        val reviews = listOf(binding.btnReview1, binding.btnReview2, binding.btnReview3, binding.btnReview4)

        viewLifecycleOwner.lifecycleScope.launch {
            cards.forEachIndexed { index, cardView ->
                if (index < weakVerbs.size) {
                    val record = weakVerbs[index]
                    cardView.visibility = View.VISIBLE
                    val verb = viewModel.getVerb(record.base)

                    names[index].text = (verb?.base ?: record.base).replaceFirstChar { it.uppercase() }
                    phonetics[index].text = if (!verb?.phonetic.isNullOrBlank()) "/${verb!!.phonetic}/" else ""
                    errs[index].text = getString(R.string.weak_error_pct_format, record.errorRatePercentage)
                    forms[index].text = if (verb != null) "${verb.past} • ${verb.pastParticiple}" else record.base
                    notes[index].text = getString(R.string.weak_attempts_format, record.correctCount, record.attempts)
                    lasts[index].text = if (record.lastErrorAtMs > 0) getString(R.string.weak_last_error_recent) else getString(R.string.weak_last_error_never)

                    reviews[index].setOnClickListener {
                        if (verb != null && isAdded) {
                            VerbPracticeBottomSheetFragment.newInstance(verb)
                                .show(childFragmentManager, "VerbPracticeBottomSheet")
                        } else if (isAdded) {
                            findNavController().navigate(R.id.nav_practice)
                        }
                    }
                } else {
                    cardView.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
