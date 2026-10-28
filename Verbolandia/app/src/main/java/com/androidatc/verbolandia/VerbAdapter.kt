package com.androidatc.verbolandia

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidatc.verbolandia.databinding.ItemVerbCardBinding

/**
 * Adaptador de RecyclerView para renderizar el listado sintáctico de verbos en Verbolandia.
 */
class VerbAdapter(
    private val onVerbClick: (Verb) -> Unit,
    private val onAudioClick: ((Verb) -> Unit)? = null
) : ListAdapter<Verb, VerbAdapter.VerbViewHolder>(VerbDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerbViewHolder {
        val binding = ItemVerbCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VerbViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VerbViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VerbViewHolder(
        private val binding: ItemVerbCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(verb: Verb) {
            val context = binding.root.context

            // Textos básicos
            binding.tvVerbBase.text = verb.base
            binding.tvVerbPhonetic.text = verb.phonetic
            binding.tvSpanishMeaning.text = verb.spanishMeaning

            // Matriz Sintáctica V1, V2, V3
            binding.tvV1Value.text = verb.base
            binding.tvV2Value.text = verb.pastSimple
            binding.tvV3Value.text = verb.pastParticiple

            // Badges de Nivel y Tipo
            binding.tvLevelChip.text = verb.level
            binding.tvTypeChip.text = if (verb.isIrregular) {
                context.getString(R.string.tag_irregular)
            } else {
                context.getString(R.string.tag_regular)
            }

            // Indicador de Dominio / Repaso Frecuente
            if (verb.isFrequentReview) {
                // Estado de Advertencia / Repaso Frecuente
                val alertText = "⚠️ " + context.getString(R.string.metric_frequent_review)
                val errorColor = ContextCompat.getColor(context, R.color.md_theme_light_error)
                binding.tvMasteryMetric.text = alertText
                binding.tvMasteryMetric.setTextColor(errorColor)
                binding.progressMastery.progress = 40
                binding.progressMastery.setIndicatorColor(errorColor)
            } else {
                val primaryColor = ContextCompat.getColor(context, R.color.brand_primary)
                binding.tvMasteryMetric.setTextColor(primaryColor)
                binding.progressMastery.setIndicatorColor(primaryColor)

                if (verb.masteryPercentage == 100) {
                    val masteredText = "✓ " + context.getString(R.string.metric_mastered, 100)
                    binding.tvMasteryMetric.text = masteredText
                } else {
                    binding.tvMasteryMetric.text = context.getString(
                        R.string.metric_mastered,
                        verb.masteryPercentage
                    )
                }
                binding.progressMastery.progress = verb.masteryPercentage
            }

            // Listeners de Click
            binding.root.setOnClickListener {
                onVerbClick(verb)
            }

            binding.btnAudio.setOnClickListener {
                onAudioClick?.invoke(verb)
            }
        }
    }

    private class VerbDiffCallback : DiffUtil.ItemCallback<Verb>() {
        override fun areItemsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem == newItem
        }
    }
}