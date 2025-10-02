package com.example.syona.ui.mood

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.syona.R
import com.example.syona.data.model.MoodEntry
import com.example.syona.databinding.ItemMoodBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MoodAdapter(
    private val onDelete: (MoodEntry) -> Unit
) : ListAdapter<MoodEntry, MoodAdapter.MoodViewHolder>(DiffCallback) {

    private val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val binding = ItemMoodBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MoodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MoodViewHolder(private val binding: ItemMoodBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: MoodEntry) {
            binding.moodEmoji.text = entry.mood.emoji
            binding.moodLabel.text = entry.mood.label
            val localDateTime = Instant.ofEpochMilli(entry.timestamp)
                .atZone(ZoneId.systemDefault())
            binding.moodTimestamp.text = formatter.format(localDateTime)
            if (!entry.note.isNullOrBlank()) {
                binding.moodNote.visibility = android.view.View.VISIBLE
                binding.moodNote.text = entry.note
            } else {
                binding.moodNote.visibility = android.view.View.GONE
            }
            binding.moodDeleteButton.setOnClickListener { onDelete(entry) }
            binding.root.setBackgroundResource(R.drawable.bg_card)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MoodEntry>() {
        override fun areItemsTheSame(oldItem: MoodEntry, newItem: MoodEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MoodEntry, newItem: MoodEntry): Boolean = oldItem == newItem
    }
}
