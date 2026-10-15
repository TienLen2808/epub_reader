package com.example.epub_reader.ui.main

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogLibraryPickerBinding
import com.example.epub_reader.databinding.ItemPickFileBinding
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.ImportedFileItem
import java.util.Locale

/**
 * Multi-select list of the library files (used for the Dropbox / Google Drive batch upload).
 * Returns the selected ids with the Fragment Result API ([REQUEST_KEY]).
 */
class LibraryPickerDialog : IosFullScreenDialogFragment() {

    private var _binding: DialogLibraryPickerBinding? = null
    private val binding get() = _binding!!

    private var files: List<ImportedFileItem> = emptyList()
    private val selectedIds = linkedSetOf<String>()
    private val adapter = PickerAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLibraryPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root, binding.layoutTopBar, binding.rvPickerFiles)
        fitNavigationTitle(
            bar = binding.layoutPickerNavBar,
            title = binding.tvPickerTitle,
            leading = binding.btnPickerCancel,
            trailing = binding.btnPickerDone
        )

        binding.tvPickerTitle.text = arguments?.getString(ARG_TITLE).orEmpty()
        binding.btnPickerCancel.setOnClickListener { dismiss() }
        binding.btnPickerDone.setOnClickListener { deliver() }
        binding.btnPickerSelectAll.setOnClickListener { toggleAll() }

        // Sample files and passcode protected files are not offered (same rule as the Wi-Fi page)
        files = FileItemManager.instance.getFiles()
            .filter { isUploadable(it) }
            .sortedBy { it.fileName.lowercase(Locale.ROOT) }
        savedInstanceState?.getStringArrayList(STATE_SELECTED)?.let { saved ->
            selectedIds.addAll(saved.filter { id -> files.any { it.id == id } })
        }

        binding.rvPickerFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPickerFiles.adapter = adapter
        binding.tvPickerEmpty.isVisible = files.isEmpty()
        updateHeader()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_SELECTED, ArrayList(selectedIds))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleAll() {
        if (files.isEmpty()) return
        if (selectedIds.size == files.size) {
            selectedIds.clear()
        } else {
            files.forEach { selectedIds.add(it.id) }
        }
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun updateHeader() {
        val b = _binding ?: return
        val count = selectedIds.size
        b.tvPickerCount.text = if (count > 0) {
            getString(R.string.selected_count, count)
        } else {
            getString(R.string.pick_files_hint)
        }
        // The button keeps the same text so the title in the bar does not move while selecting
        b.btnPickerDone.isEnabled = count > 0
        b.btnPickerDone.alpha = if (count > 0) 1f else 0.35f
        b.btnPickerSelectAll.isVisible = files.isNotEmpty()
        b.btnPickerSelectAll.setText(
            if (files.isNotEmpty() && count == files.size) R.string.deselect_all else R.string.select_all
        )
    }

    private fun deliver() {
        if (selectedIds.isEmpty()) return
        val result = Bundle().apply {
            putStringArrayList(RESULT_IDS, ArrayList(selectedIds))
            putString(RESULT_TARGET, arguments?.getString(ARG_TARGET))
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    private inner class PickerAdapter : RecyclerView.Adapter<PickerAdapter.Holder>() {

        inner class Holder(val binding: ItemPickFileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemCount(): Int = files.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemPickFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = files[position]
            val b = holder.binding
            val isSelected = selectedIds.contains(item.id)

            b.tvPickName.text = item.fileName
            b.tvPickSub.text = "${item.fileSizeFormatted.replace(" ", "")} · ${item.dateFormatted}"
            b.imgPickCheck.setImageResource(
                if (isSelected) R.drawable.ic_check_circle_selected else R.drawable.ic_check_circle_unselected
            )
            b.root.isActivated = isSelected
            b.imgPickCorner.isVisible = isSelected
            b.viewPickSeparator.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
            bindIcon(b, item)

            b.root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in files.indices) return@setOnClickListener
                val id = files[pos].id
                if (!selectedIds.add(id)) selectedIds.remove(id)
                notifyItemChanged(pos)
                updateHeader()
            }
        }

        private fun bindIcon(b: ItemPickFileBinding, item: ImportedFileItem) {
            val ext = item.extension.lowercase(Locale.ROOT)
            b.tvPickBadge.text = ext.uppercase(Locale.ROOT).ifEmpty { "FILE" }
            val (strokeRes, fillRes) = when (ext) {
                "pdf" -> R.color.file_red to R.color.file_red_fill
                "doc", "docx", "rtf", "odt", "pages" -> R.color.file_blue to R.color.file_blue_fill
                "zip", "rar", "7z", "tar", "gz" -> R.color.file_orange to R.color.file_orange_fill
                else -> R.color.file_green to R.color.file_green_fill
            }
            val ctx = b.root.context
            val strokeColor = ContextCompat.getColor(ctx, strokeRes)
            ImageViewCompat.setImageTintList(b.imgPickIconStroke, ColorStateList.valueOf(strokeColor))
            ImageViewCompat.setImageTintList(
                b.imgPickIconFill,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, fillRes))
            )
            ViewCompat.setBackgroundTintList(b.tvPickBadge, ColorStateList.valueOf(strokeColor))
        }
    }

    companion object {
        const val TAG = "LibraryPickerDialog"
        const val REQUEST_KEY = "library_picker_request"
        const val RESULT_IDS = "library_picker_ids"
        const val RESULT_TARGET = "library_picker_target"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_TARGET = "arg_target"
        private const val STATE_SELECTED = "state_selected"

        /** Files that can be sent to Dropbox / Google Drive */
        fun isUploadable(item: ImportedFileItem): Boolean =
            !item.id.startsWith("sample_") && item.passcode.isNullOrEmpty()

        fun newInstance(title: String, target: String): LibraryPickerDialog {
            return LibraryPickerDialog().apply {
                arguments = bundleOf(ARG_TITLE to title, ARG_TARGET to target)
            }
        }
    }
}
