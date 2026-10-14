package com.example.epub_reader.utils

data class SearchResultSection(
    val sectionHeader: String,
    val chapterIndex: Int,
    val results: List<SearchResultItem>
)

data class SearchResultItem(
    val chapterIndex: Int,
    val chapterTitle: String,
    val snippetText: String,
    val queryKeyword: String
)
