package com.example.epub_reader.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/**
 * Builds share / upload intents for library files.
 * content:// uris are shared as is, files created by the app (file://) go through [AppFileProvider].
 */
object FileShareHelper {

    const val DROPBOX_PACKAGE = "com.dropbox.android"
    const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

    fun shareableUri(context: Context, item: ImportedFileItem): Uri? {
        val uri = try {
            Uri.parse(item.fileUriStr)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "file") return uri
        val path = uri.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun mimeTypeOf(context: Context, item: ImportedFileItem, uri: Uri? = null): String {
        if (uri != null && uri.scheme == "content") {
            val type = try {
                context.contentResolver.getType(uri)
            } catch (_: Exception) {
                null
            }
            if (!type.isNullOrBlank() && type != "application/octet-stream") return type
        }
        return when (val ext = item.extension.lowercase(Locale.ROOT)) {
            "epub" -> "application/epub+zip"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
    }

    /**
     * ACTION_SEND (1 file) / ACTION_SEND_MULTIPLE (several files) with read permission.
     * Returns null when none of the files can be shared.
     */
    fun buildSendIntent(context: Context, items: List<ImportedFileItem>, targetPackage: String? = null): Intent? {
        val pairs = items.mapNotNull { item -> shareableUri(context, item)?.let { item to it } }
        if (pairs.isEmpty()) return null
        val uris = ArrayList(pairs.map { it.second })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeOf(context, pairs.first().first, uris.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (targetPackage != null) intent.setPackage(targetPackage)
        return intent
    }
}
