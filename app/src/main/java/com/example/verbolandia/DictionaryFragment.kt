package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.adapter.VerbAdapter
import com.example.verbolandia.databinding.FragmentDictionaryBinding
import com.example.verbolandia.databinding.LayoutDialogFiltersBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.example.verbolandia.ui.RightAction
import com.example.verbolandia.ui.SharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

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

        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Logo,
            eyebrow = getString(R.string.app_name),
            title = getString(R.string.nav_dictionary),
            rightActions = listOf(RightAction.Avatar())
        )

        setupRecyclerView()
        setupSearchAndFilterButton()
        setupPaginationControls()
        setupSwipeGesture()
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

    private fun setupSearchAndFilterButton() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                showSkeletonLoading()
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

        binding.btnOpenFilter.setOnClickListener {
            showFilterBottomSheet()
        }

        binding.btnClearFilterPill.setOnClickListener {
            showSkeletonLoading()
            viewModel.resetFilters()
        }
    }

    private fun setupPaginationControls() {
        binding.btnPrevPage.setOnClickListener {
            showSkeletonLoading()
            viewModel.prevPage()
            scrollToTopList()
        }

        binding.btnNextPage.setOnClickListener {
            showSkeletonLoading()
            viewModel.nextPage()
            scrollToTopList()
        }
    }

    private fun setupSwipeGesture() {
        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX < 0) {
                        // Swipe left -> Next Page
                        if (viewModel.currentPage.value < viewModel.totalPages.value) {
                            showSkeletonLoading()
                            viewModel.nextPage()
                            scrollToTopList()
                            return true
                        }
                    } else {
                        // Swipe right -> Previous Page
                        if (viewModel.currentPage.value > 1) {
                            showSkeletonLoading()
                            viewModel.prevPage()
                            scrollToTopList()
                            return true
                        }
                    }
                }
                return false
            }
        })

        binding.rvVerbs.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun showSkeletonLoading() {
        binding.layoutSkeleton.visibility = View.VISIBLE
        binding.rvVerbs.visibility = View.INVISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            delay(220)
            binding.layoutSkeleton.visibility = View.GONE
            binding.rvVerbs.visibility = View.VISIBLE
            binding.rvVerbs.alpha = 0f
            binding.rvVerbs.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun scrollToTopList() {
        binding.scrollView.post {
            val targetY = binding.tvSectionHeader.top
            binding.scrollView.smoothScrollTo(0, targetY)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Paginated verbs
                launch {
                    viewModel.paginatedVerbs.collect { verbs ->
                        adapter.submitList(verbs)
                    }
                }

                // Pagination state & dynamic number pills
                launch {
                    viewModel.currentPage.collect { page ->
                        val totalPages = viewModel.totalPages.value
                        val totalVerbs = viewModel.totalMatchingCount.value

                        binding.btnPrevPage.isEnabled = page > 1
                        binding.btnNextPage.isEnabled = page < totalPages

                        binding.tvSectionResults.text = getString(R.string.page_indicator, page, totalPages)
                        binding.tvPaginationSummary.text = getString(R.string.pagination_summary, page, totalPages, totalVerbs)

                        renderPageNumbers(page, totalPages)
                    }
                }

                launch {
                    viewModel.totalPages.collect { totalPages ->
                        val page = viewModel.currentPage.value
                        val totalVerbs = viewModel.totalMatchingCount.value

                        binding.btnPrevPage.isEnabled = page > 1
                        binding.btnNextPage.isEnabled = page < totalPages

                        binding.tvSectionResults.text = getString(R.string.page_indicator, page, totalPages)
                        binding.tvPaginationSummary.text = getString(R.string.pagination_summary, page, totalPages, totalVerbs)

                        renderPageNumbers(page, totalPages)
                    }
                }

                // Active filter dot & tag pill
                launch {
                    viewModel.isFilterActive.collect { isActive ->
                        binding.viewFilterActiveDot.visibility = if (isActive) View.VISIBLE else View.GONE
                        binding.layoutActiveFilterTag.visibility = if (isActive) View.VISIBLE else View.GONE

                        if (isActive) {
                            val cat = when (viewModel.filterCategory.value) {
                                "IRREGULAR" -> getString(R.string.filter_irregular)
                                "REGULAR" -> getString(R.string.filter_regular)
                                else -> getString(R.string.filter_all)
                            }
                            val lvl = if (viewModel.filterLevel.value == "ALL") "" else " • Nivel ${viewModel.filterLevel.value}"
                            binding.tvActiveFilterDescription.text = getString(R.string.active_filter_desc, cat, lvl)
                        }
                    }
                }

                // Search state header title
                launch {
                    viewModel.isSearching.collect { isSearching ->
                        if (isSearching) {
                            binding.tvSectionHeader.text = getString(R.string.search_results_title)
                        } else {
                            binding.tvSectionHeader.text = getString(R.string.list_header_title)
                        }
                    }
                }

                // Verb of the Day
                launch {
                    viewModel.currentDetailVerb.collect { verbOfToday ->
                        if (verbOfToday != null) {
                            binding.tvVerbOfDayTitle.text = getString(R.string.verbo_del_dia_title_dynamic, verbOfToday.base, verbOfToday.spanishMeaning)
                            binding.tvVerbOfDayV1.text = getString(R.string.verbo_del_dia_v1_dynamic, verbOfToday.base)
                            binding.tvVerbOfDayV2.text = getString(R.string.verbo_del_dia_v2_dynamic, verbOfToday.past)
                            binding.tvVerbOfDayV3.text = getString(R.string.verbo_del_dia_v3_dynamic, verbOfToday.pastParticiple)

                            binding.cardVerbOfDay.setOnClickListener {
                                viewModel.onVerbOpened(verbOfToday)
                                openVerbDetail(verbOfToday)
                            }

                            binding.btnVerbOfDayAudio.setOnClickListener {
                                com.example.verbolandia.util.AudioSpeaker.speak(requireContext(), verbOfToday.base)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderPageNumbers(currentPage: Int, totalPages: Int) {
        val container = binding.layoutPageNumbersContainer
        container.removeAllViews()

        if (totalPages <= 1) return

        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
        val mutedText = ContextCompat.getColor(requireContext(), R.color.on_surface_variant)

        for (i in 1..totalPages) {
            val pageButton = TextView(requireContext()).apply {
                text = "$i"
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                val isCurrent = i == currentPage
                setTextColor(if (isCurrent) whiteColor else mutedText)
                setBackgroundResource(if (isCurrent) R.drawable.bg_page_number_active else R.drawable.bg_page_number_normal)
                setPadding(32, 14, 32, 14)
                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                layoutParams = params

                setOnClickListener {
                    if (i != viewModel.currentPage.value) {
                        showSkeletonLoading()
                        viewModel.setPage(i)
                        scrollToTopList()
                    }
                }
            }
            container.addView(pageButton)
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = LayoutDialogFiltersBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        when (viewModel.filterCategory.value) {
            "IRREGULAR" -> dialogBinding.chipCatIrregular.isChecked = true
            "REGULAR" -> dialogBinding.chipCatRegular.isChecked = true
            else -> dialogBinding.chipCatAll.isChecked = true
        }

        when (viewModel.filterLevel.value) {
            "A1" -> dialogBinding.chipLvlA1.isChecked = true
            "A2" -> dialogBinding.chipLvlA2.isChecked = true
            "B1" -> dialogBinding.chipLvlB1.isChecked = true
            "B2" -> dialogBinding.chipLvlB2.isChecked = true
            else -> dialogBinding.chipLvlAll.isChecked = true
        }

        dialogBinding.btnApplyFilters.setOnClickListener {
            val selectedCat = when (dialogBinding.chipGroupCategory.checkedChipId) {
                R.id.chipCatIrregular -> "IRREGULAR"
                R.id.chipCatRegular -> "REGULAR"
                else -> "ALL"
            }

            val selectedLvl = when (dialogBinding.chipGroupLevel.checkedChipId) {
                R.id.chipLvlA1 -> "A1"
                R.id.chipLvlA2 -> "A2"
                R.id.chipLvlB1 -> "B1"
                R.id.chipLvlB2 -> "B2"
                else -> "ALL"
            }

            showSkeletonLoading()
            viewModel.applyFilters(selectedCat, selectedLvl)
            dialog.dismiss()
            scrollToTopList()
        }

        dialogBinding.btnResetFilters.setOnClickListener {
            showSkeletonLoading()
            viewModel.resetFilters()
            dialog.dismiss()
            scrollToTopList()
        }

        dialog.show()
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
