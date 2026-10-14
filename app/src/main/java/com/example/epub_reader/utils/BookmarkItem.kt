package com.example.epub_reader.utils

data class BookmarkItem(
    val id: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndexInChapter: Int,
    val globalPage: Int,
    val globalTotalPages: Int,
    val snippetText: String,
    val timestampFormatted: String
)
