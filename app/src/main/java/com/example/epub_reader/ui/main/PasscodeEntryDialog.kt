package com.example.epub_reader.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogPasscodeEntryBinding

class PasscodeEntryDialog(
    private val mode: Mode,
    private val onPasscodeEntered: (String) -> Unit
) : DialogFragment() {

    enum class Mode {
        CREATE, VERIFY, REMOVE
    }

    private var _binding: DialogPasscodeEntryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPasscodeEntryBinding.inflate(inflater, container, false)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(
            when (mode) {
                Mode.CREATE -> R.string.passcode_create_title
                Mode.VERIFY -> R.string.passcode_verify_title
                Mode.REMOVE -> R.string.passcode_remove_title
            }
        )

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            val code = binding.etPasscode.text.toString().trim()
            if (code.length == 4) {
                binding.tvError.visibility = View.GONE
                onPasscodeEntered(code)
                dismiss()
            } else {
                binding.tvError.text = getString(R.string.passcode_need_4_digits)
                binding.tvError.visibility = View.VISIBLE
            }
        }
    }

    fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PasscodeEntryDialog"
    }
}
