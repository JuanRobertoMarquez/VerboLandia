package com.example.verbolandia

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
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.util.ProgressSummary
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

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

        setupButtons()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }

    private fun setupButtons() {
        binding.btnGenerateTestErrors.setOnClickListener {
            val summary = viewModel.progressSummary.value
            if (summary.topWeakVerbs.isEmpty()) {
                Snackbar.make(binding.root, "¡Excelente! Aún no tienes verbos débiles identificados.", Snackbar.LENGTH_LONG).show()
            } else {
                findNavController().navigate(R.id.nav_practice)
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
        binding.tvMetric1Val.text = "${summary.globalRetention30DaysPct}%"
        binding.badgeMetric1Diff.text = when {
            summary.globalRetentionDiffPct > 0 -> "📈 +${summary.globalRetentionDiffPct}%"
            summary.globalRetentionDiffPct < 0 -> "📉 ${summary.globalRetentionDiffPct}%"
            else -> "—"
        }

        // Metric 2: Verbos Dominados
        binding.tvMetric2Val.text = "${summary.masteredVerbsCount}"
        binding.badgeMetric2Pct.text = "${summary.masteredVerbsPct}% dominados"

        // Metric 3: Zona Crítica
        binding.tvMetric3Val.text = "${summary.criticalZoneCount}"

        // Metric 4: Práctica Diaria (Ejercicios)
        binding.tvMetric4Val.text = "${summary.todayCompletedCount} / 10"

        // Weekly Accuracy Chart
        binding.badgeChartMax.text = "Máx ${summary.weeklyMaxPct}%"
        bindWeeklyChart(summary.weeklyAccuracyPctList)

        // Typology Accuracy
        binding.tvTypology1Val.text = "${summary.regularAccuracyPct}%"
        binding.progressTypology1.progress = summary.regularAccuracyPct

        binding.tvTypology2Val.text = "${summary.invariantAccuracyPct}%"
        binding.progressTypology2.progress = summary.invariantAccuracyPct

        binding.tvTypology3Val.text = "${summary.vowelChangeAccuracyPct}%"
        binding.progressTypology3.progress = summary.vowelChangeAccuracyPct

        // Weak Points Cards
        bindWeakCards(summary)
    }

    private fun bindWeeklyChart(pctList: List<Int>) {
        if (pctList.size < 7) return
        val bars = listOf(
            binding.bar1, binding.bar2, binding.bar3,
            binding.bar4, binding.bar5, binding.bar6, binding.bar7
        )

        // Set bar heights proportionally (max height 100dp)
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
            binding.badgeWeakCritical.text = "0 Críticos"
            return
        }

        binding.badgeWeakCritical.text = "${weakVerbs.size} Críticos"

        val cards = listOf(binding.cardWeak1, binding.cardWeak2, binding.cardWeak3, binding.cardWeak4)
        val names = listOf(binding.tvWeak1Name, binding.tvWeak2Name, binding.tvWeak3Name, binding.tvWeak4Name)
        val errs = listOf(binding.badgeWeak1Err, binding.badgeWeak2Err, binding.badgeWeak3Err, binding.badgeWeak4Err)
        val notes = listOf(binding.tvWeak1Note, binding.tvWeak2Note, binding.tvWeak3Note, binding.tvWeak4Note)
        val reviews = listOf(binding.btnReview1, binding.btnReview2, binding.btnReview3, binding.btnReview4)

        cards.forEachIndexed { index, cardView ->
            if (index < weakVerbs.size) {
                val record = weakVerbs[index]
                cardView.visibility = View.VISIBLE
                names[index].text = record.base.replaceFirstChar { it.uppercase() }
                errs[index].text = "${record.errorRatePercentage}% error"
                notes[index].text = "${record.correctCount} aciertos de ${record.attempts} intentos"
                reviews[index].setOnClickListener {
                    findNavController().navigate(R.id.nav_practice)
                }
            } else {
                cardView.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
