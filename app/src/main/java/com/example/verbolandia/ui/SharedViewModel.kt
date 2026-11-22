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
import kotlin.math.ceil

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val PAGE_SIZE = 15
    }

    private val repository = VerbRepository.getInstance(application)

    private val _dailyVerbs = MutableStateFlow<List<Verb>>(emptyList())
    val dailyVerbs: StateFlow<List<Verb>> = _dailyVerbs.asStateFlow()

    private var allMatchingVerbs: List<Verb> = emptyList()

    private val _paginatedVerbs = MutableStateFlow<List<Verb>>(emptyList())
    val paginatedVerbs: StateFlow<List<Verb>> = _paginatedVerbs.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _totalMatchingCount = MutableStateFlow(0)
    val totalMatchingCount: StateFlow<Int> = _totalMatchingCount.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Verb>>(emptyList())
    val searchResults: StateFlow<List<Verb>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<Verb>>(emptyList())
    val searchHistory: StateFlow<List<Verb>> = _searchHistory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _filterCategory = MutableStateFlow("ALL") // "ALL", "IRREGULAR", "REGULAR"
    val filterCategory: StateFlow<String> = _filterCategory.asStateFlow()

    private val _filterLevel = MutableStateFlow("ALL") // "ALL", "A1", "A2", "B1", "B2"
    val filterLevel: StateFlow<String> = _filterLevel.asStateFlow()

    private val _isFilterActive = MutableStateFlow(false)
    val isFilterActive: StateFlow<Boolean> = _isFilterActive.asStateFlow()

    private val _categoryCounts = MutableStateFlow(Triple(0, 0, 0))
    val categoryCounts: StateFlow<Triple<Int, Int, Int>> = _categoryCounts.asStateFlow()

    private val _progressSummary = MutableStateFlow(ProgressSummary())
    val progressSummary: StateFlow<ProgressSummary> = _progressSummary.asStateFlow()

    private val _verbOfTheDay = MutableStateFlow<Verb?>(null)
    val verbOfTheDay: StateFlow<Verb?> = _verbOfTheDay.asStateFlow()

    private val _currentDetailVerb = MutableStateFlow<Verb?>(null)
    val currentDetailVerb: StateFlow<Verb?> = _currentDetailVerb.asStateFlow()

    private var searchJob: Job? = null
    private var lastQuery: String = ""

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _categoryCounts.value = repository.getCategoryCounts()
            _dailyVerbs.value = repository.getDailyVerbs(_filterCategory.value)
            _searchHistory.value = repository.getSearchHistory()
            _progressSummary.value = repository.getProgressSummary()

            refreshVerbs(lastQuery)

            _verbOfTheDay.value = repository.getDailyVerbOfTheDay()

            if (_currentDetailVerb.value == null) {
                _currentDetailVerb.value = _verbOfTheDay.value
            }
        }
    }

    fun applyFilters(category: String, level: String) {
        _filterCategory.value = category
        _filterLevel.value = level
        _isFilterActive.value = (category != "ALL" || level != "ALL")
        _currentPage.value = 1
        refreshVerbs(lastQuery)
    }

    fun resetFilters() {
        applyFilters("ALL", "ALL")
    }

    fun setPage(page: Int) {
        val total = _totalPages.value
        val validPage = page.coerceIn(1, maxOf(1, total))
        if (_currentPage.value != validPage) {
            _currentPage.value = validPage
            updatePaginationSlice()
        }
    }

    fun nextPage() {
        if (_currentPage.value < _totalPages.value) {
            setPage(_currentPage.value + 1)
        }
    }

    fun prevPage() {
        if (_currentPage.value > 1) {
            setPage(_currentPage.value - 1)
        }
    }

    private fun refreshVerbs(query: String) {
        lastQuery = query
        viewModelScope.launch {
            if (_isSearching.value && query.isNotBlank()) {
                val results = repository.searchVerbs(query, _filterCategory.value, _filterLevel.value)
                allMatchingVerbs = results
                _searchResults.value = results
            } else {
                val catalog = repository.getCatalogVerbs(_filterCategory.value, _filterLevel.value)
                allMatchingVerbs = catalog
            }

            _totalMatchingCount.value = allMatchingVerbs.size
            val calcTotalPages = ceil(allMatchingVerbs.size.toDouble() / PAGE_SIZE).toInt()
            _totalPages.value = maxOf(1, calcTotalPages)

            if (_currentPage.value > _totalPages.value) {
                _currentPage.value = 1
            }

            updatePaginationSlice()
        }
    }

    private fun updatePaginationSlice() {
        val page = _currentPage.value
        val startIndex = (page - 1) * PAGE_SIZE
        if (startIndex >= allMatchingVerbs.size) {
            _paginatedVerbs.value = allMatchingVerbs.take(PAGE_SIZE)
        } else {
            val endIndex = minOf(startIndex + PAGE_SIZE, allMatchingVerbs.size)
            _paginatedVerbs.value = allMatchingVerbs.subList(startIndex, endIndex)
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchJob?.cancel()
        lastQuery = query
        val cleanQuery = query.trim()

        if (cleanQuery.isEmpty()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            _currentPage.value = 1
            viewModelScope.launch {
                _searchHistory.value = repository.getSearchHistory()
                refreshVerbs("")
            }
            return
        }

        _isSearching.value = true
        _currentPage.value = 1
        searchJob = viewModelScope.launch {
            delay(250) // 250 ms debounce
            refreshVerbs(cleanQuery)
        }
    }

    fun onVerbOpened(verb: Verb) {
        viewModelScope.launch {
            repository.recordView(verb.base)
            val refreshed = repository.getVerbByBase(verb.base) ?: verb
            _currentDetailVerb.value = refreshed

            // Refresh list in place
            allMatchingVerbs = allMatchingVerbs.map { if (it.base == verb.base) refreshed else it }
            updatePaginationSlice()
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

            allMatchingVerbs = allMatchingVerbs.map { if (it.base == verb.base) refreshed else it }
            updatePaginationSlice()
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

            if (refreshed != null) {
                allMatchingVerbs = allMatchingVerbs.map { if (it.base == base) refreshed else it }
                updatePaginationSlice()
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

    suspend fun getAttemptsForDate(year: Int, month: Int, dayOfMonth: Int): List<com.example.verbolandia.util.AttemptRecord> {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month)
            set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        cal.set(java.util.Calendar.MILLISECOND, 999)
        val endMs = cal.timeInMillis
        return repository.getAttemptsForDateRange(startMs, endMs)
    }

    suspend fun getVerb(base: String): Verb? {
        return allMatchingVerbs.find { it.base.equals(base, ignoreCase = true) }
            ?: repository.getVerbByBase(base)
    }
}
