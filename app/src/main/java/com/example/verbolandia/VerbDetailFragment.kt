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
import com.example.verbolandia.util.TranslationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VerbDetailFragment : Fragment() {

    private var _binding: FragmentVerbDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var translationManager: TranslationManager

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

        translationManager = TranslationManager(requireContext())

        com.example.verbolandia.ui.AppBarHelper.configure(
            binding = binding.appBar,
            left = com.example.verbolandia.ui.LeftSlot.Back { findNavController().navigateUp() },
            eyebrow = "DETALLE DE VERBO",
            title = getString(R.string.verb_detail_title)
        )

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
                                val msg = if (verb.isFavorite) {
                                    getString(R.string.snackbar_favorite_removed)
                                } else {
                                    getString(R.string.snackbar_favorite_added)
                                }
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                                    .setAction(getString(R.string.snackbar_action_undo)) {
                                        viewModel.toggleFavorite(verb)
                                    }
                                    .show()
                            },
                            onShareClick = {
                                shareVerb(verb)
                            },
                            onPracticeClick = {
                                com.example.verbolandia.ui.VerbPracticeBottomSheetFragment.newInstance(verb)
                                    .show(childFragmentManager, "VerbPracticeBottomSheet")
                            },
                            onTranslateClick = {
                                if (verb.spanishMeaning.isBlank()) {
                                    requestTranslationDownload(verb)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestTranslationDownload(verb: Verb) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_download_model_title))
            .setMessage(getString(R.string.dialog_download_model_msg, verb.base))
            .setPositiveButton(getString(R.string.dialog_download_model_positive)) { dialog, _ ->
                dialog.dismiss()
                performTranslation(verb)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun performTranslation(verb: Verb) {
        Snackbar.make(binding.root, getString(R.string.snackbar_translating), Snackbar.LENGTH_INDEFINITE).show()
        viewLifecycleOwner.lifecycleScope.launch {
            translationManager.translateAndSave(
                verbBase = verb.base,
                onSuccess = { translatedText ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.snackbar_translated_format, translatedText),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    viewModel.onVerbOpened(verb)
                },
                onError = { error ->
                    val errorMsg = error.localizedMessage ?: ""
                    Snackbar.make(
                        binding.root,
                        getString(R.string.snackbar_translate_error_format, errorMsg),
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(getString(R.string.snackbar_action_retry)) {
                            performTranslation(verb)
                        }
                        .show()
                }
            )
        }
    }

    private fun shareVerb(verb: Verb) {
        VerbDetailBinder.shareVerb(requireContext(), verb)
    }

    override fun onDestroyView() {
        translationManager.close()
        super.onDestroyView()
        _binding = null
    }
}
