package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var allVerbs: List<Verb> = emptyList()
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
        binding.tvVerbOfDayTitle.text = "${verbOfDay.base} (${verbOfDay.spanishMeaning})"

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
                applyFiltersAndSearch(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilterAll.setOnClickListener {
            currentFilterMode = "ALL"
            applyFiltersAndSearch(binding.etSearch.text.toString())
            Snackbar.make(binding.root, "Mostrando todos los verbos", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnFilterIrregular.setOnClickListener {
            currentFilterMode = "IRREGULAR"
            applyFiltersAndSearch(binding.etSearch.text.toString())
            Snackbar.make(binding.root, "Filtrado por verbos Irregulares", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnFilterRegular.setOnClickListener {
            currentFilterMode = "REGULAR"
            applyFiltersAndSearch(binding.etSearch.text.toString())
            Snackbar.make(binding.root, "Filtrado por verbos Regulares", Snackbar.LENGTH_SHORT).show()
        }

        // Hide static sample card
        binding.sampleVerbCardBreak.root.visibility = View.GONE
    }

    private fun loadVerbs() {
        allVerbs = repository.getVerbs()
        applyFiltersAndSearch("")
    }

    private fun applyFiltersAndSearch(query: String) {
        val filteredList = allVerbs.filter { verb ->
            val matchesQuery = query.isEmpty() ||
                    verb.base.contains(query, ignoreCase = true) ||
                    verb.past.contains(query, ignoreCase = true) ||
                    verb.spanishMeaning.contains(query, ignoreCase = true)

            val matchesFilter = when (currentFilterMode) {
                "IRREGULAR" -> verb.isIrregular
                "REGULAR" -> !verb.isIrregular
                else -> true
            }

            matchesQuery && matchesFilter
        }

        adapter.updateData(filteredList)
        binding.tvSectionResults.text = "${filteredList.size} verbos"
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
