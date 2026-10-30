package com.example.verbolandia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.FragmentProgressBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VerbRepository

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

        repository = VerbRepository(requireContext())

        setupActionButtons()
    }

    private fun setupActionButtons() {
        binding.btnGenerateTestErrors.setOnClickListener {
            // Alert Dialog confirmation
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Generar Test Personalizado")
                .setMessage("¿Deseas generar una evaluación enfocada en tus puntos débiles y verbos más consultados?")
                .setPositiveButton("Generar Test") { dialog, _ ->
                    dialog.dismiss()
                    Snackbar.make(binding.root, "Evaluación personalizada generada con éxito", Snackbar.LENGTH_LONG)
                        .setAction("INICIAR") {
                            Snackbar.make(binding.root, "Cargando test...", Snackbar.LENGTH_SHORT).show()
                        }
                        .show()
                }
                .setNegativeButton("Restablecer Progreso") { dialog, _ ->
                    dialog.dismiss()
                    confirmResetProgress()
                }
                .setNeutralButton("Cancelar", null)
                .show()
        }
    }

    private fun confirmResetProgress() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restablecer Progreso")
            .setMessage("¿Estás seguro de que deseas borrar todas tus estadísticas y favoritos?")
            .setPositiveButton("Sí, Restablecer") { dialog, _ ->
                dialog.dismiss()
                repository.resetProgress()
                Snackbar.make(binding.root, "Progreso restablecido a cero", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
