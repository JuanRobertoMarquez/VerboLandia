package com.example.verbolandia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.R
import com.example.verbolandia.databinding.ItemVerbCardBinding
import com.example.verbolandia.model.Verb
import com.example.verbolandia.util.AudioSpeaker

class VerbAdapter(
    private val onItemClick: (Verb) -> Unit
) : ListAdapter<Verb, VerbAdapter.VerbViewHolder>(VerbDiffCallback) {

    class VerbViewHolder(val binding: ItemVerbCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VerbViewHolder {
        val binding = ItemVerbCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VerbViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VerbViewHolder, position: Int) {
        val verb = getItem(position)
        with(holder.binding) {
            val context = root.context
            tvInfinitive.text = verb.base
            tvPhonetic.text = verb.phonetic
            tvSpanishMeaning.text = verb.spanishMeaning

            tvLevelBadge.text = verb.level
            tvTypeBadge.text = if (verb.isIrregular) {
                context.getString(R.string.verb_type_irregular)
            } else {
                context.getString(R.string.verb_type_regular)
            }

            tvV1.text = verb.base
            tvV2.text = verb.past
            tvV3.text = verb.pastParticiple

            when {
                verb.mastery == 100 -> {
                    tvMastery.text = context.getString(R.string.mastery_100)
                    tvMastery.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    tvMastery.setBackgroundResource(R.drawable.bg_badge_small)
                    tvMastery.setPadding(16, 6, 16, 6)
                }
                verb.mastery in 1..65 -> {
                    tvMastery.text = context.getString(R.string.mastery_review_frequent)
                    tvMastery.setTextColor(ContextCompat.getColor(context, R.color.error))
                    tvMastery.setBackgroundResource(R.drawable.bg_error_pill)
                    tvMastery.setPadding(16, 6, 16, 6)
                }
                verb.mastery > 65 -> {
                    tvMastery.text = context.getString(R.string.mastery_pct_format, verb.mastery)
                    tvMastery.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    tvMastery.background = null
                    tvMastery.setPadding(0, 0, 0, 0)
                }
                else -> {
                    tvMastery.text = context.getString(R.string.mastery_unpracticed)
                    tvMastery.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                    tvMastery.background = null
                    tvMastery.setPadding(0, 0, 0, 0)
                }
            }

            progressMastery.progress = verb.mastery

            btnPlayAudio.setOnClickListener {
                AudioSpeaker.speak(root.context, verb.base, it)
            }

            root.setOnClickListener {
                onItemClick(verb)
            }
        }
    }

    object VerbDiffCallback : DiffUtil.ItemCallback<Verb>() {
        override fun areItemsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem.base == newItem.base
        }

        override fun areContentsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem == newItem
        }
    }
}
