package com.example.syona.ui.mood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.syona.R
import com.example.syona.databinding.ItemMoodCalendarDayBinding

class MoodCalendarAdapter(
    private val onDateSelected: (java.time.LocalDate) -> Unit
) : RecyclerView.Adapter<MoodCalendarAdapter.CalendarViewHolder>() {

    private val items = mutableListOf<MoodCalendarItem>()

    fun submit(items: List<MoodCalendarItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemMoodCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CalendarViewHolder(private val binding: ItemMoodCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MoodCalendarItem) {
            val context = binding.root.context
            if (item.dayNumber == null) {
                binding.dayNumber.text = ""
                binding.dayEmoji.text = ""
                binding.calendarCell.foreground = null
                binding.calendarCell.setBackgroundResource(0)
                binding.root.visibility = View.INVISIBLE
                binding.root.setOnClickListener(null)
            } else {
                binding.root.visibility = View.VISIBLE
                binding.dayNumber.text = item.dayNumber.toString()
                binding.dayEmoji.text = item.emoji.orEmpty()
                
                val backgroundRes = when {
                    item.isSelected -> R.drawable.bg_calendar_day_selected
                    item.backgroundColor != null -> R.drawable.bg_calendar_day_filled
                    else -> R.drawable.bg_calendar_day
                }
                
                binding.calendarCell.setBackgroundResource(backgroundRes)
                
                // Apply color tint if it's a filled day but not selected (to show mood color)
                if (!item.isSelected && item.backgroundColor != null) {
                    binding.calendarCell.background.mutate().setTint(item.backgroundColor)
                } else {
                    binding.calendarCell.background.mutate().setTintList(null)
                }

                val emojiVisible = !item.emoji.isNullOrEmpty()
                binding.dayEmoji.visibility = if (emojiVisible) View.VISIBLE else View.GONE
                
                binding.root.setOnClickListener {
                    item.date?.let { date -> onDateSelected(date) }
                }
            }
        }
    }
}

data class MoodCalendarItem(
    val dayNumber: Int?,
    val emoji: String?,
    val backgroundColor: Int?,
    val date: java.time.LocalDate?,
    val isSelected: Boolean = false
)
