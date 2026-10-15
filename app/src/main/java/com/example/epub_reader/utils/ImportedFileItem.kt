package com.example.epub_reader.utils

data class ImportedFileItem(
    val id: String,
    val fileName: String,
    val fileUriStr: String,
    val fileSizeFormatted: String,
    val dateFormatted: String,
    val extension: String,
    val coverBase64: String? = null
)
