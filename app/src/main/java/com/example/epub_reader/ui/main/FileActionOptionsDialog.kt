package com.example.epub_reader.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.epub_reader.databinding.DialogFileActionOptionsBinding
import com.example.epub_reader.utils.ImportedFileItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FileActionOptionsDialog(
    private val item: ImportedFileItem,
    private val onRename: (ImportedFileItem) -> Unit,
    private val onShare: (ImportedFileItem) -> Unit,
    private val onDelete: (ImportedFileItem) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogFileActionOptionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFileActionOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDialogFileName.text = item.fileName

        binding.btnRename.setOnClickListener {
            onRename(item)
            dismiss()
        }

        binding.btnMove.setOnClickListener {
            dismiss()
        }

        binding.btnShare.setOnClickListener {
            onShare(item)
            dismiss()
        }

        binding.btnUploadDrive.setOnClickListener {
            onShare(item)
            dismiss()
        }

        binding.btnCopy.setOnClickListener {
            dismiss()
        }

        binding.btnDelete.setOnClickListener {
            onDelete(item)
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FileActionOptionsDialog"
    }
}
