package com.example.verbolandia.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.ProgressSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VerbRepository.getInstance(application)

    private val _dailyVerbs = MutableStateFlow<List<Verb>>(emptyList())
    val dailyVerbs: StateFlow<List<Verb>> = _dailyVerbs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Verb>>(emptyList())
    val searchResults: StateFlow<List<Verb>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<Verb>>(emptyList())
    val searchHistory: StateFlow<List<Verb>> = _searchHistory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _filterCategory = MutableStateFlow("ALL") // "ALL", "IRREGULAR", "REGULAR"
    val filterCategory: StateFlow<String> = _filterCategory.asStateFlow()

    private val _categoryCounts = MutableStateFlow(Triple(0, 0, 0))
    val categoryCounts: StateFlow<Triple<Int, Int, Int>> = _categoryCounts.asStateFlow()

    private val _progressSummary = MutableStateFlow(ProgressSummary())
    val progressSummary: StateFlow<ProgressSummary> = _progressSummary.asStateFlow()

    private val _currentDetailVerb = MutableStateFlow<Verb?>(null)
    val currentDetailVerb: StateFlow<Verb?> = _currentDetailVerb.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _categoryCounts.value = repository.getCategoryCounts()
            _dailyVerbs.value = repository.getDailyVerbs(_filterCategory.value)
            _searchHistory.value = repository.getSearchHistory()
            _progressSummary.value = repository.getProgressSummary()

            if (_currentDetailVerb.value == null) {
                _currentDetailVerb.value = repository.getLastOpenedOrFirstDailyVerb()
            }
        }
    }

    fun setFilterCategory(category: String, currentQuery: String) {
        _filterCategory.value = category
        viewModelScope.launch {
            if (_isSearching.value) {
                performSearch(currentQuery)
            } else {
                _dailyVerbs.value = repository.getDailyVerbs(category)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()

        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            viewModelScope.launch {
                _searchHistory.value = repository.getSearchHistory()
                _dailyVerbs.value = repository.getDailyVerbs(_filterCategory.value)
            }
            return
        }

        _isSearching.value = true
        searchJob = viewModelScope.launch {
            delay(250) // 250 ms debounce
            performSearch(cleanQuery)
        }
    }

    private suspend fun performSearch(query: String) {
        val results = repository.searchVerbs(query, _filterCategory.value)
        _searchResults.value = results
    }

    fun onVerbOpened(verb: Verb) {
        viewModelScope.launch {
            repository.recordView(verb.base)
            repository.addSearchHistory(verb.base)
            _searchHistory.value = repository.getSearchHistory()

            // Fetch refreshed verb with updated stats
            val refreshed = repository.getVerbByBase(verb.base) ?: verb
            _currentDetailVerb.value = refreshed
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun toggleFavorite(verb: Verb) {
        viewModelScope.launch {
            repository.toggleFavorite(verb.base)
            val refreshed = repository.getVerbByBase(verb.base) ?: verb
            _currentDetailVerb.value = refreshed

            // Refresh current lists
            if (_isSearching.value) {
                _searchResults.value = _searchResults.value.map { if (it.base == verb.base) refreshed else it }
            } else {
                _dailyVerbs.value = _dailyVerbs.value.map { if (it.base == verb.base) refreshed else it }
            }
        }
    }

    fun recordPracticeAttempt(base: String, isCorrect: Boolean, mode: String) {
        viewModelScope.launch {
            repository.recordPracticeAttempt(base, isCorrect, mode)
            _progressSummary.value = repository.getProgressSummary()

            val refreshed = repository.getVerbByBase(base)
            if (refreshed != null && _currentDetailVerb.value?.base == base) {
                _currentDetailVerb.value = refreshed
            }
        }
    }

    fun resetProgress() {
        viewModelScope.launch {
            repository.resetProgress()
            _progressSummary.value = repository.getProgressSummary()
            loadInitialData()
        }
    }
}
