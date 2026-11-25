package com.example.verbolandia

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityGrammarMemoryBinding
import com.example.verbolandia.memory.MemoryCardAdapter
import com.example.verbolandia.memory.MemoryFlipResult
import com.example.verbolandia.memory.MemoryGameEngine
import com.example.verbolandia.util.AudioSpeaker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrammarMemoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LEVEL = "extra_level"
    }

    private lateinit var binding: ActivityGrammarMemoryBinding
    private lateinit var repository: VerbRepository
    private lateinit var adapter: MemoryCardAdapter

    private var currentLevel: String = "ALL"
    private var gameEngine: MemoryGameEngine? = null
    private var isProcessingFlip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGrammarMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = VerbRepository.getInstance(applicationContext)
        currentLevel = intent.getStringExtra(EXTRA_LEVEL) ?: "ALL"

        setupUI()
        loadNewMemoryGame()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvMemorySubtitle.text = getString(R.string.memory_subtitle_format, currentLevel)

        binding.rvCards.layoutManager = GridLayoutManager(this, 3)
        adapter = MemoryCardAdapter(emptyList()) { position ->
            handleCardClick(position)
        }
        binding.rvCards.adapter = adapter

        binding.btnAudioHint.setOnClickListener {
            val engine = gameEngine ?: return@setOnClickListener
            val targetVerb = engine.getUnmatchedPairVerb()
            if (targetVerb != null) {
                val wordToSpeak = if (engine.isPastTense) targetVerb.past else targetVerb.pastParticiple
                AudioSpeaker.speak(this, wordToSpeak, it)
                Snackbar.make(
                    binding.root,
                    getString(R.string.memory_match_success_format, "${targetVerb.base.uppercase()} ($wordToSpeak)"),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        binding.btnShuffle.setOnClickListener {
            val engine = gameEngine ?: return@setOnClickListener
            engine.shuffleUnmatchedCards()
            adapter.notifyDataSetChanged()
            Snackbar.make(binding.root, R.string.memory_btn_shuffle, Snackbar.LENGTH_SHORT).show()
        }

        binding.btnRestart.setOnClickListener {
            loadNewMemoryGame()
        }
    }

    private fun loadNewMemoryGame() {
        lifecycleScope.launch {
            val isPast = kotlin.random.Random.nextBoolean()
            val tenseTitle = if (isPast) {
                getString(R.string.wordsearch_tense_past_simple)
            } else {
                getString(R.string.wordsearch_tense_past_participle)
            }
            binding.tvTargetTensePill.text = getString(R.string.memory_target_tense_format, tenseTitle)

            val verbs = withContext(Dispatchers.IO) {
                val all = repository.getCatalogVerbs(category = "ALL", level = currentLevel)
                all.filter { it.isIrregular || it.isCommon }.shuffled()
            }

            if (verbs.size < 6) {
                Snackbar.make(binding.root, R.string.snackbar_no_weak_verbs, Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val engine = MemoryGameEngine(
                verbs = verbs,
                targetTenseLabel = tenseTitle,
                isPastTense = isPast
            )
            gameEngine = engine
            adapter.updateCards(engine.cards)
            updateBadgesUI()
        }
    }

    private fun handleCardClick(position: Int) {
        if (isProcessingFlip) return
        val engine = gameEngine ?: return

        when (val result = engine.onCardSelected(position)) {
            is MemoryFlipResult.Ignored -> {
                // Do nothing
            }
            is MemoryFlipResult.FirstCardFlipped -> {
                adapter.notifyItemChanged(position)
            }
            is MemoryFlipResult.MatchFound -> {
                adapter.notifyItemChanged(position)
                AudioSpeaker.speak(this, result.card2.displayText)

                lifecycleScope.launch(Dispatchers.IO) {
                    repository.recordPracticeAttempt(result.card1.verbBase, true, "Memorama Gramatical")
                }

                updateBadgesUI()
                Snackbar.make(
                    binding.root,
                    getString(R.string.memory_match_success_format, "${result.card1.verbBase.uppercase()} · ${result.card2.displayText}"),
                    Snackbar.LENGTH_SHORT
                ).show()

                if (engine.isGameFinished) {
                    showVictoryDialog()
                }
            }
            is MemoryFlipResult.NoMatch -> {
                isProcessingFlip = true
                adapter.notifyItemChanged(position)
                updateBadgesUI()

                // Turn both cards face down after 800ms
                val idx1 = engine.cards.indexOfFirst { it.id == result.card1.id }
                val idx2 = engine.cards.indexOfFirst { it.id == result.card2.id }

                Handler(Looper.getMainLooper()).postDelayed({
                    engine.turnCardsFaceDown(idx1, idx2)
                    if (idx1 >= 0) adapter.notifyItemChanged(idx1)
                    if (idx2 >= 0) adapter.notifyItemChanged(idx2)
                    isProcessingFlip = false
                }, 800)
            }
        }
    }

    private fun updateBadgesUI() {
        val engine = gameEngine ?: return
        binding.tvPairsCounter.text = getString(R.string.memory_pairs_counter_format, engine.pairsFound, engine.pairsTotal)
        binding.tvStreakCounter.text = getString(R.string.memory_streak_format, engine.streak)
    }

    private fun showVictoryDialog() {
        val engine = gameEngine ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.memory_victory_title)
            .setMessage(getString(R.string.memory_victory_desc, engine.maxStreak) + "\n" + getString(R.string.memory_score_earned))
            .setPositiveButton(R.string.memory_btn_next) { dialog, _ ->
                dialog.dismiss()
                loadNewMemoryGame()
            }
            .setNegativeButton(R.string.memory_btn_finish) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
