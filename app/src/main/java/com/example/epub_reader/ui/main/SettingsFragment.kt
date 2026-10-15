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

        binding.btnVipBadge.setOnClickListener {
            Toast.makeText(requireContext(), "Màn hình VIP / Pro Membership", Toast.LENGTH_SHORT).show()
        }

        binding.switchPasscode.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "Đã bật Khóa Passcode" else "Đã tắt Khóa Passcode"
            Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
        }

        binding.btnProFeatures.setOnClickListener {
            Toast.makeText(requireContext(), "Tính năng chuyên nghiệp VIP", Toast.LENGTH_SHORT).show()
        }

        binding.btnRestorePurchase.setOnClickListener {
            Toast.makeText(requireContext(), "Đang khôi phục giao dịch mua hàng...", Toast.LENGTH_SHORT).show()
        }

        binding.btnRateUs.setOnClickListener {
            Toast.makeText(requireContext(), "Cảm ơn bạn đã đánh giá 5 sao!", Toast.LENGTH_SHORT).show()
        }

        binding.btnFeedback.setOnClickListener {
            Toast.makeText(requireContext(), "Gửi ý kiến phản hồi", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareFriend.setOnClickListener {
            Toast.makeText(requireContext(), "Chia sẻ ứng dụng tới bạn bè", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
