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
import com.example.verbolandia.databinding.FragmentPracticeBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.SharedViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

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

        setupPracticeModalities()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
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
            startQuizModal("Batería de Refuerzo")
        }

        binding.cardModality1.setOnClickListener {
            startQuizModal("Tríada Rápida")
        }

        binding.cardModality2.setOnClickListener {
            startQuizModal("Completar en Contexto")
        }

        binding.cardModality3.setOnClickListener {
            startQuizModal("Billar de Verbos")
        }

        binding.cardModality4.setOnClickListener {
            startQuizModal("Caza de Errores")
        }
    }

    private fun startQuizModal(modalityName: String) {
        val verbs = viewModel.dailyVerbs.value
        if (verbs.isEmpty()) return

        val targetVerb = verbs.random()
        val correctAnswer = targetVerb.past

        // Generate plausible distractors (e.g. gerund, base, or regularized past)
        val distractor1 = targetVerb.gerund
        val distractor2 = if (targetVerb.base.endsWith("e")) "${targetVerb.base}d" else "${targetVerb.base}ed"
        val distractor3 = targetVerb.thirdPerson

        val options = listOf(correctAnswer, distractor1, distractor2, distractor3)
            .distinct()
            .shuffled()
            .take(3)

        val optionsArray = options.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Práctica: $modalityName")
            .setMessage("¿Cuál es el PASADO SIMPLE de '${targetVerb.base.uppercase()}' (${targetVerb.spanishMeaning})?")
            .setItems(optionsArray) { dialog, which ->
                dialog.dismiss()
                val selectedOption = optionsArray[which]
                val isCorrect = selectedOption == correctAnswer

                viewModel.recordPracticeAttempt(targetVerb.base, isCorrect, modalityName)

                val msg = if (isCorrect) {
                    "¡Correcto! ${targetVerb.base} ➔ $correctAnswer"
                } else {
                    "Incorrecto. El pasado de ${targetVerb.base} es $correctAnswer (seleccionaste $selectedOption)"
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
