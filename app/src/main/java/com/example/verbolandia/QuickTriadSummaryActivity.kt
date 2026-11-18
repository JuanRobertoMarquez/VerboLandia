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
        binding.tvRoundBadge.text = "⚡ Ronda $total/$total"
        binding.tvSummaryExpGained.text = "+$exp EXP"
        binding.tvSummaryComboBonus.text = "↗ +$comboBonus por combo"

        if (correct >= 8) {
            binding.tvSummaryHeadline.text = "¡Tríada Impecable!"
            binding.tvSummarySubheader.text = "¡Excelente dominio! Tienes una velocidad de conjugación asombrosa."
        } else if (correct >= 5) {
            binding.tvSummaryHeadline.text = "¡Buen Esfuerzo!"
            binding.tvSummarySubheader.text = "Cada repetición consolida los patrones irregulares en tu memoria verbal."
        } else {
            binding.tvSummaryHeadline.text = "¡Sesión Completada!"
            binding.tvSummarySubheader.text = "La práctica constante es la clave. Sigue repasando estos verbos."
        }

        // 2. Metrics
        val accuracyPct = if (total > 0) (correct * 100) / total else 0
        binding.tvSummaryAccuracyPct.text = "$accuracyPct%"
        binding.tvSummaryAccuracyCount.text = "$correct de $total aciertos"

        binding.tvSummaryMaxStreak.text = "🔥 x$maxStreak"
        binding.tvSummaryStreakLabel.text = if (maxStreak >= 5) "¡Imparable!" else if (maxStreak >= 2) "Buen ritmo" else "En calentamiento..."

        val mins = timeSec / 60
        val secs = timeSec % 60
        binding.tvSummaryTime.text = String.format("⏱ %02d:%02d", mins, secs)
        val avgSecPerVerb = if (total > 0) timeSec / total else 0
        binding.tvSummarySpeedPace.text = "~${avgSecPerVerb}s por verbo"

        // 3. Verb Radiography List
        binding.tvAnalyzedCountBadge.text = "${results.size} Analizados"
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

        val shareText = """
            ⚡ ¡Completé mi sesión de Tríada Rápida en VerboLandia!
            🎯 Aciertos: $correct de $total (${if (total > 0) (correct * 100) / total else 0}%)
            🔥 Racha máxima: x$maxStreak
            ⭐ EXP Ganada: +$exp EXP
            
            ¡Aprende y domina los verbos en inglés con VerboLandia! 🚀
        """.trimIndent()

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

            binding.tvMasteryBadge.text = "Nivel $userLevel de Maestría"
            binding.tvMasteryExpFraction.text = "$currentLevelExp / $maxLevelExp EXP"
            binding.progressMastery.progress = ((currentLevelExp.toFloat() / maxLevelExp) * 100).toInt()

            val remaining = (maxLevelExp - currentLevelExp).coerceAtLeast(0)
            binding.tvMasteryNextLevelTip.text = "Aprendiz Constante · Faltan $remaining EXP para Nivel ${userLevel + 1}"
        }
    }
}
