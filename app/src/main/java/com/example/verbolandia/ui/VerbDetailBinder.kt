package com.example.verbolandia.ui

import android.content.Intent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import com.example.verbolandia.GrammarWebActivity
import com.example.verbolandia.R
import com.example.verbolandia.VideoTutorialActivity
import com.example.verbolandia.databinding.FragmentVerbDetailBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.VerbClassifier
import com.example.verbolandia.util.VerbStatsRecord

object VerbDetailBinder {

    fun bindVerb(
        binding: FragmentVerbDetailBinding,
        verb: Verb,
        stats: VerbStatsRecord,
        onBookmarkClick: () -> Unit,
        onShareClick: () -> Unit,
        onPracticeClick: () -> Unit,
        onTranslateClick: () -> Unit
    ) {
        val context = binding.root.context

        // Title & Badges
        binding.tvVerbName.text = verb.base
        binding.badgeIrregular.text = if (verb.isIrregular) "IRREGULAR" else "REGULAR"
        binding.badgeHighFreq.visibility = if (verb.isCommon) View.VISIBLE else View.GONE
        binding.badgeLevel.text = verb.level

        // Meaning
        binding.tvMeaningPrimary.text = if (verb.spanishMeaning.isNotBlank()) verb.spanishMeaning else "Toca para traducir"
        binding.cardMeaning.setOnClickListener { onTranslateClick() }

        // Audio button
        binding.btnPlayAudio.visibility = View.VISIBLE
        binding.btnPlayAudio.setOnClickListener {
            com.example.verbolandia.util.AudioSpeaker.speak(context, verb.base)
        }

        // Hide static fields without real data
        binding.tvPhonetic.visibility = View.GONE
        binding.cardMnemonic.visibility = View.GONE
        binding.cardEx1.visibility = View.GONE
        binding.cardEx2.visibility = View.GONE
        binding.cardEx3.visibility = View.GONE

        // Matrix V1, V2, V3
        binding.tvMatrixV1Val.text = verb.base
        binding.tvMatrixV1Phonetic.text = "/${verb.base}/"

        binding.tvMatrixV2Val.text = verb.past
        binding.tvMatrixV2Phonetic.text = "/${verb.past}/"

        binding.tvMatrixV3Val.text = verb.pastParticiple
        binding.tvMatrixV3Phonetic.text = "/${verb.pastParticiple}/"

        // Derived Forms
        binding.tvDerivedThirdPerson.text = "3ª Persona: ${verb.thirdPerson}"
        binding.tvDerivedGerund.text = "Gerundio (-ing): ${verb.gerund}"

        // Error Común
        val commonErr = VerbClassifier.generateCommonError(verb)
        if (commonErr != null) {
            binding.cardErrorWarning.visibility = View.VISIBLE
            binding.tvErrorWarningDesc.text = commonErr
        } else {
            binding.cardErrorWarning.visibility = View.GONE
        }

        // Retention Card & Stats
        binding.cardRetention.visibility = View.VISIBLE
        if (stats.attempts > 0) {
            val accuracyPct = ((stats.correctCount.toDouble() / stats.attempts) * 100).toInt()
            binding.tvRetentionMetric.text = "$accuracyPct%"
            binding.progressRetention.progress = accuracyPct
            binding.tvRetentionViews.text = "Visto ${stats.views} veces • ${stats.attempts} intentos"
            binding.tvRetentionLastSuccess.text = if (stats.lastCorrectAtMs > 0) "Último acierto registrado" else "Sin aciertos aún"
        } else {
            binding.tvRetentionMetric.text = "0%"
            binding.progressRetention.progress = 0
            binding.tvRetentionViews.text = "Aún no has practicado este verbo"
            binding.tvRetentionLastSuccess.text = "Visto ${stats.views} veces"
        }

        // Android Widgets Integration (RatingBar & SeekBar)
        binding.myRatingBar.rating = if (verb.isIrregular) 4.0f else 2.0f
        binding.rateText.text = "Dificultad: ${binding.myRatingBar.rating.toInt()} / 5 ⭐"

        binding.myRatingBar.setOnRatingBarChangeListener { _, rating, _ ->
            binding.rateText.text = "Dificultad: ${rating.toInt()} / 5 ⭐"
            Toast.makeText(context, "Valoración de dificultad guardada: ${rating.toInt()} estrellas", Toast.LENGTH_SHORT).show()
        }

        binding.seekBarResult.text = "Ajuste de velocidad: ${binding.mySeekBar.progress}%"
        binding.mySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.seekBarResult.text = "Ajuste de velocidad: ${progress}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                binding.seekBarResult.text = "Ajustando velocidad..."
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                binding.seekBarResult.text = "Velocidad establecida: ${seekBar?.progress}%"
            }
        })

        // Web & Video Buttons
        binding.btnOpenWebView.setOnClickListener {
            val intent = Intent(context, GrammarWebActivity::class.java).apply {
                putExtra(GrammarWebActivity.EXTRA_VERB_BASE, verb.base)
            }
            context.startActivity(intent)
        }

        binding.btnOpenVideoView.setOnClickListener {
            val intent = Intent(context, VideoTutorialActivity::class.java).apply {
                putExtra(VideoTutorialActivity.EXTRA_VERB_TITLE, verb.base.uppercase())
            }
            context.startActivity(intent)
        }

        // Bookmark Icon Tint
        if (stats.isFavorite) {
            binding.btnBookmark.setColorFilter(context.getColor(R.color.primary_accent))
        } else {
            binding.btnBookmark.clearColorFilter()
        }

        // Click Handlers
        binding.btnBookmark.setOnClickListener { onBookmarkClick() }
        binding.btnShare.setImageResource(R.drawable.ic_share)
        binding.btnShare.setOnClickListener { onShareClick() }
        binding.btnPractice.setOnClickListener { onPracticeClick() }
    }
}
