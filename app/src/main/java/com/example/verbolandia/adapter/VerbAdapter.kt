package com.example.verbolandia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.databinding.ItemVerbCardBinding
import com.example.verbolandia.model.Verb

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
            tvInfinitive.text = verb.base
            tvPhonetic.text = verb.phonetic
            tvSpanishMeaning.text = verb.spanishMeaning
            tvTypeBadge.text = if (verb.isIrregular) "Irregular" else "Regular"
            tvLevelBadge.text = verb.level

            tvV1.text = verb.base
            tvV2.text = verb.past
            tvV3.text = verb.pastParticiple

            tvMastery.text = "${verb.mastery}% Dominado"
            progressMastery.progress = verb.mastery

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
