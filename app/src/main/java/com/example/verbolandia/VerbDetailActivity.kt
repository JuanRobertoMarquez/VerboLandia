package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VerbDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VERB = "extra_verb"
    }

    private lateinit var binding: FragmentVerbDetailBinding
    private val viewModel: SharedViewModel by viewModels()
    private var currentVerb: Verb? = null

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
                systemBars.bottom,
            )
            insets
        }

        @Suppress("DEPRECATION")
        val verbFromExtra = intent.getSerializableExtra(EXTRA_VERB) as? Verb
        if (verbFromExtra != null) {
            currentVerb = verbFromExtra
            viewModel.onVerbOpened(verbFromExtra)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentDetailVerb.collect { verb ->
                    if (verb != null) {
                        currentVerb = verb
                        val stats = viewModel.progressSummary.value.topWeakVerbs.find { it.base == verb.base }
                            ?: com.example.verbolandia.util.VerbStatsRecord(verb.base, isFavorite = verb.isFavorite, mastery = verb.mastery)

                        VerbDetailBinder.bindVerb(
                            binding = binding,
                            verb = verb,
                            stats = stats,
                            onBookmarkClick = {
                                viewModel.toggleFavorite(verb)
                                val msg = if (verb.isFavorite) "Eliminado de favoritos" else "Añadido a favoritos"
                                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
                                    .setAction("DESHACER") {
                                        viewModel.toggleFavorite(verb)
                                    }
                                    .show()
                            },
                            onShareClick = {
                                shareVerb(verb)
                            },
                            onPracticeClick = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun shareVerb(verb: Verb) {
        val shareText = "Verbo en Verbolandia: ${verb.base.uppercase()} (${verb.spanishMeaning})\n" +
                "Pasado: ${verb.past}\n" +
                "Participio: ${verb.pastParticiple}\n" +
                "Gerundio: ${verb.gerund}"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Compartir Verbo")
        startActivity(shareIntent)
    }
}
