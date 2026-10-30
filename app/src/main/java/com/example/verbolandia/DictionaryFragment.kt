package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.verbolandia.adapter.VerbAdapter
import com.example.verbolandia.databinding.FragmentDictionaryBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.SharedViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private lateinit var adapter: VerbAdapter

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

        setupRecyclerView()
        setupSearchAndFilters()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }

    private fun setupRecyclerView() {
        adapter = VerbAdapter { verb ->
            viewModel.onVerbOpened(verb)
            openVerbDetail(verb)
        }
        binding.rvVerbs.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.onSearchQueryChanged(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.etSearch.text.isNull_or_empty()) {
                val history = viewModel.searchHistory.value
                if (history.isNotEmpty()) {
                    showHistoryList(history)
                }
            }
        }

        binding.btnFilterAll.setOnClickListener {
            viewModel.setFilterCategory("ALL", binding.etSearch.text.toString())
        }

        binding.btnFilterIrregular.setOnClickListener {
            viewModel.setFilterCategory("IRREGULAR", binding.etSearch.text.toString())
        }

        binding.btnFilterRegular.setOnClickListener {
            viewModel.setFilterCategory("REGULAR", binding.etSearch.text.toString())
        }

        // Hide sample card
        binding.sampleVerbCardBreak.root.visibility = View.GONE
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dailyVerbs.collect { verbs ->
                        if (!viewModel.isSearching.value) {
                            adapter.submitList(verbs)
                            binding.tvSectionHeader.text = getString(R.string.list_header_title)
                            binding.tvSectionResults.text = "${verbs.size} de hoy"
                        }
                    }
                }

                launch {
                    viewModel.searchResults.collect { results ->
                        if (viewModel.isSearching.value) {
                            adapter.submitList(results)
                            if (results.isEmpty()) {
                                binding.tvSectionHeader.text = getString(R.string.empty_search_results)
                                binding.tvSectionResults.text = "0 verbos"
                            } else {
                                binding.tvSectionHeader.text = "RESULTADOS DE BÚSQUEDA"
                                binding.tvSectionResults.text = "${results.size} resultados"
                            }
                        }
                    }
                }

                launch {
                    viewModel.categoryCounts.collect { (total, irregular, regular) ->
                        binding.tvActiveVerbsBadge.text = "$total Activos"
                        binding.btnFilterAll.text = "Todos ($total)"
                        binding.btnFilterIrregular.text = "Irregulares ($irregular)"
                        binding.btnFilterRegular.text = "Regulares ($regular)"
                    }
                }

                launch {
                    viewModel.filterCategory.collect { category ->
                        updateFilterPillsUI(category)
                    }
                }

                launch {
                    viewModel.currentDetailVerb.collect { verbOfToday ->
                        if (verbOfToday != null) {
                            binding.tvVerbOfDayTitle.text = "${verbOfToday.base} • ${verbOfToday.spanishMeaning}"
                            binding.tvVerbOfDayV1.text = "Base: ${verbOfToday.base}"
                            binding.tvVerbOfDayV2.text = "Pasado: ${verbOfToday.past}"
                            binding.tvVerbOfDayV3.text = "Part.: ${verbOfToday.pastParticiple}"

                            binding.cardVerbOfDay.setOnClickListener {
                                viewModel.onVerbOpened(verbOfToday)
                                openVerbDetail(verbOfToday)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showHistoryList(history: List<Verb>) {
        binding.tvSectionHeader.text = getString(R.string.history_title)
        binding.tvSectionResults.text = getString(R.string.history_clear)
        binding.tvSectionResults.setOnClickListener {
            confirmClearHistory()
        }
        adapter.submitList(history)
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_clear_confirm_title)
            .setMessage(R.string.history_clear_confirm_msg)
            .setPositiveButton(R.string.history_clear) { dialog, _ ->
                dialog.dismiss()
                viewModel.clearHistory()
                Snackbar.make(binding.root, R.string.snackbar_history_cleared, Snackbar.LENGTH_SHORT).show()
                viewModel.onSearchQueryChanged(binding.etSearch.text.toString())
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateFilterPillsUI(selectedCategory: String) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.on_surface_variant)

        val isAll = selectedCategory == "ALL"
        val isIrregular = selectedCategory == "IRREGULAR"
        val isRegular = selectedCategory == "REGULAR"

        binding.btnFilterAll.isSelected = isAll
        binding.btnFilterIrregular.isSelected = isIrregular
        binding.btnFilterRegular.isSelected = isRegular

        binding.btnFilterAll.setTextColor(if (isAll) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnFilterIrregular.setTextColor(if (isIrregular) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnFilterRegular.setTextColor(if (isRegular) ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)

        binding.btnFilterAll.setBackgroundColor(if (isAll) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnFilterIrregular.setBackgroundColor(if (isIrregular) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnFilterRegular.setBackgroundColor(if (isRegular) primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
    }

    private fun openVerbDetail(verb: Verb) {
        val intent = Intent(requireContext(), VerbDetailActivity::class.java).apply {
            putExtra(VerbDetailActivity.EXTRA_VERB, verb)
        }
        startActivity(intent)
    }

    private fun CharSequence?.isNull_or_empty(): Boolean = this == null || this.isEmpty()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
