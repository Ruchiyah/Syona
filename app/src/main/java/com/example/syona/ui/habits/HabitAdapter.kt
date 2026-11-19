package com.example.syona.ui.habits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.syona.R
import com.example.syona.data.model.Habit
import com.example.syona.databinding.ItemHabitBinding

class HabitAdapter(
    private val onToggle: (Habit) -> Unit,
    private val onEdit: (Habit) -> Unit,
    private val onDelete: (Habit) -> Unit
) : ListAdapter<Habit, HabitAdapter.HabitViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HabitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HabitViewHolder(private val binding: ItemHabitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(habit: Habit) {
            binding.habitName.text = habit.name
            binding.habitFrequency.text = habit.frequency.displayName
            binding.habitIcon.setImageResource(habit.icon.drawableRes)

            // Detach the listener from the previous binding BEFORE changing the checked state;
            // otherwise a recycled row fires the old listener and toggles the wrong habit.
            binding.habitCheckBox.setOnCheckedChangeListener(null)
            binding.habitCheckBox.isChecked = habit.completed

            binding.habitCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != habit.completed) onToggle(habit)
            }

            binding.optionsButton.setOnClickListener { view ->
                showMenu(view, habit)
            }
        }

        private fun showMenu(view: View, habit: Habit) {
            try {
                val popup = PopupMenu(view.context, view)
                popup.inflate(R.menu.menu_habit_item)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> {
                            onEdit(habit)
                            true
                        }
                        R.id.action_delete -> {
                            onDelete(habit)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            } catch (e: Exception) {
                // Popup menu failed to show, skip
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Habit>() {
        override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean = oldItem == newItem
    }
}
