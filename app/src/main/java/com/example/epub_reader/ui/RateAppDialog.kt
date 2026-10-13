package com.example.epub_reader.ui

import androidx.fragment.app.FragmentManager

object RateAppDialog {
    fun show(
        fragmentManager: FragmentManager,
        onDismissOutSize: () -> Unit,
        onRate: (Int) -> Unit
    ) {
        onRate(5)
    }
}
