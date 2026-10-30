package com.example.verbolandia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.FragmentVerbDetailBinding
import com.example.verbolandia.model.Verb
import com.google.android.material.snackbar.Snackbar

class VerbDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VERB = "extra_verb"
    }

    private lateinit var binding: FragmentVerbDetailBinding
    private lateinit var repository: VerbRepository
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

        repository = VerbRepository(this)

        @Suppress("DEPRECATION")
        currentVerb = intent.getSerializableExtra(EXTRA_VERB) as? Verb

        if (currentVerb != null) {
            populateUI(currentVerb!!)
        } else {
            finish()
        }

        setupClickListeners()
    }

    private fun populateUI(verb: Verb) {
        binding.tvVerbName.text = verb.base
        binding.tvPhonetic.text = verb.phonetic
        binding.badgeIrregular.text = if (verb.isIrregular) "Irregular" else "Regular"
        binding.badgeLevel.text = verb.level

        // Set favorite icon color
        updateBookmarkIcon(verb.isFavorite)

        // Actions on bottom bar & share
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPlayAudio.setOnClickListener {
            Snackbar.make(binding.root, "Pronunciación: /${verb.base}/", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupClickListeners() {
        val verb = currentVerb ?: return

        binding.btnBookmark.setOnClickListener {
            val isNowFav = repository.toggleFavorite(verb.base)
            verb.isFavorite = isNowFav
            updateBookmarkIcon(isNowFav)

            val message = if (isNowFav) "Añadido a favoritos" else "Eliminado de favoritos"
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction("DESHACER") {
                    val reverted = repository.toggleFavorite(verb.base)
                    verb.isFavorite = reverted
                    updateBookmarkIcon(reverted)
                }
                .show()
        }

        // Implicit Intent 1: Share verb via Intent.ACTION_SEND
        binding.btnShuffle.setOnClickListener {
            shareVerb(verb)
        }

        // Implicit Intent 2: Search web / translate via Intent.ACTION_VIEW
        binding.cardMeaning.setOnClickListener {
            openWebSearch(verb.base)
        }

        binding.btnPractice.setOnClickListener {
            Snackbar.make(binding.root, "Practicando verbo ${verb.base}", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateBookmarkIcon(isFavorite: Boolean) {
        if (isFavorite) {
            binding.btnBookmark.setColorFilter(getColor(R.color.primary_accent))
        } else {
            binding.btnBookmark.clearColorFilter()
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

    private fun openWebSearch(verbBase: String) {
        val url = "https://translate.google.com/?sl=en&tl=es&text=$verbBase"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
