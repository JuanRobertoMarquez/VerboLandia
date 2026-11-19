package com.example.verbolandia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.R
import com.example.verbolandia.databinding.ItemVerbCardBinding
import com.example.verbolandia.model.Verb

class HistoryAdapter(
    private val onItemClick: (Verb) -> Unit
) : ListAdapter<Verb, HistoryAdapter.HistoryViewHolder>(HistoryDiffCallback) {

    class HistoryViewHolder(val binding: ItemVerbCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemVerbCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val verb = getItem(position)
        with(holder.binding) {
            val context = root.context
            tvInfinitive.text = verb.base
            tvPhonetic.text = verb.phonetic
            tvSpanishMeaning.text = verb.spanishMeaning
            tvTypeBadge.text = if (verb.isIrregular) {
                context.getString(R.string.verb_type_irregular)
            } else {
                context.getString(R.string.verb_type_regular)
            }
            tvLevelBadge.text = context.getString(R.string.badge_recent)

            tvV1.text = verb.base
            tvV2.text = verb.past
            tvV3.text = verb.pastParticiple

            tvMastery.text = context.getString(R.string.mastery_pct_format, verb.mastery)
            progressMastery.progress = verb.mastery

            root.setOnClickListener {
                onItemClick(verb)
            }
        }
    }

    object HistoryDiffCallback : DiffUtil.ItemCallback<Verb>() {
        override fun areItemsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem.base == newItem.base
        }

        override fun areContentsTheSame(oldItem: Verb, newItem: Verb): Boolean {
            return oldItem == newItem
        }
    }
}
