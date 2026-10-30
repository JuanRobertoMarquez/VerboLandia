package com.example.verbolandia

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.FragmentVerbDetailBinding
import com.example.verbolandia.model.Verb
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class VerbDetailActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_VERB = "extra_verb"
    }

    private lateinit var binding: FragmentVerbDetailBinding
    private lateinit var repository: VerbRepository
    private var currentVerb: Verb? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

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
        tts = TextToSpeech(this, this)

        @Suppress("DEPRECATION")
        currentVerb = intent.getSerializableExtra(EXTRA_VERB) as? Verb

        if (currentVerb != null) {
            populateUI(currentVerb!!)
        } else {
            finish()
        }

        setupClickListeners()
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

    private fun populateUI(verb: Verb) {
        binding.tvVerbName.text = verb.base
        binding.tvPhonetic.text = verb.phonetic
        binding.badgeIrregular.text = if (verb.isIrregular) "Irregular" else "Regular"
        binding.badgeLevel.text = verb.level
        binding.tvMeaningPrimary.text = verb.spanishMeaning

        // Matrix V1, V2, V3
        binding.tvMatrixV1Val.text = verb.base
        binding.tvMatrixV1Phonetic.text = "/${verb.base}/"

        binding.tvMatrixV2Val.text = verb.past
        binding.tvMatrixV2Phonetic.text = "/${verb.past}/"

        binding.tvMatrixV3Val.text = verb.pastParticiple
        binding.tvMatrixV3Phonetic.text = "/${verb.pastParticiple}/"

        // Derived tenses
        binding.tvDerivedThirdPerson.text = "3ª Persona: ${verb.thirdPerson}"
        binding.tvDerivedGerund.text = "Gerundio (-ing): ${verb.gerund}"

        // Mnemonic pattern
        binding.tvMnemonicFamily1.text = "${verb.base}  ➔  ${verb.past}  ➔  ${verb.pastParticiple}"
        binding.tvMnemonicFamily2.text = "Gerundio: ${verb.gerund}"
        binding.tvMnemonicFamily3.text = "3ª Persona: ${verb.thirdPerson}"

        // Examples
        binding.tvEx1En.text = "\"I ${verb.past} yesterday.\""
        binding.tvEx1Es.text = "Yo ${verb.spanishMeaning.lowercase()} ayer."

        binding.tvEx2En.text = "\"They have ${verb.pastParticiple} recently.\""
        binding.tvEx2Es.text = "Ellos han ${verb.spanishMeaning.lowercase()} recientemente."

        binding.tvEx3En.text = "${verb.base.replaceFirstChar { it.uppercase() }} up"
        binding.tvEx3Es.text = "Expresión común con ${verb.base}."

        // Warning
        binding.tvErrorWarningDesc.text = "Evita decir \"${verb.base}ed\" si es irregular. La forma de pasado es \"${verb.past}\" y participio es \"${verb.pastParticiple}\"."

        updateBookmarkIcon(verb.isFavorite)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPlayAudio.setOnClickListener {
            speakText(verb.base)
        }

        binding.btnEx1Audio.setOnClickListener {
            speakText("I ${verb.past} yesterday")
        }

        binding.btnEx2Audio.setOnClickListener {
            speakText("They have ${verb.pastParticiple} recently")
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

        // Implicit Intent 1: Share verb
        binding.btnShare.setOnClickListener {
            shareVerb(verb)
        }

        // Implicit Intent 2: Search web / translate via Intent.ACTION_VIEW
        binding.cardMeaning.setOnClickListener {
            openWebSearch(verb.base)
        }

        binding.btnPractice.setOnClickListener {
            speakText(verb.base)
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

    override fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        super.onDestroy()
    }
}
