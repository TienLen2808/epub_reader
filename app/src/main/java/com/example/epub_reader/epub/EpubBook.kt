package com.example.epub_reader.epub

import android.graphics.Bitmap

data class EpubBook(
    val title: String,
    val author: String,
    val coverBitmap: Bitmap? = null,
    val chapters: List<EpubChapter> = emptyList(),
    val filePath: String? = null,
    val images: Map<String, ByteArray> = emptyMap()
)

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String,
    val contentHtml: String
)
