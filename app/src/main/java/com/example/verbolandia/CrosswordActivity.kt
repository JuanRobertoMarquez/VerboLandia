package com.example.verbolandia

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.crossword.CrosswordBoard
import com.example.verbolandia.crossword.CrosswordClueCandidate
import com.example.verbolandia.crossword.CrosswordGenerator
import com.example.verbolandia.crossword.CrosswordGridView
import com.example.verbolandia.crossword.CrosswordWord
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityCrosswordBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrosswordActivity : AppCompatActivity(), CrosswordGridView.OnCellClickListener {

    companion object {
        const val EXTRA_LEVEL = "extra_level"
    }

    private lateinit var binding: ActivityCrosswordBinding
    private lateinit var repository: VerbRepository

    private var currentLevel: String = "ALL"
    private var board: CrosswordBoard? = null
    private var activeWord: CrosswordWord? = null
    private var cursorRow: Int = -1
    private var cursorCol: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCrosswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        repository = VerbRepository.getInstance(applicationContext)
        currentLevel = intent.getStringExtra(EXTRA_LEVEL) ?: "ALL"

        setupUI()
        setupKeyboard()
        loadNewCrossword()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvCrosswordSubtitle.text = getString(R.string.crossword_subtitle_format, currentLevel)

        binding.crosswordGrid.listener = this

        binding.btnSpeaker.setOnClickListener {
            activeWord?.let { w ->
                com.example.verbolandia.util.AudioSpeaker.speak(this, w.word)
            }
        }
    }

    private fun loadNewCrossword() {
        lifecycleScope.launch {
            val candidates = withContext(Dispatchers.IO) {
                fetchCrosswordCandidates(currentLevel)
            }

            if (candidates.isEmpty()) {
                Snackbar.make(binding.root, R.string.snackbar_no_weak_verbs, Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val newBoard = withContext(Dispatchers.Default) {
                CrosswordGenerator.generate(candidates, targetCount = 4, gridSize = 8)
            }

            board = newBoard
            binding.crosswordGrid.setBoard(newBoard)

            // Select first word
            if (newBoard.words.isNotEmpty()) {
                selectWord(newBoard.words.first())
            }

            setupWordChips(newBoard)
            updateSolvedCounter()
        }
    }

    private suspend fun fetchCrosswordCandidates(level: String): List<CrosswordClueCandidate> {
        val allVerbs = repository.getCatalogVerbs(category = "ALL", level = level)
        val selectedVerbs = allVerbs.filter { it.isIrregular || it.isCommon }.shuffled().take(15)

        val candidates = mutableListOf<CrosswordClueCandidate>()

        for (verb in selectedVerbs) {
            val tenses = repository.getTensesForVerb(verb.id)

            // 1. Past Simple candidate
            if (verb.past.length in 3..6 && verb.past.all { it.isLetter() }) {
                val pastTense = tenses.find { it.tenseCode == "PAST_SIMPLE" }
                val sentence = pastTense?.affirmativeEn ?: "Yesterday they ${verb.past} carefully."
                val blankSentence = sentence.replace(Regex("(?i)\\b${Regex.escape(verb.past)}\\b"), "_______")
                val phon = if (verb.ipaBase.isNotEmpty() && verb.ipaPast.isNotEmpty()) {
                    getString(R.string.phonetic_transition_format, verb.ipaBase, verb.ipaPast)
                } else ""

                candidates.add(
                    CrosswordClueCandidate(
                        verbId = verb.id,
                        base = verb.base,
                        targetWord = verb.past.uppercase(),
                        tenseCode = "PAST_SIMPLE",
                        tenseLabel = getString(R.string.crossword_clue_v2_format, verb.base.uppercase()),
                        exampleSentence = blankSentence,
                        spanishMeaning = verb.spanishMeaning,
                        phoneticChange = phon,
                        level = verb.level
                    )
                )
            }

            // 2. Past Participle candidate
            if (verb.pastParticiple.length in 3..6 && verb.pastParticiple.all { it.isLetter() } &&
                verb.pastParticiple.uppercase() != verb.past.uppercase()) {
                val partTense = tenses.find { it.tenseCode == "PRESENT_PERFECT" }
                    ?: tenses.find { it.tenseCode == "PAST_PERFECT" }
                val sentence = partTense?.affirmativeEn ?: "They have ${verb.pastParticiple} recently."
                val blankSentence = sentence.replace(Regex("(?i)\\b${Regex.escape(verb.pastParticiple)}\\b"), "_______")
                val phon = if (verb.ipaBase.isNotEmpty() && verb.ipaParticiple.isNotEmpty()) {
                    getString(R.string.phonetic_transition_format, verb.ipaBase, verb.ipaParticiple)
                } else ""

                candidates.add(
                    CrosswordClueCandidate(
                        verbId = verb.id,
                        base = verb.base,
                        targetWord = verb.pastParticiple.uppercase(),
                        tenseCode = "PAST_PARTICIPLE",
                        tenseLabel = getString(R.string.crossword_clue_v3_format, verb.base.uppercase()),
                        exampleSentence = blankSentence,
                        spanishMeaning = verb.spanishMeaning,
                        phoneticChange = phon,
                        level = verb.level
                    )
                )
            }
        }

        return candidates
    }

    private fun setupWordChips(board: CrosswordBoard) {
        binding.layoutWordChips.removeAllViews()

        for (word in board.words) {
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
                text = getString(R.string.crossword_chip_format, word.number, word.directionCode, if (word.isSolved) word.word else "••••")
                cornerRadius = (18 * resources.displayMetrics.density).toInt()

                setOnClickListener {
                    selectWord(word)
                }
            }
            binding.layoutWordChips.addView(chip)
        }
        updateChipsUI()
    }

    private fun updateChipsUI() {
        val b = board ?: return
        val primaryColor = ContextCompat.getColor(this, R.color.primary)
        val successColor = ContextCompat.getColor(this, R.color.success)
        val surfaceColor = ContextCompat.getColor(this, R.color.surface_container_low)
        val outlineColor = ContextCompat.getColor(this, R.color.outline_variant)
        val onSurfaceColor = ContextCompat.getColor(this, R.color.on_surface)
        val whiteColor = ContextCompat.getColor(this, R.color.white)

        for (i in 0 until binding.layoutWordChips.childCount) {
            val chip = binding.layoutWordChips.getChildAt(i) as? MaterialButton ?: continue
            val word = b.words.getOrNull(i) ?: continue

            val isSelected = (word.number == activeWord?.number)
            chip.text = getString(R.string.crossword_chip_format, word.number, word.directionCode, if (word.isSolved) word.word else "••••")

            when {
                word.isSolved -> {
                    chip.backgroundTintList = ColorStateList.valueOf(successColor)
                    chip.setTextColor(whiteColor)
                    chip.strokeColor = ColorStateList.valueOf(successColor)
                    chip.setIconResource(R.drawable.ic_check)
                    chip.iconTint = ColorStateList.valueOf(whiteColor)
                }
                isSelected -> {
                    chip.backgroundTintList = ColorStateList.valueOf(primaryColor)
                    chip.setTextColor(whiteColor)
                    chip.strokeColor = ColorStateList.valueOf(primaryColor)
                    chip.icon = null
                }
                else -> {
                    chip.backgroundTintList = ColorStateList.valueOf(surfaceColor)
                    chip.setTextColor(onSurfaceColor)
                    chip.strokeColor = ColorStateList.valueOf(outlineColor)
                    chip.icon = null
                }
            }
        }
    }

    private fun selectWord(word: CrosswordWord) {
        activeWord = word
        binding.crosswordGrid.setActiveWord(word)

        // Find first empty cell or start cell
        var targetR = word.startRow
        var targetC = word.startCol
        val b = board
        if (b != null) {
            for (step in 0 until word.length) {
                val r = word.startRow + if (word.isVertical) step else 0
                val c = word.startCol + if (word.isVertical) 0 else step
                val cell = b.getCell(r, c)
                if (cell != null && !cell.isFilled) {
                    targetR = r
                    targetC = c
                    break
                }
            }
        }
        setCursor(targetR, targetC)

        // Update Clue Card
        val dirName = if (word.isVertical) getString(R.string.crossword_direction_v) else getString(R.string.crossword_direction_h)
        binding.tvDirectionBadge.text = getString(R.string.crossword_clue_direction_format, word.number, dirName)
        binding.tvClueTense.text = word.tenseLabel
        binding.tvClueSentence.text = word.exampleSentenceWithBlank
        binding.tvClueMeaning.text = word.spanishMeaning
        binding.tvCluePhonetics.text = word.phoneticChange.ifEmpty { "IPA" }

        updateChipsUI()
    }

    private fun setCursor(row: Int, col: Int) {
        cursorRow = row
        cursorCol = col
        binding.crosswordGrid.setCursor(row, col)
    }

    override fun onCellClicked(row: Int, col: Int) {
        val b = board ?: return
        val cell = b.getCell(row, col) ?: return

        val curWord = activeWord
        if (curWord != null && cell.wordNumbers.contains(curWord.number)) {
            // Already inside current word, just move cursor
            setCursor(row, col)
        } else {
            // Pick the first word that contains this cell
            val targetNum = cell.wordNumbers.firstOrNull() ?: return
            val newWord = b.words.find { it.number == targetNum } ?: return
            selectWord(newWord)
            setCursor(row, col)
        }
    }

    private fun setupKeyboard() {
        val kb = binding.includeKeyboard

        val letterButtons = listOf(
            kb.keyQ, kb.keyW, kb.keyE, kb.keyR, kb.keyT, kb.keyY, kb.keyU, kb.keyI, kb.keyO, kb.keyP,
            kb.keyA, kb.keyS, kb.keyD, kb.keyF, kb.keyG, kb.keyH, kb.keyJ, kb.keyK, kb.keyL,
            kb.keyZ, kb.keyX, kb.keyC, kb.keyV, kb.keyB, kb.keyN, kb.keyM
        )

        for (btn in letterButtons) {
            btn.setOnClickListener {
                val letter = btn.text.firstOrNull() ?: return@setOnClickListener
                handleLetterInput(letter)
            }
        }

        kb.keyBackspace.setOnClickListener {
            handleBackspace()
        }

        kb.keyHint.setOnClickListener {
            handleHint()
        }
    }

    private fun handleLetterInput(letter: Char) {
        val b = board ?: return
        val word = activeWord ?: return
        val cell = b.getCell(cursorRow, cursorCol) ?: return

        cell.enteredChar = letter.uppercaseChar()
        binding.crosswordGrid.invalidate()

        // Advance cursor to next cell in current word
        advanceCursor(word)

        // Check if word is now solved
        checkWordSolved(word)
    }

    private fun advanceCursor(word: CrosswordWord) {
        val b = board ?: return
        val isVert = word.isVertical

        // Find step of current cursor
        val step = if (isVert) cursorRow - word.startRow else cursorCol - word.startCol
        if (step in 0 until word.length - 1) {
            val nextR = word.startRow + if (isVert) step + 1 else 0
            val nextC = word.startCol + if (isVert) 0 else step + 1
            if (b.getCell(nextR, nextC) != null) {
                setCursor(nextR, nextC)
            }
        }
    }

    private fun handleBackspace() {
        val b = board ?: return
        val word = activeWord ?: return
        val cell = b.getCell(cursorRow, cursorCol) ?: return

        if (cell.isFilled) {
            cell.enteredChar = ' '
            binding.crosswordGrid.invalidate()
        } else {
            // Move cursor backward and clear
            val isVert = word.isVertical
            val step = if (isVert) cursorRow - word.startRow else cursorCol - word.startCol
            if (step > 0) {
                val prevR = word.startRow + if (isVert) step - 1 else 0
                val prevC = word.startCol + if (isVert) 0 else step - 1
                val prevCell = b.getCell(prevR, prevC)
                if (prevCell != null) {
                    prevCell.enteredChar = ' '
                    setCursor(prevR, prevC)
                    binding.crosswordGrid.invalidate()
                }
            }
        }
    }

    private fun handleHint() {
        val b = board ?: return
        val word = activeWord ?: return

        for (step in 0 until word.length) {
            val r = word.startRow + if (word.isVertical) step else 0
            val c = word.startCol + if (word.isVertical) 0 else step
            val cell = b.getCell(r, c)
            if (cell != null && !cell.isCorrect) {
                cell.enteredChar = cell.expectedChar
                setCursor(r, c)
                binding.crosswordGrid.invalidate()
                Snackbar.make(binding.root, getString(R.string.crossword_hint_used, cell.expectedChar.toString()), Snackbar.LENGTH_SHORT).show()
                checkWordSolved(word)
                return
            }
        }
        Snackbar.make(binding.root, R.string.crossword_no_more_hints, Snackbar.LENGTH_SHORT).show()
    }

    private fun checkWordSolved(word: CrosswordWord) {
        val b = board ?: return
        if (!word.isSolved && b.isWordSolved(word)) {
            word.isSolved = true
            com.example.verbolandia.util.AudioSpeaker.speak(this, word.word)

            lifecycleScope.launch(Dispatchers.IO) {
                repository.recordPracticeAttempt(word.verbBase, true, "Crucigrama Gramatical")
            }

            Snackbar.make(binding.root, getString(R.string.crossword_word_solved_format, word.word), Snackbar.LENGTH_SHORT).show()
            updateChipsUI()
            updateSolvedCounter()

            // Check if entire board is solved
            if (b.isFullySolved()) {
                showVictoryDialog()
            } else {
                // Advance to next unsolved word
                val nextUnsolved = b.words.find { !it.isSolved }
                if (nextUnsolved != null) {
                    selectWord(nextUnsolved)
                }
            }
        }
    }

    private fun updateSolvedCounter() {
        val b = board ?: return
        val solved = b.words.count { it.isSolved }
        binding.tvSolvedCounter.text = getString(R.string.goal_status_format, solved, b.words.size)
    }

    private fun showVictoryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.crossword_victory_title)
            .setMessage(getString(R.string.crossword_victory_desc) + "\n" + getString(R.string.crossword_score_earned))
            .setPositiveButton(R.string.crossword_btn_next) { dialog, _ ->
                dialog.dismiss()
                loadNewCrossword()
            }
            .setNegativeButton(R.string.crossword_btn_finish) { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
