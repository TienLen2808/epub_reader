package com.example.epub_reader.utils

import androidx.core.content.FileProvider

/**
 * FileProvider for files created by the app itself (duplicated files, zip archives),
 * so they can be shared / opened by other apps.
 * A subclass avoids manifest-merge conflicts with SDKs that also declare FileProvider.
 */
class AppFileProvider : FileProvider()
