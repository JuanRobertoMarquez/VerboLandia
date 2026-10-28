package com.example.verbolandia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.verbolandia.databinding.FragmentDictionaryBinding

class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Programar evento de clic en la tarjeta de muestra ("break") para navegar al detalle
        binding.sampleVerbCardBreak.cardVerb.setOnClickListener {
            findNavController().navigate(R.id.action_dictionary_to_verbDetail)
        }

        // Programar evento de clic en la tarjeta "Verbo del día" para navegar al detalle
        binding.cardVerbOfDay.setOnClickListener {
            findNavController().navigate(R.id.action_dictionary_to_verbDetail)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
