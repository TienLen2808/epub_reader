package com.example.epub_reader.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.epub_reader.databinding.FragmentTransferBinding

class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOptionIcloud.setOnClickListener {
            Toast.makeText(requireContext(), "Nhập tệp từ Files / iCloud Drive", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptionWifi.setOnClickListener {
            Toast.makeText(requireContext(), "Nhập tệp từ PC qua Wi-Fi", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptionItunes.setOnClickListener {
            Toast.makeText(requireContext(), "Chia sẻ tệp tin iTunes / PC", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptionDropbox.setOnClickListener {
            Toast.makeText(requireContext(), "Đăng nhập Dropbox", Toast.LENGTH_SHORT).show()
        }

        binding.btnOptionDrive.setOnClickListener {
            Toast.makeText(requireContext(), "Đăng nhập Google Drive", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
