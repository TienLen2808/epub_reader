package com.example.epub_reader.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Imports files into the app library ("Các tập tin"), like importing into the
 * Documents folder of the iOS app: every file is COPIED into the app storage
 * (filesDir/documents) so it keeps working when the source (cloud, USB, PC) is gone.
 *
 * All functions do disk IO -> call them from a background thread.
 */
object LibraryImporter {

    private val lock = Any()
    private var lastTimestamp = 0L

    /** Unique, increasing timestamp (also used for ids "file_<timestamp>") */
    fun nextTimestamp(): Long = synchronized(lock) {
        val now = System.currentTimeMillis()
        lastTimestamp = if (now > lastTimestamp) now else lastTimestamp + 1
        lastTimestamp
    }

    /** content:// or file:// uris (system picker, Dropbox, Google Drive...) */
    fun importUris(context: Context, uris: List<Uri>): List<ImportedFileItem> =
        uris.mapNotNull { importUri(context, it) }

    fun importUri(context: Context, uri: Uri): ImportedFileItem? {
        val name = queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "file"
        val timestamp = nextTimestamp()
        val dest = LocalFileStore.newFileFor(context, name, timestamp)
        if (!LocalFileStore.copyUriToFile(context, uri, dest)) return null
        return register(dest, name, timestamp)
    }

    /** A file on disk (USB folder). The source is deleted after a successful copy when [deleteSource] is true. */
    fun importLocalFile(context: Context, source: File, deleteSource: Boolean): ImportedFileItem? {
        val timestamp = nextTimestamp()
        val dest = LocalFileStore.newFileFor(context, source.name, timestamp)
        try {
            source.inputStream().use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {
            dest.delete()
            return null
        }
        val item = register(dest, source.name, timestamp)
        if (deleteSource) {
            try {
                source.delete()
            } catch (_: Exception) {
            }
        }
        return item
    }

    /** Exactly [length] bytes from [input] (Wi-Fi upload) */
    fun importStream(context: Context, displayName: String, input: InputStream, length: Long): ImportedFileItem? {
        val timestamp = nextTimestamp()
        val dest = LocalFileStore.newFileFor(context, displayName, timestamp)
        try {
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read == -1) throw IOException("Unexpected end of stream")
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        } catch (_: Exception) {
            dest.delete()
            return null
        }
        return register(dest, displayName, timestamp)
    }

    /** Adds the copied [file] to the library list with an iOS style unique name ("Name(1).ext") */
    private fun register(file: File, displayName: String, timestamp: Long): ImportedFileItem = synchronized(lock) {
        val existingNames = FileItemManager.instance.getFiles().map { it.fileName }
        val name = LocalFileStore.uniqueFileName(cleanName(displayName), existingNames)
        val extension = if (name.contains('.')) {
            name.substringAfterLast('.').lowercase(Locale.ROOT)
        } else {
            "epub"
        }
        val item = ImportedFileItem(
            id = "file_$timestamp",
            fileName = name,
            fileUriStr = Uri.fromFile(file).toString(),
            fileSizeFormatted = LocalFileStore.formatFileSize(file.length()),
            dateFormatted = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date()),
            extension = extension
        )
        FileItemManager.instance.addFile(item)
        item
    }

    private fun cleanName(name: String): String {
        val cleaned = name.substringAfterLast('/')
            .replace(Regex("[\\u0000-\\u001F\\\\:*?\"<>|]"), "_")
            .trim()
        return cleaned.ifEmpty { "file" }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    // region USB ("Chia sẻ tệp tin iTunes")

    private const val USB_FOLDER = "Import"
    private const val USB_README = "HUONG_DAN.txt"

    /**
     * Folder the user copies files into from a computer (USB / MTP):
     * Android/data/<package>/files/Import – readable by the app without any permission.
     */
    fun usbInboxDir(context: Context): File? {
        val dir = context.getExternalFilesDir(USB_FOLDER) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Help file of the USB folder. [text] is read from an activity context so it is
     * in the language chosen in the app; the file is rewritten when the language changes.
     */
    fun writeUsbReadme(context: Context, text: String): File? {
        val dir = usbInboxDir(context) ?: return null
        val readme = File(dir, USB_README)
        return try {
            if (!readme.exists() || readme.readText() != text) readme.writeText(text)
            readme
        } catch (_: Exception) {
            null
        }
    }

    fun isUsbReadme(file: File): Boolean = file.name.equals(USB_README, ignoreCase = true)

    /** "Android/data/com.example.epub_reader/files/Import" (path shown on the computer) */
    @Suppress("DEPRECATION")
    fun usbInboxDisplayPath(context: Context): String {
        val dir = usbInboxDir(context) ?: return "Android/data/${context.packageName}/files/$USB_FOLDER"
        val root = try {
            android.os.Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            ""
        }
        val path = dir.absolutePath
        return if (root.isNotEmpty() && path.startsWith(root)) path.removePrefix(root).trimStart('/') else path
    }

    // endregion
}
