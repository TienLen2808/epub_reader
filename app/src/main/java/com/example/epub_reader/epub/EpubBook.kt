package com.example.epub_reader.epub

import android.graphics.Bitmap

data class EpubBook(
    val title: String,
    val author: String,
    val coverBitmap: Bitmap? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val filePath: String? = null,
    val images: Map<String, ByteArray> = emptyMap(),
    /** Raw <dc:language> of the OPF ("en", "vi-VN", "zh-Hant"...), null when missing */
    val language: String? = null
)

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val contentHtml: String
)
