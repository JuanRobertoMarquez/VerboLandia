package com.example.verbolandia.memory

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.R
import com.example.verbolandia.databinding.ItemMemoryCardBinding

class MemoryCardAdapter(
    private var cards: List<MemoryCard>,
    private val onCardClicked: (Int) -> Unit
) : RecyclerView.Adapter<MemoryCardAdapter.CardViewHolder>() {

    fun updateCards(newCards: List<MemoryCard>) {
        this.cards = newCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemMemoryCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], position)
    }

    override fun getItemCount(): Int = cards.size

    inner class CardViewHolder(private val binding: ItemMemoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: MemoryCard, position: Int) {
            val context = binding.root.context
            val successColor = ContextCompat.getColor(context, R.color.success)
            val outlineColor = ContextCompat.getColor(context, R.color.outline_variant)

            // Setup card front texts
            binding.tvCardBadge.text = card.badgeLabel
            binding.tvCardWord.text = card.displayText
            binding.tvCardPhonetic.text = card.phonetic.ifEmpty { "IPA" }
            binding.tvCardMeaning.text = card.meaning

            // Matched vs Unmatched Styling
            if (card.isMatched) {
                binding.cardRoot.strokeColor = successColor
                binding.layoutCardFront.setBackgroundResource(R.drawable.bg_memory_card_matched)
                binding.tvCardWord.setTextColor(successColor)
                binding.tvCardBadge.backgroundTintList = ColorStateList.valueOf(successColor)
                binding.tvCardBadge.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.cardRoot.strokeColor = outlineColor
                binding.layoutCardFront.setBackgroundResource(R.drawable.bg_memory_card_front)
                binding.tvCardWord.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
                binding.tvCardBadge.backgroundTintList = null
                binding.tvCardBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
            }

            // Visibility
            if (card.isFaceUp || card.isMatched) {
                binding.layoutCardBack.visibility = View.GONE
                binding.layoutCardFront.visibility = View.VISIBLE
                binding.layoutCardFront.rotationY = 0f
            } else {
                binding.layoutCardFront.visibility = View.GONE
                binding.layoutCardBack.visibility = View.VISIBLE
                binding.layoutCardBack.rotationY = 0f
            }

            binding.cardRoot.setOnClickListener {
                if (!card.isFaceUp && !card.isMatched) {
                    flipCardUp(binding) {
                        onCardClicked(position)
                    }
                }
            }
        }

        private fun flipCardUp(binding: ItemMemoryCardBinding, onComplete: () -> Unit) {
            val density = binding.root.resources.displayMetrics.density
            val distance = 8000f * density
            binding.layoutCardBack.cameraDistance = distance
            binding.layoutCardFront.cameraDistance = distance

            binding.layoutCardBack.animate()
                .rotationY(90f)
                .setDuration(150)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.layoutCardBack.visibility = View.GONE
                        binding.layoutCardFront.visibility = View.VISIBLE
                        binding.layoutCardFront.rotationY = -90f
                        binding.layoutCardFront.animate()
                            .rotationY(0f)
                            .setDuration(150)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    onComplete()
                                }
                            })
                            .start()
                    }
                })
                .start()
        }
    }
}
