package com.example.verbolandia.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.verbolandia.R
import com.example.verbolandia.databinding.ItemTriadSummaryVerbBinding
import com.example.verbolandia.model.TriadVerbResult

class TriadSummaryAdapter(
    private val results: List<TriadVerbResult>
) : RecyclerView.Adapter<TriadSummaryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTriadSummaryVerbBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTriadSummaryVerbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = results[position]
        val context = holder.itemView.context
        val verb = item.verb

        holder.binding.tvVerbTriadTitle.text = context.getString(
            R.string.triad_summary_verb_title_format,
            verb.base.uppercase(),
            verb.past,
            verb.pastParticiple
        )
        holder.binding.tvVerbLevelMeaning.text = context.getString(
            R.string.triad_summary_verb_subtitle_format,
            verb.level.uppercase(),
            verb.spanishMeaning
        )

        if (item.isCorrect) {
            holder.binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
            holder.binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.success))
            holder.binding.tvStatusBadge.text = context.getString(R.string.triad_status_mastered)
            holder.binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.success))
            holder.binding.layoutErrorDetail.visibility = View.GONE
        } else {
            holder.binding.ivStatusIcon.setImageResource(R.drawable.ic_warning)
            holder.binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error))
            holder.binding.tvStatusBadge.text = context.getString(R.string.triad_status_review)
            holder.binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.error))

            holder.binding.layoutErrorDetail.visibility = View.VISIBLE
            val note = if (item.wrongSelection.isNotBlank() && item.correctExpected.isNotBlank()) {
                context.getString(R.string.triad_confusion_note_format, item.wrongSelection, item.correctExpected)
            } else if (verb.commonErrorTip.isNotBlank()) {
                verb.commonErrorTip
            } else {
                context.getString(R.string.triad_correct_forms_tip_format, verb.base.uppercase(), verb.past, verb.pastParticiple)
            }
            holder.binding.tvErrorNote.text = note
        }
    }

    override fun getItemCount(): Int = results.size
}
