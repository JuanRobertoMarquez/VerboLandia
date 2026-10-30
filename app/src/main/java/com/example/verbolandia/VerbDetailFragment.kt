package com.example.verbolandia

import android.content.Intent
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
import com.example.verbolandia.databinding.FragmentVerbDetailBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.ui.VerbDetailBinder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VerbDetailFragment : Fragment() {

    private var _binding: FragmentVerbDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVerbDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDetailVerb.collect { verb ->
                    if (verb != null) {
                        val stats = viewModel.progressSummary.value.topWeakVerbs.find { it.base == verb.base }
                            ?: com.example.verbolandia.util.VerbStatsRecord(verb.base, isFavorite = verb.isFavorite, mastery = verb.mastery)

                        VerbDetailBinder.bindVerb(
                            binding = binding,
                            verb = verb,
                            stats = stats,
                            onBookmarkClick = {
                                viewModel.toggleFavorite(verb)
                                val msg = if (verb.isFavorite) "Eliminado de favoritos" else "Añadido a favoritos"
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                                    .setAction("DESHACER") {
                                        viewModel.toggleFavorite(verb)
                                    }
                                    .show()
                            },
                            onShareClick = {
                                shareVerb(verb)
                            },
                            onPracticeClick = {
                                findNavController().navigate(R.id.nav_practice)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun shareVerb(verb: Verb) {
        val shareText = "Verbo en Verbolandia: ${verb.base.uppercase()} (${verb.spanishMeaning})\n" +
                "Pasado: ${verb.past}\n" +
                "Participio: ${verb.pastParticiple}\n" +
                "Gerundio: ${verb.gerund}"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir Verbo")
        startActivity(shareIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
