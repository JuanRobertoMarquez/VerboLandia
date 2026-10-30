package com.example.verbolandia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.FragmentPracticeBinding
import com.example.verbolandia.util.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class PracticeFragment : Fragment() {

    private var _binding: FragmentPracticeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VerbRepository

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

        repository = VerbRepository(requireContext())

        updateProgressUI()
        setupPracticeModalities()
    }

    private fun updateProgressUI() {
        val count = repository.getPracticedCount()
        binding.tvGoalStatus.text = "$count de 10 ejercicios completados"
        val progressPct = (count * 10).coerceAtMost(100)
        binding.progressGoal.progress = progressPct
        binding.tvGoalPct.text = "$progressPct%"
    }

    private fun setupPracticeModalities() {
        binding.btnStartGuidedReview.setOnClickListener {
            startQuizModal("Batería de Refuerzo")
        }

        binding.cardModality1.setOnClickListener {
            startQuizModal("Tríada Rápida (V1 - V2 - V3)")
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
        val randomVerbs = repository.getRandomVerbs(3)
        if (randomVerbs.isEmpty()) return

        val targetVerb = randomVerbs.first()

        // 1. Alert Dialog for quiz confirmation & question
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Práctica: $modalityName")
            .setMessage("¿Cuál es el PASADO SIMPLE de '${targetVerb.base.uppercase()}' (${targetVerb.spanishMeaning})?")
            .setPositiveButton(targetVerb.past) { dialog, _ ->
                dialog.dismiss()
                repository.incrementPracticedCount()
                repository.updateMastery(targetVerb.base, (targetVerb.mastery + 25).coerceAtMost(100))
                updateProgressUI()

                // Send Notification as part of the practice workflow
                NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)

                // Show Snackbar with feedback
                Snackbar.make(binding.root, "¡Correcto! ${targetVerb.base} -> ${targetVerb.past}", Snackbar.LENGTH_LONG)
                    .setAction("NOTIFICACIÓN") {
                        NotificationHelper.showVerbOfDayNotification(requireContext(), targetVerb)
                    }
                    .show()
            }
            .setNegativeButton(targetVerb.gerund) { dialog, _ ->
                dialog.dismiss()
                Snackbar.make(binding.root, "Incorrecto. El pasado de ${targetVerb.base} es ${targetVerb.past}", Snackbar.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
