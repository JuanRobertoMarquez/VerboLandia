package com.example.verbolandia

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.verbolandia.adapter.TriadSummaryAdapter
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityQuickTriadSummaryBinding
import com.example.verbolandia.model.TriadVerbResult
import kotlinx.coroutines.launch

class QuickTriadSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EXP = "extra_exp"
        const val EXTRA_CORRECT = "extra_correct"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_MAX_STREAK = "extra_max_streak"
        const val EXTRA_TIME_SECONDS = "extra_time_seconds"
        const val EXTRA_COMBO_BONUS = "extra_combo_bonus"
        const val EXTRA_RESULTS = "extra_results"
    }

    private lateinit var binding: ActivityQuickTriadSummaryBinding
    private lateinit var repository: VerbRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuickTriadSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        repository = VerbRepository.getInstance(this)

        bindData()
        setupListeners()
        loadMasteryLevel()
    }

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun bindData() {
        val exp = intent.getIntExtra(EXTRA_EXP, 100)
        val correct = intent.getIntExtra(EXTRA_CORRECT, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 10)
        val maxStreak = intent.getIntExtra(EXTRA_MAX_STREAK, 0)
        val timeSec = intent.getIntExtra(EXTRA_TIME_SECONDS, 60)
        val comboBonus = intent.getIntExtra(EXTRA_COMBO_BONUS, 25)

        val results = intent.getSerializableExtra(EXTRA_RESULTS) as? ArrayList<TriadVerbResult> ?: arrayListOf()

        // 1. Trophy & EXP
        binding.tvRoundBadge.text = getString(R.string.triad_round_badge_format, total, total)
        binding.tvSummaryExpGained.text = getString(R.string.triad_exp_gained_format, exp)
        binding.tvSummaryComboBonus.text = getString(R.string.triad_combo_bonus_format, comboBonus)

        if (correct >= 8) {
            binding.tvSummaryHeadline.text = getString(R.string.triad_headline_flawless)
            binding.tvSummarySubheader.text = getString(R.string.triad_sub_flawless)
        } else if (correct >= 5) {
            binding.tvSummaryHeadline.text = getString(R.string.triad_headline_good)
            binding.tvSummarySubheader.text = getString(R.string.triad_sub_good)
        } else {
            binding.tvSummaryHeadline.text = getString(R.string.triad_headline_keep_going)
            binding.tvSummarySubheader.text = getString(R.string.triad_sub_keep_going)
        }

        // 2. Metrics
        val accuracyPct = if (total > 0) (correct * 100) / total else 0
        binding.tvSummaryAccuracyPct.text = "$accuracyPct%"
        binding.tvSummaryAccuracyCount.text = getString(R.string.triad_accuracy_format, correct, total, accuracyPct)

        binding.tvSummaryMaxStreak.text = getString(R.string.triad_streak_format, maxStreak)
        binding.tvSummaryStreakLabel.text = if (maxStreak >= 4) getString(R.string.triad_streak_hot) else getString(R.string.triad_streak_warming)

        val mins = timeSec / 60
        val secs = timeSec % 60
        binding.tvSummaryTime.text = String.format("%02d:%02d", mins, secs)
        val avgSecPerVerb = if (total > 0) timeSec / total else 0
        binding.tvSummarySpeedPace.text = getString(R.string.triad_pace_format, avgSecPerVerb)

        // 3. Verb Radiography List
        binding.tvAnalyzedCountBadge.text = getString(R.string.triad_analyzed_badge_format, results.size)
        binding.rvSummaryVerbs.layoutManager = LinearLayoutManager(this)
        binding.rvSummaryVerbs.adapter = TriadSummaryAdapter(results)
    }

    private fun setupListeners() {
        binding.btnSummaryClose.setOnClickListener {
            finish()
        }

        binding.btnMainMenu.setOnClickListener {
            finish()
        }

        binding.btnRetrySession.setOnClickListener {
            val intent = Intent(this, QuickTriadActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnSummaryShare.setOnClickListener {
            shareSummary()
        }
    }

    private fun shareSummary() {
        val exp = intent.getIntExtra(EXTRA_EXP, 0)
        val correct = intent.getIntExtra(EXTRA_CORRECT, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 10)
        val maxStreak = intent.getIntExtra(EXTRA_MAX_STREAK, 0)
        val accuracyPct = if (total > 0) (correct * 100) / total else 0

        val shareText = getString(R.string.triad_share_text_format, correct, total, accuracyPct, maxStreak, exp)

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, "Compartir resumen"))
    }

    @SuppressLint("SetTextI18n")
    private fun loadMasteryLevel() {
        lifecycleScope.launch {
            val summary = repository.getProgressSummary()
            val masteredVerbs = summary.masteredVerbsCount

            // Calculate level (1 level per 10 mastered verbs or exp scale)
            val userLevel = (masteredVerbs / 10) + 1
            val currentLevelExp = (masteredVerbs % 10) * 80 + 120
            val maxLevelExp = 1000

            binding.tvMasteryBadge.text = getString(R.string.triad_mastery_level_format, userLevel)
            binding.tvMasteryExpFraction.text = getString(R.string.triad_mastery_fraction_format, currentLevelExp, maxLevelExp)
            binding.progressMastery.progress = ((currentLevelExp.toFloat() / maxLevelExp) * 100).toInt()

            val remaining = (maxLevelExp - currentLevelExp).coerceAtLeast(0)
            binding.tvMasteryNextLevelTip.text = getString(R.string.triad_mastery_next_level_format, remaining, userLevel + 1)
        }
    }
}
