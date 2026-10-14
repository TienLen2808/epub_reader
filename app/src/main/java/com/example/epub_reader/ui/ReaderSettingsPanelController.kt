package com.example.epub_reader.ui

import android.view.View
import com.example.epub_reader.ReaderActivity
import com.example.epub_reader.databinding.DialogReaderSettingsSheetBinding

class ReaderSettingsPanelController(
    private val binding: DialogReaderSettingsSheetBinding,
    private val onFontSizeChanged: (Int) -> Unit,
    private val onThemeChanged: (ReaderActivity.ThemeMode) -> Unit,
    private val onLineSpacingChanged: (Float) -> Unit
) {

    private var currentSpacingVal = 10

    fun initListeners(currentFontSize: Int, currentSpacingRatio: Float) {
        updateFontSizeDisplay(currentFontSize)
        currentSpacingVal = ((currentSpacingRatio - 1.0f) * 10 + 6).toInt().coerceIn(4, 20)
        binding.tvSpacingValue.text = "$currentSpacingVal"

        binding.btnFontDecrease.setOnClickListener {
            val newSize = (binding.tvFontSizeValue.text.toString().toIntOrNull() ?: 18) - 1
            if (newSize >= 12) {
                updateFontSizeDisplay(newSize)
                onFontSizeChanged(newSize)
            }
        }

        binding.btnFontIncrease.setOnClickListener {
            val newSize = (binding.tvFontSizeValue.text.toString().toIntOrNull() ?: 18) + 1
            if (newSize <= 32) {
                updateFontSizeDisplay(newSize)
                onFontSizeChanged(newSize)
            }
        }

        binding.btnLineSpacing.setOnClickListener {
            binding.layoutMainSettings.visibility = View.GONE
            binding.layoutSpacingSettings.visibility = View.VISIBLE
        }

        binding.btnBackToMainSettings.setOnClickListener {
            binding.layoutSpacingSettings.visibility = View.GONE
            binding.layoutMainSettings.visibility = View.VISIBLE
        }

        binding.btnSpacingDecrease.setOnClickListener {
            if (currentSpacingVal > 4) {
                currentSpacingVal--
                binding.tvSpacingValue.text = "$currentSpacingVal"
                val newRatio = 1.0f + (currentSpacingVal - 6) * 0.1f
                onLineSpacingChanged(newRatio)
            }
        }

        binding.btnSpacingIncrease.setOnClickListener {
            if (currentSpacingVal < 20) {
                currentSpacingVal++
                binding.tvSpacingValue.text = "$currentSpacingVal"
                val newRatio = 1.0f + (currentSpacingVal - 6) * 0.1f
                onLineSpacingChanged(newRatio)
            }
        }

        binding.btnThemeWhite.setOnClickListener {
            onThemeChanged(ReaderActivity.ThemeMode.LIGHT)
        }

        binding.btnThemeBlue.setOnClickListener {
            onThemeChanged(ReaderActivity.ThemeMode.LIGHT)
        }

        binding.btnThemeSepia.setOnClickListener {
            onThemeChanged(ReaderActivity.ThemeMode.SEPIA)
        }

        binding.btnThemeGreen.setOnClickListener {
            onThemeChanged(ReaderActivity.ThemeMode.SEPIA)
        }

        binding.btnThemeDark.setOnClickListener {
            onThemeChanged(ReaderActivity.ThemeMode.DARK)
        }
    }

    fun updateFontSizeDisplay(size: Int) {
        binding.tvFontSizeValue.text = "$size"
    }

    fun isVisible(): Boolean {
        return binding.root.visibility == View.VISIBLE
    }

    fun toggle() {
        if (isVisible()) {
            hide()
        } else {
            show()
        }
    }

    fun show() {
        if (isVisible()) return
        binding.layoutSpacingSettings.visibility = View.GONE
        binding.layoutMainSettings.visibility = View.VISIBLE
        binding.root.visibility = View.VISIBLE
        binding.root.alpha = 0f
        binding.root.translationY = 50f
        binding.root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .start()
    }

    fun hide() {
        if (!isVisible()) return
        binding.root.animate()
            .alpha(0f)
            .translationY(50f)
            .setDuration(200)
            .withEndAction {
                binding.root.visibility = View.GONE
            }
            .start()
    }
}
