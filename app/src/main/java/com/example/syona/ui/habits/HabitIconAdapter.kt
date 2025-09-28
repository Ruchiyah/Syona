package com.example.syona.ui.habits

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import com.example.syona.R
import com.example.syona.data.model.HabitIcon
import com.example.syona.databinding.ItemHabitIconBinding

class HabitIconAdapter(
    private val items: List<HabitIcon>,
    private var selected: HabitIcon,
    private val onSelect: (HabitIcon) -> Unit
) : RecyclerView.Adapter<HabitIconAdapter.IconViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val binding = ItemHabitIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IconViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class IconViewHolder(private val binding: ItemHabitIconBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(icon: HabitIcon) {
            binding.iconView.setImageResource(icon.drawableRes)
            binding.iconLabel.text = icon.displayName
            val context = binding.root.context
            val selectedColor = context.getColor(R.color.color_gradient_start)
            val normalStroke = context.getColor(R.color.color_secondary)
            binding.root.strokeWidth = if (icon == selected) 4 else 2
            binding.root.strokeColor = if (icon == selected) selectedColor else normalStroke
            val background = if (icon == selected) {
                ColorStateList.valueOf(context.getColor(R.color.color_background_soft))
            } else {
                ColorStateList.valueOf(context.getColor(android.R.color.white))
            }
            binding.root.setCardBackgroundColor(background)
            binding.root.setOnClickListener {
                selected = icon
                notifyDataSetChanged()
                onSelect(icon)
            }
        }
    }

    fun getSelected(): HabitIcon = selected
}
