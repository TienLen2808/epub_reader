package com.example.epub_reader.ui

import android.view.View
import android.widget.SeekBar
import com.example.epub_reader.databinding.DialogReaderProgressSheetBinding

class ReaderProgressPanelController(
    private val binding: DialogReaderProgressSheetBinding,
    private val onSeekToChapter: (Int) -> Unit,
    private val onPrevChapter: () -> Unit,
    private val onNextChapter: () -> Unit
) {

    private var isUserTracking = false

    fun initListeners() {
        binding.btnProgressPrev.setOnClickListener {
            onPrevChapter()
        }

        binding.btnProgressNext.setOnClickListener {
            onNextChapter()
        }

        binding.seekBarProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onSeekToChapter(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = false
                seekBar?.progress?.let { onSeekToChapter(it) }
            }
        })
    }

    fun updateData(chapterIndex: Int, totalChapters: Int, chapterTitle: String, globalPage: Int, globalTotalPages: Int) {
        val displayTitle = "${chapterIndex + 1}. ${chapterTitle.ifBlank { "Chương ${chapterIndex + 1}" }}"
        binding.tvProgressChapterTitle.text = displayTitle
        binding.tvProgressPages.text = "$globalPage/$globalTotalPages"

        if (!isUserTracking && totalChapters > 1) {
            binding.seekBarProgress.max = totalChapters - 1
            binding.seekBarProgress.progress = chapterIndex
        }
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
