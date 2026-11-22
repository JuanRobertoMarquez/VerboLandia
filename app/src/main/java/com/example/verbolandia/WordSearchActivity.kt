package com.example.verbolandia

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityWordSearchBinding
import com.example.verbolandia.util.AudioSpeaker
import com.example.verbolandia.wordsearch.WordSearchBoard
import com.example.verbolandia.wordsearch.WordSearchCandidate
import com.example.verbolandia.wordsearch.WordSearchCanvasView
import com.example.verbolandia.wordsearch.WordSearchGenerator
import com.example.verbolandia.wordsearch.WordSearchTargetWord
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordSearchActivity : AppCompatActivity(), WordSearchCanvasView.OnWordSearchInteractionListener {

    companion object {
        const val EXTRA_LEVEL = "extra_level"
    }

    private lateinit var binding: ActivityWordSearchBinding
    private lateinit var repository: VerbRepository

    private var currentLevel: String = "ALL"
    private var board: WordSearchBoard? = null
    private var currentTenseTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWordSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = VerbRepository.getInstance(applicationContext)
        currentLevel = intent.getStringExtra(EXTRA_LEVEL) ?: "ALL"

        setupUI()
        loadNewWordSearch()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.wordSearchGrid.listener = this

        binding.btnSoundHint.setOnClickListener {
            val pending = board?.targetWords?.find { !it.isFound }
            if (pending != null) {
                AudioSpeaker.speak(this, pending.targetWord)
                Snackbar.make(binding.root, getString(R.string.crossword_chip_format, 1, "", pending.targetWord), Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.btnPhoneticHint.setOnClickListener {
            val pending = board?.targetWords?.find { !it.isFound }
            if (pending != null) {
                binding.wordSearchGrid.showHintForWord(pending)
                Snackbar.make(
                    binding.root,
                    getString(R.string.wordsearch_hint_revealed, pending.base.uppercase(), pending.startRow + 1),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnRestart.setOnClickListener {
            loadNewWordSearch()
        }
    }

    private fun loadNewWordSearch() {
        lifecycleScope.launch {
            val isPast = kotlin.random.Random.nextBoolean()
            currentTenseTitle = if (isPast) {
                getString(R.string.wordsearch_tense_past_simple)
            } else {
                getString(R.string.wordsearch_tense_past_participle)
            }
            binding.tvTargetTenseSubtitle.text = getString(R.string.wordsearch_target_tense_format, currentTenseTitle)

            val candidates = withContext(Dispatchers.IO) {
                fetchCandidates(currentLevel, isPast)
            }

            if (candidates.isEmpty()) {
                Snackbar.make(binding.root, R.string.snackbar_no_weak_verbs, Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val newBoard = withContext(Dispatchers.Default) {
                WordSearchGenerator.generate(
                    candidates = candidates,
                    targetCount = 4,
                    targetTenseTitle = currentTenseTitle,
                    gridSize = 8
                )
            }

            board = newBoard
            binding.wordSearchGrid.setBoard(newBoard)
            setupChips(newBoard)
            updateCounterUI()

            // Reset live tracing text and grammar pill
            binding.tvLiveTracing.text = getString(R.string.wordsearch_tracing_idle)
            if (newBoard.targetWords.isNotEmpty()) {
                val firstWord = newBoard.targetWords.first()
                updateGrammarPill(firstWord)
            }
        }
    }

    private suspend fun fetchCandidates(level: String, isPast: Boolean): List<WordSearchCandidate> {
        val allVerbs = repository.getCatalogVerbs(category = "ALL", level = level)
        val selected = allVerbs.filter { it.isIrregular || it.isCommon }.shuffled().take(15)

        val list = mutableListOf<WordSearchCandidate>()
        for (v in selected) {
            val word = if (isPast) v.past.uppercase() else v.pastParticiple.uppercase()
            if (word.length in 3..6 && word.all { it.isLetter() }) {
                val phon = if (isPast) {
                    if (v.ipaBase.isNotEmpty() && v.ipaPast.isNotEmpty()) getString(R.string.phonetic_transition_format, v.ipaBase, v.ipaPast) else ""
                } else {
                    if (v.ipaBase.isNotEmpty() && v.ipaParticiple.isNotEmpty()) getString(R.string.phonetic_transition_format, v.ipaBase, v.ipaParticiple) else ""
                }
                val tenseLabel = if (isPast) getString(R.string.wordsearch_tense_past_simple) else getString(R.string.wordsearch_tense_past_participle)

                list.add(
                    WordSearchCandidate(
                        verbId = v.id,
                        base = v.base,
                        targetWord = word,
                        tenseLabel = tenseLabel,
                        spanishMeaning = v.spanishMeaning,
                        phoneticTip = phon,
                        commonErrorTip = v.commonErrorTip
                    )
                )
            }
        }
        return list
    }

    private fun setupChips(board: WordSearchBoard) {
        binding.layoutTargetChips.removeAllViews()

        for (target in board.targetWords) {
            val chip = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (36 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(0, 0, (6 * resources.displayMetrics.density).toInt(), 0)
                }
                layoutParams = params
                insetTop = 0
                insetBottom = 0
                setPadding((10 * resources.displayMetrics.density).toInt(), 0, (10 * resources.displayMetrics.density).toInt(), 0)
                textSize = 11f
                text = getString(R.string.wordsearch_word_item_format, target.base.uppercase(), target.targetWord)
                cornerRadius = (18 * resources.displayMetrics.density).toInt()

                setOnClickListener {
                    AudioSpeaker.speak(this@WordSearchActivity, target.targetWord)
                    updateGrammarPill(target)
                }
            }
            binding.layoutTargetChips.addView(chip)
        }
        updateChipsUI()
    }

    private fun updateChipsUI() {
        val b = board ?: return
        val successColor = ContextCompat.getColor(this, R.color.success)
        val surfaceColor = ContextCompat.getColor(this, R.color.surface_container_low)
        val outlineColor = ContextCompat.getColor(this, R.color.outline_variant)
        val onSurfaceColor = ContextCompat.getColor(this, R.color.on_surface)
        val whiteColor = ContextCompat.getColor(this, R.color.white)

        for (i in 0 until binding.layoutTargetChips.childCount) {
            val chip = binding.layoutTargetChips.getChildAt(i) as? MaterialButton ?: continue
            val word = b.targetWords.getOrNull(i) ?: continue

            if (word.isFound) {
                chip.backgroundTintList = ColorStateList.valueOf(successColor)
                chip.setTextColor(whiteColor)
                chip.strokeColor = ColorStateList.valueOf(successColor)
                chip.setIconResource(R.drawable.ic_check)
                chip.iconTint = ColorStateList.valueOf(whiteColor)
            } else {
                chip.backgroundTintList = ColorStateList.valueOf(surfaceColor)
                chip.setTextColor(onSurfaceColor)
                chip.strokeColor = ColorStateList.valueOf(outlineColor)
                chip.icon = null
            }
        }
    }

    private fun updateCounterUI() {
        val b = board ?: return
        val found = b.targetWords.count { it.isFound }
        binding.tvFoundCounter.text = getString(R.string.goal_status_format, found, b.targetWords.size)
    }

    private fun updateGrammarPill(target: WordSearchTargetWord) {
        binding.tvPillTense.text = target.tenseLabel
        val phonStr = if (target.phoneticTip.isNotEmpty()) " (${target.phoneticTip})" else ""
        binding.tvPillVerb.text = getString(
            R.string.wordsearch_pill_verb_format,
            target.base.uppercase(),
            target.targetWord,
            target.spanishMeaning,
            phonStr
        )
        binding.tvPillExplanation.text = if (target.commonErrorTip.isNotEmpty()) {
            target.commonErrorTip
        } else {
            getString(R.string.wordsearch_pill_default_explanation)
        }
    }

    // WordSearchCanvasView.OnWordSearchInteractionListener
    override fun onWordTracing(tracedWord: String) {
        val formatted = tracedWord.toList().joinToString(" - ")
        binding.tvLiveTracing.text = getString(R.string.wordsearch_tracing_format, formatted)
    }

    override fun onWordFound(targetWord: WordSearchTargetWord) {
        AudioSpeaker.speak(this, targetWord.targetWord)

        lifecycleScope.launch(Dispatchers.IO) {
            repository.recordPracticeAttempt(targetWord.base, true, "Sopa de Tiempos")
        }

        Snackbar.make(binding.root, getString(R.string.wordsearch_found_toast, targetWord.targetWord), Snackbar.LENGTH_SHORT).show()
        updateChipsUI()
        updateCounterUI()
        updateGrammarPill(targetWord)
        binding.tvLiveTracing.text = getString(R.string.wordsearch_tracing_idle)

        val b = board ?: return
        if (b.isAllFound()) {
            showVictoryDialog()
        }
    }

    override fun onTracingCancelled() {
        binding.tvLiveTracing.text = getString(R.string.wordsearch_tracing_idle)
    }

    private fun showVictoryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.wordsearch_victory_title)
            .setMessage(getString(R.string.wordsearch_victory_desc) + "\n" + getString(R.string.wordsearch_score_earned))
            .setPositiveButton(R.string.wordsearch_btn_next) { dialog, _ ->
                dialog.dismiss()
                loadNewWordSearch()
            }
            .setNegativeButton(R.string.wordsearch_btn_finish) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
