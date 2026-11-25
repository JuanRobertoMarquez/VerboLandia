package com.example.verbolandia.adapter

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.R
import com.example.verbolandia.databinding.ItemBatteryCardBinding

data class BatteryCardItem(
    val id: String,
    val word: String,
    val ipa: String,
    val tag: String,
    val role: String,
    val isTrap: Boolean = false,
    val trapReason: String = "",
    val targetTense: Int = 0, // 2 = V2 Pasado, 3 = V3 Participio, 0 = Distractor
    var isUsed: Boolean = false
)

class BatteryCardAdapter(
    private val onCardClicked: (BatteryCardItem, Int) -> Unit
) : RecyclerView.Adapter<BatteryCardAdapter.ViewHolder>() {

    private val items = mutableListOf<BatteryCardItem>()
    private var itemHeight: Int = 0

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<BatteryCardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItemHeight(height: Int) {
        if (itemHeight != height && height > 0) {
            itemHeight = height
            notifyDataSetChanged()
        }
    }

    fun getItem(position: Int): BatteryCardItem? {
        return if (position in items.indices) items[position] else null
    }

    fun markUsedById(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            items[index].isUsed = true
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBatteryCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (itemHeight > 0) {
            val lp = holder.itemView.layoutParams
            if (lp.height != itemHeight) {
                lp.height = itemHeight
                holder.itemView.layoutParams = lp
            }
        }
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemBatteryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BatteryCardItem) {
            binding.tvCardTag.text = item.tag
            binding.tvCardWord.text = item.word
            binding.tvCardIpa.text = item.ipa
            binding.tvCardRole.text = item.role

            val context = binding.root.context
            val primaryColor = ContextCompat.getColor(context, R.color.primary)
            val errorColor = ContextCompat.getColor(context, R.color.error)
            val strokeNormal = ContextCompat.getColor(context, R.color.outline_variant)
            val surfaceNormal = ContextCompat.getColor(context, R.color.surface_container_lowest)
            val disabledSurface = ContextCompat.getColor(context, R.color.surface_container_high)

            if (item.isUsed) {
                binding.cardBatteryOption.alpha = 0.25f
                binding.cardBatteryOption.isClickable = false
                binding.cardBatteryOption.strokeColor = strokeNormal
                binding.cardBatteryOption.setCardBackgroundColor(disabledSurface)
            } else {
                binding.cardBatteryOption.alpha = 1.0f
                binding.cardBatteryOption.isClickable = true
                binding.cardBatteryOption.setCardBackgroundColor(surfaceNormal)
                if (item.isTrap) {
                    binding.tvCardTag.background = ContextCompat.getDrawable(context, R.drawable.bg_badge_reinforcement)
                    binding.tvCardTag.setTextColor(errorColor)
                    binding.cardBatteryOption.strokeColor = errorColor
                } else {
                    binding.tvCardTag.background = ContextCompat.getDrawable(context, R.drawable.bg_focus_pill)
                    binding.tvCardTag.setTextColor(primaryColor)
                    binding.cardBatteryOption.strokeColor = strokeNormal
                }
            }

            // Pure Tap interaction (Drag & Drop removed per user request)
            binding.cardBatteryOption.setOnClickListener {
                if (!item.isUsed) {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onCardClicked(item, bindingAdapterPosition)
                }
            }
        }

        fun shakeAndHighlightError() {
            binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val context = binding.root.context
            val errorColor = ContextCompat.getColor(context, R.color.error)
            binding.cardBatteryOption.strokeColor = errorColor

            val anim = ObjectAnimator.ofFloat(
                binding.cardBatteryOption,
                "translationX",
                0f, 20f, -20f, 15f, -15f, 8f, -8f, 0f
            )
            anim.duration = 400
            anim.start()

            binding.root.postDelayed({
                val strokeNormal = ContextCompat.getColor(context, R.color.outline_variant)
                binding.cardBatteryOption.strokeColor = strokeNormal
            }, 800)
        }
    }
}
