package com.example.verbolandia.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.databinding.ItemVerbCardBinding
import com.example.verbolandia.model.Verb

class VerbAdapter(
    private var verbs: List<Verb>,
    private val onItemClick: (Verb) -> Unit
) : RecyclerView.Adapter<VerbAdapter.VerbViewHolder>() {

    inner class VerbViewHolder(val binding: ItemVerbCardBinding) :
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
        val verb = verbs[position]
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

            btnPlayAudio.setOnClickListener {
                Toast.makeText(
                    root.context,
                    "Reproduciendo pronunciación de: ${verb.base}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            root.setOnClickListener {
                onItemClick(verb)
            }
        }
    }

    override fun getItemCount(): Int = verbs.size

    fun updateData(newVerbs: List<Verb>) {
        verbs = newVerbs
        notifyDataSetChanged()
    }
}
