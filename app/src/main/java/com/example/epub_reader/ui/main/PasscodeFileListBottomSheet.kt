package com.example.epub_reader.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogPasscodeFileListBinding
import com.example.epub_reader.databinding.ItemPasscodeFileBinding
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.ImportedFileItem
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PasscodeFileListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogPasscodeFileListBinding? = null
    private val binding get() = _binding!!

    private var fileList = mutableListOf<ImportedFileItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPasscodeFileListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val displayMetrics = resources.displayMetrics
        val targetHeight = (displayMetrics.heightPixels * 0.85).toInt()
        bottomSheet.layoutParams.height = targetHeight
        bottomSheet.requestLayout()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = targetHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())

        loadFiles()
    }

    private fun loadFiles() {
        fileList = FileItemManager.instance.getFiles().toMutableList()
        binding.rvFiles.adapter = PasscodeFileAdapter()
    }

    inner class PasscodeFileAdapter : RecyclerView.Adapter<PasscodeFileAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemPasscodeFileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPasscodeFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = fileList[position]
            holder.binding.tvFileName.text = item.fileName

            val isLocked = !item.passcode.isNullOrEmpty()
            if (isLocked) {
                holder.binding.imgLockStatus.setImageResource(android.R.drawable.ic_lock_lock)
                holder.binding.imgLockStatus.setColorFilter(android.graphics.Color.parseColor("#D84315")) // Red-Orange
            } else {
                holder.binding.imgLockStatus.setImageResource(android.R.drawable.ic_menu_info_details)
                holder.binding.imgLockStatus.setColorFilter(android.graphics.Color.parseColor("#CCCCCC")) // Gray
            }

            holder.itemView.setOnClickListener {
                if (isLocked) {
                    val dialog = PasscodeEntryDialog(PasscodeEntryDialog.Mode.REMOVE) { code ->
                        if (code == item.passcode) {
                            val updated = item.copy(passcode = null)
                            FileItemManager.instance.addFile(updated)
                            loadFiles()
                            Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.passcode_removed), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.wrong_password_exclamation), Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.show(parentFragmentManager, PasscodeEntryDialog.TAG)
                } else {
                    val dialog = PasscodeEntryDialog(PasscodeEntryDialog.Mode.CREATE) { code ->
                        val updated = item.copy(passcode = code)
                        FileItemManager.instance.addFile(updated)
                        loadFiles()
                        Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.passcode_set), Toast.LENGTH_SHORT).show()
                    }
                    dialog.show(parentFragmentManager, PasscodeEntryDialog.TAG)
                }
            }
        }

        override fun getItemCount(): Int = fileList.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PasscodeFileListBottomSheet"
    }
}
