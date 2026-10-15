package com.example.epub_reader.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.epub_reader.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchPasscode.visibility = View.GONE
        val imgNext = android.widget.ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(android.graphics.Color.parseColor("#CCCCCC"))
            val size = (20 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size)
        }
        (binding.switchPasscode.parent as android.view.ViewGroup).addView(imgNext)
        (binding.switchPasscode.parent as android.view.View).setBackgroundResource(com.example.epub_reader.R.drawable.bg_bottom_sheet_rounded)
        (binding.switchPasscode.parent as android.view.View).setOnClickListener {
            val sheet = PasscodeFileListBottomSheet()
            sheet.show(parentFragmentManager, PasscodeFileListBottomSheet.TAG)
        }

        binding.btnSettingLanguage.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.example.epub_reader.ui.LanguageActivity::class.java)
            startActivity(intent)
        }

        binding.btnRateUs.setOnClickListener {
            Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.thanks_for_5_stars), Toast.LENGTH_SHORT).show()
        }

        binding.btnFeedback.setOnClickListener {
            Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.send_feedback), Toast.LENGTH_SHORT).show()
        }

        binding.btnShareFriend.setOnClickListener {
            Toast.makeText(requireContext(), getString(com.example.epub_reader.R.string.share_app_with_friends), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
