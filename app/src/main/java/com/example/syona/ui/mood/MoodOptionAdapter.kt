package com.example.syona.ui.mood

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.syona.data.model.MoodType
import com.example.syona.databinding.ItemMoodOptionBinding

class MoodOptionAdapter(
    private val items: List<MoodType>,
    private var selected: MoodType?,
    private val onSelect: (MoodType) -> Unit
) : RecyclerView.Adapter<MoodOptionAdapter.MoodOptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodOptionViewHolder {
        val binding = ItemMoodOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MoodOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoodOptionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MoodOptionViewHolder(private val binding: ItemMoodOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mood: MoodType) {
            binding.moodEmojiView.text = mood.emoji
            binding.moodLabelView.text = mood.label
            val context = binding.root.context
            val isSelected = selected == mood
            val backgroundRes = if (isSelected) com.example.syona.R.drawable.bg_mood_option_selected else com.example.syona.R.drawable.bg_mood_option_unselected
            binding.moodOptionContainer.setBackgroundResource(backgroundRes)
            val inactiveColor = ContextCompat.getColor(context, com.example.syona.R.color.color_on_background)
            val activeColor = ContextCompat.getColor(context, android.R.color.white)
            binding.moodLabelView.setTextColor(if (isSelected) activeColor else inactiveColor)
            binding.moodOptionContainer.setOnClickListener {
                selected = mood
                notifyDataSetChanged()
                onSelect(mood)
            }
        }
    }

    fun getSelected(): MoodType? = selected
}
