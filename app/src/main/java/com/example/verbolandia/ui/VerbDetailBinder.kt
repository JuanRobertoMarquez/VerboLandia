package com.example.verbolandia.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

        // Encabezado Unificado y Dinámico (Detalle de Verbo / Subpantallas)
        val verbCategory = if (verb.isIrregular) {
            context.getString(R.string.eyebrow_verb_irregular)
        } else {
            context.getString(R.string.eyebrow_verb_regular)
        }
        val displayPhonetic = if (verb.ipaBase.isNotBlank()) verb.ipaBase else verb.phonetic
        val verbTitle = if (displayPhonetic.isNotBlank()) "${verb.base} · $displayPhonetic" else verb.base
        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Back {
                if (context is android.app.Activity) {
                    context.finish()
                }
            },
            eyebrow = verbCategory,
            title = verbTitle,
            rightActions = listOf(
                RightAction.Bookmark(isBookmarked = verb.isFavorite, onClick = onBookmarkClick),
                RightAction.Audio {
                    com.example.verbolandia.util.AudioSpeaker.speak(context, verb.base)
                }
            )
        )

        // Title & Badges
        binding.tvVerbName.text = verb.base
        binding.badgeIrregular.text = if (verb.isIrregular) {
            context.getString(R.string.badge_irregular)
        } else {
            context.getString(R.string.badge_regular)
        }
        binding.badgeHighFreq.visibility = if (verb.isCommon) View.VISIBLE else View.GONE
        binding.badgeLevel.text = verb.level

        // Meaning
        binding.tvMeaningPrimary.text = if (verb.spanishMeaning.isNotBlank()) {
            verb.spanishMeaning
        } else {
            context.getString(R.string.tap_to_translate)
        }
        binding.cardMeaning.setOnClickListener { onTranslateClick() }

        // Hide static fields without real data
        binding.tvPhonetic.visibility = View.GONE
        binding.cardMnemonic.visibility = View.GONE

        // 3 Real/Dynamic Context Examples
        binding.cardEx1.visibility = View.VISIBLE
        binding.cardEx2.visibility = View.VISIBLE
        binding.cardEx3.visibility = View.VISIBLE

        val presSimple = verb.tenses.find { it.tenseCode == "PRESENT_SIMPLE" }
        val pastSimple = verb.tenses.find { it.tenseCode == "PAST_SIMPLE" }
        val presPerf = verb.tenses.find { it.tenseCode == "PRESENT_PERFECT" }

        // Example 1: V1 Present Simple
        val ex1En = presSimple?.affirmativeEn ?: context.getString(R.string.ex_v1_en_format, verb.base)
        val ex1Es = presSimple?.exampleEs ?: if (verb.spanishMeaning.isNotBlank()) {
            context.getString(R.string.ex_v1_es_format, verb.spanishMeaning.lowercase())
        } else {
            context.getString(R.string.ex_v1_es_format, verb.base)
        }
        binding.tvEx1Title.text = context.getString(R.string.ex_v1_title)
        binding.tvEx1En.text = "\"$ex1En\""
        binding.tvEx1Es.text = ex1Es
        binding.btnEx1Audio.setOnClickListener {
            com.example.verbolandia.util.AudioSpeaker.speak(context, ex1En)
        }

        // Example 2: V2 Past Simple
        val ex2En = pastSimple?.affirmativeEn ?: context.getString(R.string.ex_v2_en_format, verb.past)
        val ex2Es = pastSimple?.exampleEs ?: if (verb.spanishMeaning.isNotBlank()) {
            context.getString(R.string.ex_v2_es_format, verb.spanishMeaning.lowercase())
        } else {
            context.getString(R.string.ex_v2_es_format, verb.past)
        }
        binding.tvEx2Title.text = context.getString(R.string.ex_v2_title)
        binding.tvEx2En.text = "\"$ex2En\""
        binding.tvEx2Es.text = ex2Es
        binding.btnEx2Audio.setOnClickListener {
            com.example.verbolandia.util.AudioSpeaker.speak(context, ex2En)
        }

        // Example 3: V3 Present Perfect
        val ex3En = presPerf?.affirmativeEn ?: context.getString(R.string.ex_v3_en_format, verb.pastParticiple)
        val ex3Es = presPerf?.exampleEs ?: if (verb.spanishMeaning.isNotBlank()) {
            context.getString(R.string.ex_v3_es_format, verb.spanishMeaning.lowercase())
        } else {
            context.getString(R.string.ex_v3_es_format, verb.pastParticiple)
        }
        binding.tvEx3Title.text = context.getString(R.string.ex_v3_title)
        binding.tvEx3En.text = "\"$ex3En\""
        binding.tvEx3Es.text = ex3Es
        binding.btnEx3Audio.setOnClickListener {
            com.example.verbolandia.util.AudioSpeaker.speak(context, ex3En)
        }

        // Matrix V1, V2, V3 with real IPA phonetics
        binding.tvMatrixV1Val.text = verb.base
        binding.tvMatrixV1Phonetic.text = if (verb.ipaBase.isNotBlank()) verb.ipaBase else "/${verb.base}/"

        binding.tvMatrixV2Val.text = verb.past
        binding.tvMatrixV2Phonetic.text = if (verb.ipaPast.isNotBlank()) verb.ipaPast else "/${verb.past}/"

        binding.tvMatrixV3Val.text = verb.pastParticiple
        binding.tvMatrixV3Phonetic.text = if (verb.ipaParticiple.isNotBlank()) verb.ipaParticiple else "/${verb.pastParticiple}/"

        // Derived Forms
        binding.tvDerivedThirdPerson.text = context.getString(R.string.derived_third_person_format, verb.thirdPerson)
        binding.tvDerivedGerund.text = context.getString(R.string.derived_gerund_format, verb.gerund)

        // Error Común
        val commonErr = if (verb.commonErrorTip.isNotBlank()) {
            verb.commonErrorTip
        } else {
            VerbClassifier.generateCommonError(context, verb)
        }
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
            binding.tvRetentionViews.text = context.getString(R.string.retention_views_format, stats.views, stats.attempts)
            binding.tvRetentionLastSuccess.text = if (stats.lastCorrectAtMs > 0) {
                context.getString(R.string.retention_last_correct)
            } else {
                context.getString(R.string.retention_no_correct_yet)
            }
        } else {
            binding.tvRetentionMetric.text = "0%"
            binding.progressRetention.progress = 0
            binding.tvRetentionViews.text = context.getString(R.string.retention_not_practiced)
            binding.tvRetentionLastSuccess.text = context.getString(R.string.retention_views_only_format, stats.views)
        }

        // Android Widgets Integration (RatingBar & SeekBar)
        val prefs = context.getSharedPreferences("verb_ratings", android.content.Context.MODE_PRIVATE)
        val defaultRating = if (verb.isIrregular) 3.0f else 2.0f
        val savedRating = prefs.getFloat("rating_${verb.base}", defaultRating).coerceIn(1.0f, 5.0f)
        binding.myRatingBar.rating = savedRating
        binding.rateText.text = context.getString(R.string.difficulty_rating_format, savedRating.toInt())

        binding.myRatingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                val clampedRating = rating.coerceIn(1.0f, 5.0f)
                prefs.edit().putFloat("rating_${verb.base}", clampedRating).apply()
                binding.rateText.text = context.getString(R.string.difficulty_rating_format, clampedRating.toInt())
                Toast.makeText(
                    context,
                    context.getString(R.string.difficulty_toast_format, clampedRating.toInt()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val speedRates = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f)
        val speedLabels = arrayOf(
            context.getString(R.string.speed_0_5x),
            context.getString(R.string.speed_0_75x),
            context.getString(R.string.speed_1_0x),
            context.getString(R.string.speed_1_25x),
            context.getString(R.string.speed_1_5x)
        )
        val speedPrefs = context.getSharedPreferences("tts_prefs", android.content.Context.MODE_PRIVATE)
        val savedSpeedRate = speedPrefs.getFloat("speech_rate", 1.0f)
        com.example.verbolandia.util.AudioSpeaker.setSpeechRate(savedSpeedRate)
        val initialSpeedIndex = speedRates.indexOfFirst { kotlin.math.abs(it - savedSpeedRate) < 0.05f }.let {
            if (it >= 0) it else 2
        }
        binding.mySeekBar.progress = initialSpeedIndex
        binding.seekBarResult.text = context.getString(R.string.speed_pronunciation_format, speedLabels[initialSpeedIndex])

        binding.mySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val index = progress.coerceIn(0, speedRates.size - 1)
                val rate = speedRates[index]
                binding.seekBarResult.text = context.getString(R.string.speed_pronunciation_format, speedLabels[index])
                com.example.verbolandia.util.AudioSpeaker.setSpeechRate(rate)
                if (fromUser) {
                    speedPrefs.edit().putFloat("speech_rate", rate).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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

        // Click Handlers
        binding.btnShare.setImageResource(R.drawable.ic_share)
        binding.btnShare.contentDescription = context.getString(R.string.cd_share)
        binding.btnShare.setOnClickListener { onShareClick() }
        binding.btnPractice.setOnClickListener { onPracticeClick() }
    }

    fun shareVerb(context: Context, verb: Verb) {
        val tapToTranslate = context.getString(R.string.tap_to_translate)
        val meaning = if (verb.spanishMeaning.isNotBlank() && verb.spanishMeaning != tapToTranslate) {
            verb.spanishMeaning
        } else {
            "—"
        }

        val shareText = context.getString(
            R.string.share_verb_format,
            verb.base.uppercase(),
            meaning,
            verb.past,
            verb.pastParticiple,
            verb.gerund
        )

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_verb_title))

        try {
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Verbolandia", shareText)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.share_copied_clipboard), Toast.LENGTH_SHORT).show()
        }
    }
}
