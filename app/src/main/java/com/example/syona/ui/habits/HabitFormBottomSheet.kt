package com.example.syona.ui.habits

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.example.syona.R
import com.example.syona.data.model.Habit
import com.example.syona.data.model.HabitFrequency
import com.example.syona.data.model.HabitIcon
import com.example.syona.databinding.SheetHabitBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HabitFormBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetHabitBinding? = null
    private val binding get() = _binding!!

    private var habit: Habit? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        habit = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_HABIT, Habit::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable(ARG_HABIT)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetHabitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        // Get grid column count from resources (varies by device size)
        val columnCount = resources.getInteger(R.integer.habit_icon_grid_columns)

        val iconAdapter = HabitIconAdapter(
            HabitIcon.values().toList(),
            habit?.icon ?: HabitIcon.HEART
        ) {
            // selection handled inside adapter
        }
        binding.iconRecycler.apply {
            layoutManager = GridLayoutManager(context, columnCount)
            adapter = iconAdapter
            setHasFixedSize(true)
        }

        val frequencyOptions = HabitFrequency.values().filter { it != HabitFrequency.WEEKLY }
        val frequencies = frequencyOptions.map { it.displayName }
        val frequencyAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, frequencies)
        binding.frequencyAutoComplete.setAdapter(frequencyAdapter)
        
        binding.frequencyAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val selected = frequencyOptions[position]
            binding.customDaysContainer.visibility = if (selected == HabitFrequency.CUSTOM) View.VISIBLE else View.GONE
        }

        if (habit != null) {
            binding.titleText.setText(R.string.label_edit_habit)
            binding.nameEditText.setText(habit?.name)
            binding.frequencyAutoComplete.setText(habit?.frequency?.displayName, false)
            binding.notesEditText.setText(habit?.notes)
            binding.saveButton.setText(R.string.label_save)
            
            if (habit?.frequency == HabitFrequency.CUSTOM) {
                binding.customDaysContainer.visibility = View.VISIBLE
                habit?.customDays?.forEach { day ->
                    when (day) {
                        java.util.Calendar.SUNDAY -> binding.cbSun.isChecked = true
                        java.util.Calendar.MONDAY -> binding.cbMon.isChecked = true
                        java.util.Calendar.TUESDAY -> binding.cbTue.isChecked = true
                        java.util.Calendar.WEDNESDAY -> binding.cbWed.isChecked = true
                        java.util.Calendar.THURSDAY -> binding.cbThu.isChecked = true
                        java.util.Calendar.FRIDAY -> binding.cbFri.isChecked = true
                        java.util.Calendar.SATURDAY -> binding.cbSat.isChecked = true
                    }
                }
            }
        } else {
            binding.titleText.setText(R.string.title_add_new_habit)
            binding.frequencyAutoComplete.setText(frequencies.first(), false)
            binding.saveButton.setText(R.string.label_add_habit)
        }

        binding.closeButton.setOnClickListener { dismiss() }
        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                binding.nameInputLayout.error = getString(R.string.label_habit_name)
                return@setOnClickListener
            }
            binding.nameInputLayout.error = null
            val frequency = HabitFrequency.fromDisplayName(binding.frequencyAutoComplete.text?.toString())
            
            val customDays = if (frequency == HabitFrequency.CUSTOM) {
                val days = mutableListOf<Int>()
                if (binding.cbSun.isChecked) days.add(java.util.Calendar.SUNDAY)
                if (binding.cbMon.isChecked) days.add(java.util.Calendar.MONDAY)
                if (binding.cbTue.isChecked) days.add(java.util.Calendar.TUESDAY)
                if (binding.cbWed.isChecked) days.add(java.util.Calendar.WEDNESDAY)
                if (binding.cbThu.isChecked) days.add(java.util.Calendar.THURSDAY)
                if (binding.cbFri.isChecked) days.add(java.util.Calendar.FRIDAY)
                if (binding.cbSat.isChecked) days.add(java.util.Calendar.SATURDAY)
                days
            } else null

            val selectedIcon = iconAdapter.getSelected()
            val notes = binding.notesEditText.text?.toString()?.takeIf { it.isNotBlank() }
            val updatedHabit = habit?.copy(
                name = name,
                frequency = frequency,
                customDays = customDays,
                icon = selectedIcon,
                notes = notes
            ) ?: Habit(
                name = name,
                frequency = frequency,
                customDays = customDays,
                icon = selectedIcon,
                notes = notes
            )
            // Fragment Result API survives configuration changes (a lambda field would be lost)
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putParcelable(RESULT_HABIT, updatedHabit) }
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_HABIT = "arg_habit"
        const val REQUEST_KEY = "habit_form_result"
        const val RESULT_HABIT = "result_habit"

        fun newInstance(habit: Habit? = null): HabitFormBottomSheet {
            val fragment = HabitFormBottomSheet()
            fragment.arguments = Bundle().apply {
                putParcelable(ARG_HABIT, habit)
            }
            return fragment
        }
    }
}
