package com.example.epub_reader.utils

import android.content.Context
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Private storage for files the app creates itself ("Sao chép" duplicates, "Zip" archives).
 * Imported files stay where they are (content:// with persisted permission);
 * created files live in filesDir/documents and are referenced with file:// URIs.
 */
object LocalFileStore {

    private const val DIR_NAME = "documents"

    fun documentsDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** true if [uri] points to a file inside our private documents folder */
    fun isLocalCopy(context: Context, uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "file") return false
        val path = uri.path ?: return false
        return File(path).absolutePath.startsWith(documentsDir(context).absolutePath)
    }

    fun deleteLocalCopy(context: Context, uri: Uri?) {
        if (!isLocalCopy(context, uri)) return
        val path = uri?.path ?: return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    /**
     * iOS style unique name: "ImportFiles.txt" -> "ImportFiles(1).txt" -> "ImportFiles(2).txt"
     */
    fun uniqueFileName(desired: String, existingNames: Collection<String>): String {
        if (desired !in existingNames) return desired
        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var index = 1
        while (true) {
            val candidate = "$base($index)$ext"
            if (candidate !in existingNames) return candidate
            index++
        }
    }

    /** Name that is safe to use on disk */
    fun safeDiskName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "file" }
    }

    fun newFileFor(context: Context, displayName: String, timestamp: Long): File {
        return File(documentsDir(context), "${timestamp}_${safeDiskName(displayName)}")
    }

    fun copyUriToFile(context: Context, source: Uri, dest: File): Boolean {
        return try {
            val input = context.contentResolver.openInputStream(source) ?: return false
            input.use { inStream ->
                FileOutputStream(dest).use { outStream -> inStream.copyTo(outStream) }
            }
            true
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }

    /**
     * Zips [sources] (display name -> uri) into [dest].
     * Returns true when at least one file was written.
     */
    fun zip(context: Context, sources: List<Pair<String, Uri>>, dest: File): Boolean {
        var written = 0
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(dest))).use { zos ->
                val usedEntryNames = mutableSetOf<String>()
                sources.forEach { (name, uri) ->
                    val input = try {
                        context.contentResolver.openInputStream(uri)
                    } catch (_: Exception) {
                        null
                    }
                    input?.use { stream ->
                        val entryName = uniqueFileName(safeDiskName(name), usedEntryNames)
                        usedEntryNames += entryName
                        zos.putNextEntry(ZipEntry(entryName))
                        stream.copyTo(zos)
                        zos.closeEntry()
                        written++
                    }
                }
            }
            if (written == 0) dest.delete()
            written > 0
        } catch (_: Exception) {
            dest.delete()
            false
        }
    }

    /** "2.3KB", "4.1MB" (like the iOS app) */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1_000L -> "${bytes}B"
            bytes < 1_000_000L -> String.format(Locale.US, "%.1fKB", bytes / 1_000.0)
            bytes < 1_000_000_000L -> String.format(Locale.US, "%.1fMB", bytes / 1_000_000.0)
            else -> String.format(Locale.US, "%.2fGB", bytes / 1_000_000_000.0)
        }
    }
}
