package com.example.epub_reader.ui.main

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.epub_reader.databinding.DialogFileActionOptionsBinding
import com.example.epub_reader.utils.ImportedFileItem

/**
 * iOS style alert (UIAlertController .alert) shown when tapping the (i) button of a file:
 * Đổi tên / di chuyển đến thư mục / Gửi/Mở ra... / Tải lên Google Drive / Sao chép / Xóa bỏ / Hủy
 *
 * The chosen action is returned with the Fragment Result API ([REQUEST_KEY]) instead of
 * constructor lambdas, so the dialog does not crash when the activity is recreated.
 */
class FileActionOptionsDialog : DialogFragment() {

    enum class Action { RENAME, MOVE, SEND_OPEN, UPLOAD_DRIVE, COPY, DELETE }

    private var _binding: DialogFileActionOptionsBinding? = null
    private val binding get() = _binding!!

    private val fileId: String
        get() = arguments?.getString(ARG_FILE_ID).orEmpty()

    private val fileName: String
        get() = arguments?.getString(ARG_FILE_NAME).orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
        }
    }

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

        // Clip the pressed state of the first / last button to the rounded corners
        binding.root.clipToOutline = true
        binding.tvDialogFileName.text = fileName

        binding.btnRename.setOnClickListener { deliver(Action.RENAME) }
        binding.btnMove.setOnClickListener { deliver(Action.MOVE) }
        binding.btnShare.setOnClickListener { deliver(Action.SEND_OPEN) }
        binding.btnUploadDrive.setOnClickListener { deliver(Action.UPLOAD_DRIVE) }
        binding.btnCopy.setOnClickListener { deliver(Action.COPY) }
        binding.btnDelete.setOnClickListener { deliver(Action.DELETE) }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(
                (270 * resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(IosAlertDialogs.IOS_DIM_AMOUNT)
        }
    }

    private fun deliver(action: Action) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_ACTION to action.name, RESULT_FILE_ID to fileId)
        )
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FileActionOptionsDialog"
        const val REQUEST_KEY = "file_action_options_request"
        const val RESULT_ACTION = "file_action_options_action"
        const val RESULT_FILE_ID = "file_action_options_file_id"

        private const val ARG_FILE_ID = "arg_file_id"
        private const val ARG_FILE_NAME = "arg_file_name"

        fun newInstance(item: ImportedFileItem): FileActionOptionsDialog {
            return FileActionOptionsDialog().apply {
                arguments = bundleOf(ARG_FILE_ID to item.id, ARG_FILE_NAME to item.fileName)
            }
        }
    }
}
