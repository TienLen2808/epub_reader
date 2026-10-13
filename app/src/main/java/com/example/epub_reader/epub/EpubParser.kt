package com.example.epub_reader.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object EpubParser {

    private const val TAG = "EpubParser"

    fun parse(context: Context, uri: Uri): EpubBook? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val entries = readZipEntries(inputStream)
            inputStream.close()

            val containerBytes = entries["META-INF/container.xml"]
                ?: entries["meta-inf/container.xml"] ?: return null
            val opfPath = parseOpfPath(containerBytes) ?: return null
            Log.d(TAG, "OPF Path: $opfPath")

            val opfBytes = entries[opfPath] ?: entries[opfPath.lowercase()] ?: return null
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""

            val opfDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(opfBytes))
            opfDoc.documentElement.normalize()

            val title = getElementText(opfDoc.getElementsByTagName("dc:title"))
                .ifEmpty { getElementText(opfDoc.getElementsByTagName("title")) }
                .ifEmpty { "Unknown Title" }

            val author = getElementText(opfDoc.getElementsByTagName("dc:creator"))
                .ifEmpty { getElementText(opfDoc.getElementsByTagName("creator")) }
                .ifEmpty { "Unknown Author" }

            val manifestMap = mutableMapOf<String, String>()
            val manifestMediaTypeMap = mutableMapOf<String, String>()
            val manifestNodes = opfDoc.getElementsByTagName("item")
            var coverItemId: String? = null

            for (i in 0 until manifestNodes.length) {
                val node = manifestNodes.item(i) as? Element ?: continue
                val id = node.getAttribute("id")
                val href = node.getAttribute("href")
                val mediaType = node.getAttribute("media-type")
                val properties = node.getAttribute("properties")

                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifestMap[id] = href
                    manifestMediaTypeMap[id] = mediaType
                    if (properties.contains("cover-image", ignoreCase = true) || id.contains("cover", ignoreCase = true)) {
                        coverItemId = id
                    }
                }
            }

            if (coverItemId == null) {
                val metaNodes = opfDoc.getElementsByTagName("meta")
                for (i in 0 until metaNodes.length) {
                    val node = metaNodes.item(i) as? Element ?: continue
                    if (node.getAttribute("name") == "cover") {
                        coverItemId = node.getAttribute("content")
                        break
                    }
                }
            }

            var coverBitmap: Bitmap? = null
            coverItemId?.let { id ->
                manifestMap[id]?.let { coverHref ->
                    val fullCoverPath = normalizePath(opfDir + coverHref)
                    val imgBytes = entries[fullCoverPath] ?: entries[fullCoverPath.lowercase()]
                    imgBytes?.let { bytes ->
                        coverBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            }

            val spineList = mutableListOf<String>()
            val spineNodes = opfDoc.getElementsByTagName("itemref")
            for (i in 0 until spineNodes.length) {
                val node = spineNodes.item(i) as? Element ?: continue
                val idref = node.getAttribute("idref")
                if (idref.isNotEmpty()) {
                    spineList.add(idref)
                }
            }

            val chapters = mutableListOf<EpubChapter>()
            spineList.forEachIndexed { index, idref ->
                val href = manifestMap[idref] ?: return@forEachIndexed
                val fullChapterPath = normalizePath(opfDir + href)
                val chapterBytes = entries[fullChapterPath] ?: entries[fullChapterPath.lowercase()]
                val contentHtml = if (chapterBytes != null) String(chapterBytes, Charsets.UTF_8) else ""
                val chapterTitle = extractChapterTitle(contentHtml, "Chapter ${index + 1}")

                chapters.add(
                    EpubChapter(
                        id = idref,
                        title = chapterTitle,
                        href = href,
                        contentHtml = contentHtml
                    )
                )
            }

            val images = entries.filterKeys { key ->
                val lower = key.lowercase()
                lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")
            }

            EpubBook(
                title = title,
                author = author,
                coverBitmap = coverBitmap,
                chapters = chapters,
                images = images
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPUB file", e)
            null
        }
    }

    private fun readZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val buffer = ByteArray(8192)

        while (entry != null) {
            if (!entry.isDirectory) {
                val baos = ByteArrayOutputStream()
                var len: Int
                while (zip.read(buffer).also { len = it } > 0) {
                    baos.write(buffer, 0, len)
                }
                map[entry.name] = baos.toByteArray()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return map
    }

    private fun parseOpfPath(containerXmlBytes: ByteArray): String? {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(containerXmlBytes))
            val rootfile = doc.getElementsByTagName("rootfile").item(0) as? Element
            rootfile?.getAttribute("full-path")
        } catch (e: Exception) {
            null
        }
    }

    private fun getElementText(nodeList: NodeList): String {
        return if (nodeList.length > 0) {
            nodeList.item(0).textContent.trim()
        } else {
            ""
        }
    }

    private fun extractChapterTitle(htmlContent: String, fallback: String): String {
        try {
            val titleRegex = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            val match = titleRegex.find(htmlContent)
            if (match != null && match.groupValues[1].isNotBlank()) {
                return match.groupValues[1].trim()
            }
            val h1Regex = Regex("<h1.*?>(.*?)</h1>", RegexOption.IGNORE_CASE)
            val h1Match = h1Regex.find(htmlContent)
            if (h1Match != null && h1Match.groupValues[1].isNotBlank()) {
                return h1Match.groupValues[1].replace(Regex("<.*?>"), "").trim()
            }
        } catch (_: Exception) {}
        return fallback
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/")
        val stack = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            } else {
                stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
