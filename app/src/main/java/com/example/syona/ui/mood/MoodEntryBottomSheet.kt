package com.example.syona.ui.mood

import android.app.Dialog
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.example.syona.R
import androidx.recyclerview.widget.GridLayoutManager
import com.example.syona.data.model.MoodType
import com.example.syona.databinding.SheetMoodEntryBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MoodEntryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetMoodEntryBinding? = null
    private val binding get() = _binding!!

    var onSave: ((MoodType, String?) -> Unit)? = null

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
        _binding = SheetMoodEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = MoodOptionAdapter(MoodType.values().toList(), MoodType.HAPPY) {
            // selection handled in adapter
        }
        binding.moodPickerRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            this.adapter = adapter
        }
        binding.moodSheetClose.setOnClickListener { dismiss() }
        binding.saveMoodButton.setOnClickListener {
            val selected = adapter.getSelected() ?: return@setOnClickListener
            val note = binding.moodNoteEdit.text?.toString()?.takeIf { it.isNotBlank() }
            onSave?.invoke(selected, note)
            dismiss()
        }
        applyTitleGradient()
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
    }

    private fun applyTitleGradient() {
        val titleView = binding.moodSheetTitle
        titleView.post {
            val text = titleView.text?.toString().orEmpty()
            if (text.isEmpty()) return@post
            val textWidth = titleView.paint.measureText(text)
            if (textWidth <= 0f) return@post
            val shader = LinearGradient(
                0f,
                0f,
                textWidth,
                0f,
                intArrayOf(
                    ContextCompat.getColor(requireContext(), R.color.color_mood_sheet_title_start),
                    ContextCompat.getColor(requireContext(), R.color.color_mood_sheet_title_end)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            titleView.paint.shader = shader
            titleView.invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
