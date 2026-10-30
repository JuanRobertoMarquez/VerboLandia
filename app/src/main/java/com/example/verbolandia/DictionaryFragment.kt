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
import com.example.verbolandia.adapter.VerbAdapter
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.FragmentDictionaryBinding
import com.example.verbolandia.model.Verb
import com.google.android.material.snackbar.Snackbar

class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VerbRepository
    private lateinit var adapter: VerbAdapter

    private var currentFilterMode: String = "ALL" // "ALL", "IRREGULAR", "REGULAR"

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

        repository = VerbRepository(requireContext())

        setupVerbOfDay()
        setupRecyclerView()
        setupSearchAndFilters()
        loadVerbs()
    }

    private fun setupVerbOfDay() {
        val verbOfDay = repository.getVerbOfDay()
        binding.tvVerbOfDayTitle.text = "${verbOfDay.base} • ${verbOfDay.spanishMeaning}"
        binding.tvVerbOfDayV1.text = "Base: ${verbOfDay.base}"
        binding.tvVerbOfDayV2.text = "Pasado: ${verbOfDay.past}"
        binding.tvVerbOfDayV3.text = "Part.: ${verbOfDay.pastParticiple}"

        binding.cardVerbOfDay.setOnClickListener {
            openVerbDetail(verbOfDay)
        }

        binding.btnVerbOfDayAudio.setOnClickListener {
            Snackbar.make(binding.root, "Audio de ${verbOfDay.base} reproduciéndose", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = VerbAdapter(emptyList()) { verb ->
            openVerbDetail(verb)
        }
        binding.rvVerbs.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearchAndFilter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilterAll.setOnClickListener {
            currentFilterMode = "ALL"
            updateFilterPillsUI()
            performSearchAndFilter(binding.etSearch.text.toString())
        }

        binding.btnFilterIrregular.setOnClickListener {
            currentFilterMode = "IRREGULAR"
            updateFilterPillsUI()
            performSearchAndFilter(binding.etSearch.text.toString())
        }

        binding.btnFilterRegular.setOnClickListener {
            currentFilterMode = "REGULAR"
            updateFilterPillsUI()
            performSearchAndFilter(binding.etSearch.text.toString())
        }
    }

    private fun loadVerbs() {
        val totalCount = repository.getTotalCount()
        binding.tvActiveVerbsBadge.text = "$totalCount Activos"
        performSearchAndFilter("")
    }

    private fun performSearchAndFilter(query: String) {
        val searchResults = repository.searchVerbs(query)

        val filteredList = when (currentFilterMode) {
            "IRREGULAR" -> searchResults.filter { it.isIrregular }
            "REGULAR" -> searchResults.filter { !it.isIrregular }
            else -> searchResults
        }

        adapter.updateData(filteredList)
        binding.tvSectionResults.text = "${filteredList.size} verbos visibles"

        // Update counts on filter pills dynamically
        val allCount = searchResults.size
        val irregularCount = searchResults.count { it.isIrregular }
        val regularCount = searchResults.count { !it.isIrregular }

        binding.btnFilterAll.text = "Todos ($allCount)"
        binding.btnFilterIrregular.text = "Irregulares ($irregularCount)"
        binding.btnFilterRegular.text = "Regulares ($regularCount)"
    }

    private fun updateFilterPillsUI() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.on_surface_variant)

        // Reset all pills
        binding.btnFilterAll.setTextColor(if (currentFilterMode == "ALL") ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnFilterIrregular.setTextColor(if (currentFilterMode == "IRREGULAR") ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)
        binding.btnFilterRegular.setTextColor(if (currentFilterMode == "REGULAR") ContextCompat.getColor(requireContext(), R.color.white) else mutedColor)

        binding.btnFilterAll.setBackgroundColor(if (currentFilterMode == "ALL") primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnFilterIrregular.setBackgroundColor(if (currentFilterMode == "IRREGULAR") primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
        binding.btnFilterRegular.setBackgroundColor(if (currentFilterMode == "REGULAR") primaryColor else ContextCompat.getColor(requireContext(), R.color.surface_container_low))
    }

    private fun openVerbDetail(verb: Verb) {
        val intent = Intent(requireContext(), VerbDetailActivity::class.java).apply {
            putExtra(VerbDetailActivity.EXTRA_VERB, verb)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
