package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.verbolandia.databinding.FragmentVerbDetailBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.ui.SharedViewModel
import com.example.verbolandia.ui.VerbDetailBinder
import com.example.verbolandia.util.TranslationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Locale

class VerbDetailActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_VERB = "extra_verb"
    }

    private lateinit var binding: FragmentVerbDetailBinding
    private val viewModel: SharedViewModel by viewModels()
    private var currentVerb: Verb? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private lateinit var translationManager: TranslationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = FragmentVerbDetailBinding.inflate(layoutInflater)
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

        tts = TextToSpeech(this, this)
        translationManager = TranslationManager(this)

        @Suppress("DEPRECATION")
        val verbFromExtra = intent.getSerializableExtra(EXTRA_VERB) as? Verb
        if (verbFromExtra != null) {
            currentVerb = verbFromExtra
            renderVerb(verbFromExtra)
            viewModel.onVerbOpened(verbFromExtra)
        }

        val initialTitle = currentVerb?.let { if (it.phonetic.isNotBlank()) "${it.base} · ${it.phonetic}" else it.base }
            ?: getString(R.string.verb_detail_title)
        val initialCategory = if (currentVerb?.isIrregular == true) "VERBO IRREGULAR" else "VERBO REGULAR"

        com.example.verbolandia.ui.AppBarHelper.configure(
            binding = binding.appBar,
            left = com.example.verbolandia.ui.LeftSlot.Back { finish() },
            eyebrow = initialCategory,
            title = initialTitle,
            rightActions = listOf(
                com.example.verbolandia.ui.RightAction.Bookmark(
                    isBookmarked = currentVerb?.isFavorite == true,
                    onClick = { currentVerb?.let { viewModel.toggleFavorite(it) } }
                ),
                com.example.verbolandia.ui.RightAction.Audio {
                    currentVerb?.let { speakText(it.base) }
                }
            )
        )

        observeViewModel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    private fun speakText(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verb_speech")
        } else {
            Toast.makeText(this, "Pronunciando: $text", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderVerb(verb: Verb) {
        currentVerb = verb
        val stats = viewModel.progressSummary.value.topWeakVerbs.find { it.base == verb.base }
            ?: com.example.verbolandia.util.VerbStatsRecord(verb.base, isFavorite = verb.isFavorite, mastery = verb.mastery)

        VerbDetailBinder.bindVerb(
            binding = binding,
            verb = verb,
            stats = stats,
            onBookmarkClick = {
                viewModel.toggleFavorite(verb)
                val msg = if (verb.isFavorite) {
                    getString(R.string.snackbar_favorite_removed)
                } else {
                    getString(R.string.snackbar_favorite_added)
                }
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.snackbar_action_undo)) {
                        viewModel.toggleFavorite(verb)
                    }
                    .show()
            },
            onShareClick = {
                shareVerb(verb)
            },
            onPracticeClick = {
                com.example.verbolandia.ui.VerbPracticeBottomSheetFragment.newInstance(verb)
                    .show(supportFragmentManager, "VerbPracticeBottomSheet")
            },
            onTranslateClick = {
                requestTranslationDownload(verb)
            }
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDetailVerb.collect { verb ->
                    if (verb != null) {
                        renderVerb(verb)
                    }
                }
            }
        }
    }

    private fun requestTranslationDownload(verb: Verb) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_download_model_title))
            .setMessage(getString(R.string.dialog_download_model_msg, verb.base))
            .setPositiveButton(getString(R.string.dialog_download_model_positive)) { dialog, _ ->
                dialog.dismiss()
                performTranslation(verb)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun performTranslation(verb: Verb) {
        Snackbar.make(binding.root, getString(R.string.snackbar_translating), Snackbar.LENGTH_INDEFINITE).show()
        lifecycleScope.launch {
            translationManager.translateAndSave(
                verbBase = verb.base,
                onSuccess = { translatedText ->
                    Snackbar.make(
                        binding.root,
                        getString(R.string.snackbar_translated_format, translatedText),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    viewModel.onVerbOpened(verb)
                },
                onError = { error ->
                    val errorMsg = error.localizedMessage ?: ""
                    Snackbar.make(
                        binding.root,
                        getString(R.string.snackbar_translate_error_format, errorMsg),
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(getString(R.string.snackbar_action_retry)) {
                            performTranslation(verb)
                        }
                        .show()
                }
            )
        }
    }

    private fun shareVerb(verb: Verb) {
        VerbDetailBinder.shareVerb(this, verb)
    }

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        translationManager.close()
        super.onDestroy()
    }
}
