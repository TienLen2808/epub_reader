package com.example.epub_reader.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.View
import com.example.epub_reader.R
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.FileShareHelper
import com.example.epub_reader.utils.ImportedFileItem
import com.example.epub_reader.utils.LibraryImporter
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tiny HTTP server for "Chuyển Wifi" (like the Wi-Fi transfer of iOS document apps).
 *
 * While it runs, a computer on the same Wi-Fi opens http://<phone-ip>:<port> and can:
 *  - GET  /               upload page (res/raw/wifi_transfer_page.html)
 *  - GET  /api/files      JSON list of the library files
 *  - GET  /download/<id>  download a file
 *  - POST /upload         raw file body, header "X-File-Name" = encodeURIComponent(name)
 *
 * Uploaded files are copied into the library ("Các tập tin").
 * Files protected with a passcode are never listed nor served.
 */
class WifiTransferServer(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** Called on a background thread */
        fun onFileReceived(item: ImportedFileItem)
    }

    private val appContext = context.applicationContext

    // Texts of the web page, read from [context] (the activity) so they follow the language of the app
    private val pageTexts: Map<String, String> = mapOf(
        "TITLE" to context.getString(R.string.web_page_title),
        "DROP_TITLE" to context.getString(R.string.web_drop_title),
        "DROP_HINT" to context.getString(R.string.web_drop_hint),
        "CHOOSE" to context.getString(R.string.web_choose_files),
        "UPLOADING" to context.getString(R.string.web_uploading),
        "FILES_ON_PHONE" to context.getString(R.string.web_files_on_phone),
        "REFRESH" to context.getString(R.string.refresh),
        "LOADING" to context.getString(R.string.web_loading),
        "FOOTER" to context.getString(R.string.web_footer)
    )
    private val scriptTexts: String = JSONObject()
        .put("waiting", context.getString(R.string.web_waiting))
        .put("done", context.getString(R.string.web_done))
        .put("error", context.getString(R.string.web_error))
        .put("connectionLost", context.getString(R.string.web_connection_lost))
        .put("noFiles", context.getString(R.string.web_no_files))
        .put("download", context.getString(R.string.web_download))
        .put("cannotConnect", context.getString(R.string.web_cannot_connect))
        .toString()
    private val pageLocale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    private val pageLanguage: String = pageLocale.toLanguageTag()
    private val pageDirection: String =
        if (TextUtils.getLayoutDirectionFromLocale(pageLocale) == View.LAYOUT_DIRECTION_RTL) "rtl" else "ltr"
    private val executor: ExecutorService = Executors.newFixedThreadPool(4)
    private val clients: MutableSet<Socket> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var stopped = false

    @Volatile
    var port: Int = 0
        private set

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    /** Starts listening on [preferredPort] (or the next free port). Returns false when no port could be opened. */
    fun start(preferredPort: Int = DEFAULT_PORT): Boolean {
        if (stopped) return false
        if (isRunning) return true
        var opened: ServerSocket? = null
        for (candidate in preferredPort until preferredPort + 10) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate))
                opened = socket
                port = candidate
                break
            } catch (_: IOException) {
            }
        }
        val socket = opened ?: return false
        if (stopped) {
            // stop() was called while the port was being opened
            try {
                socket.close()
            } catch (_: IOException) {
            }
            return false
        }
        serverSocket = socket

        val acceptThread = Thread({
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: IOException) {
                    break
                }
                try {
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    closeQuietly(client)
                }
            }
        }, "WifiTransferServer")
        acceptThread.isDaemon = true
        acceptThread.start()
        return true
    }

    fun stop() {
        stopped = true
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        synchronized(clients) {
            clients.forEach { closeQuietly(it) }
            clients.clear()
        }
        executor.shutdownNow()
    }

    // region Request handling

    private class Request(
        val method: String,
        val path: String,
        val headers: Map<String, String>
    )

    private fun handleClient(client: Socket) {
        clients.add(client)
        try {
            client.soTimeout = SOCKET_TIMEOUT_MS
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val request = readRequest(input) ?: return

            when {
                request.method == "GET" && (request.path == "/" || request.path == "/index.html") ->
                    sendBytes(output, 200, "OK", "text/html; charset=utf-8", pageHtml())

                request.method == "GET" && request.path == "/api/files" ->
                    sendBytes(output, 200, "OK", "application/json; charset=utf-8", filesJson().toByteArray(Charsets.UTF_8))

                request.method == "GET" && request.path.startsWith("/download/") ->
                    sendFile(output, request.path.removePrefix("/download/"))

                request.method == "POST" && request.path == "/upload" ->
                    handleUpload(request, input, output)

                request.method == "GET" && request.path == "/favicon.ico" ->
                    writeHead(output, 204, "No Content", mapOf("Content-Length" to "0"))

                else ->
                    sendBytes(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found".toByteArray())
            }
            output.flush()
        } catch (_: Exception) {
            // Connection closed / timeout: nothing to do
        } finally {
            clients.remove(client)
            closeQuietly(client)
        }
    }

    /** Reads the request line + headers (until an empty line) */
    private fun readRequest(input: InputStream): Request? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val b = input.read()
            if (b == -1) return null
            buffer.write(b)
            matched = when (b) {
                '\r'.code -> if (matched == 2) 3 else 1
                '\n'.code -> if (matched == 1 || matched == 3) matched + 1 else 0
                else -> 0
            }
            if (buffer.size() > MAX_HEADER_BYTES) return null
        }

        val lines = buffer.toString("ISO-8859-1").split("\r\n")
        val requestLine = lines.firstOrNull()?.split(" ") ?: return null
        if (requestLine.size < 2) return null

        val method = requestLine[0].uppercase(Locale.ROOT)
        val rawPath = requestLine[1].substringBefore('?')
        val path = try {
            URLDecoder.decode(rawPath, "UTF-8")
        } catch (_: Exception) {
            rawPath
        }
        val headers = mutableMapOf<String, String>()
        lines.drop(1).forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
            }
        }
        return Request(method, path, headers)
    }

    private fun handleUpload(request: Request, input: InputStream, output: OutputStream) {
        val length = request.headers["content-length"]?.toLongOrNull()
        if (length == null || length < 0) {
            sendJson(output, 411, "Length Required", JSONObject().put("ok", false).put("error", "length"))
            return
        }
        if (request.headers["expect"]?.equals("100-continue", ignoreCase = true) == true) {
            output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            output.flush()
        }

        val rawName = request.headers["x-file-name"].orEmpty()
        val decodedName = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (_: Exception) {
            rawName
        }
        val name = decodedName.substringAfterLast('/').substringAfterLast('\\').trim().ifEmpty { "file" }

        val item = LibraryImporter.importStream(appContext, name, input, length)
        if (item == null) {
            sendJson(output, 500, "Internal Server Error", JSONObject().put("ok", false).put("error", "write"))
            return
        }
        try {
            listener.onFileReceived(item)
        } catch (_: Exception) {
        }
        sendJson(output, 200, "OK", JSONObject().put("ok", true).put("name", item.fileName))
    }

    private fun sendFile(output: OutputStream, id: String) {
        val item = visibleFiles().firstOrNull { it.id == id }
        val uri = item?.let { FileShareHelper.shareableUri(appContext, it) }
        val stream = try {
            item?.let { appContext.contentResolver.openInputStream(Uri.parse(it.fileUriStr)) }
        } catch (_: Exception) {
            null
        }
        if (item == null || stream == null) {
            sendBytes(output, 404, "Not Found", "text/plain; charset=utf-8", "Not found".toByteArray())
            return
        }

        stream.use { input ->
            val headers = linkedMapOf(
                "Content-Type" to FileShareHelper.mimeTypeOf(appContext, item, uri),
                "Content-Disposition" to contentDisposition(item.fileName),
                "Cache-Control" to "no-store"
            )
            val length = fileLength(item)
            if (length >= 0) headers["Content-Length"] = length.toString()
            writeHead(output, 200, "OK", headers)
            input.copyTo(output, 64 * 1024)
        }
    }

    // endregion

    // region Content

    private fun visibleFiles(): List<ImportedFileItem> =
        FileItemManager.instance.getFiles()
            .filterNot { it.id.startsWith("sample_") }
            .filter { it.passcode.isNullOrEmpty() }

    private fun filesJson(): String {
        val array = JSONArray()
        visibleFiles().forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.fileName)
                    .put("size", item.fileSizeFormatted)
                    .put("date", item.dateFormatted)
                    .put("ext", item.extension)
            )
        }
        return array.toString()
    }

    @Volatile
    private var cachedPage: ByteArray? = null

    private fun pageHtml(): ByteArray {
        cachedPage?.let { return it }
        var html = appContext.resources.openRawResource(R.raw.wifi_transfer_page).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        val appName = appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
        html = html.replace("{{APP_NAME}}", escapeHtml(appName))
            .replace("{{LANG}}", escapeHtml(pageLanguage))
            .replace("{{DIR}}", pageDirection)
            .replace("{{JS_TEXTS}}", scriptTexts)
        for ((key, value) in pageTexts) {
            html = html.replace("{{$key}}", escapeHtml(value))
        }
        val bytes = html.toByteArray(Charsets.UTF_8)
        cachedPage = bytes
        return bytes
    }

    private fun fileLength(item: ImportedFileItem): Long {
        val uri = try {
            Uri.parse(item.fileUriStr)
        } catch (_: Exception) {
            return -1L
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return -1L
            val file = File(path)
            return if (file.exists()) file.length() else -1L
        }
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    // endregion

    // region HTTP helpers

    private fun writeHead(output: OutputStream, status: Int, reason: String, headers: Map<String, String>) {
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
        headers.forEach { (key, value) -> head.append(key).append(": ").append(value).append("\r\n") }
        head.append("Connection: close\r\n\r\n")
        output.write(head.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun sendBytes(output: OutputStream, status: Int, reason: String, contentType: String, body: ByteArray) {
        writeHead(
            output,
            status,
            reason,
            linkedMapOf(
                "Content-Type" to contentType,
                "Content-Length" to body.size.toString(),
                "Cache-Control" to "no-store"
            )
        )
        output.write(body)
    }

    private fun sendJson(output: OutputStream, status: Int, reason: String, json: JSONObject) {
        sendBytes(output, status, reason, "application/json; charset=utf-8", json.toString().toByteArray(Charsets.UTF_8))
    }

    /** attachment; filename="ascii"; filename*=UTF-8''percent-encoded (Vietnamese names) */
    private fun contentDisposition(name: String): String {
        val ascii = name.map { ch -> if (ch.code in 0x20..0x7E && ch != '"' && ch != '\\') ch else '_' }.joinToString("")
        val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        return "attachment; filename=\"$ascii\"; filename*=UTF-8''$encoded"
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun closeQuietly(socket: Socket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    // endregion

    companion object {
        const val DEFAULT_PORT = 8080
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val MAX_HEADER_BYTES = 32 * 1024
    }
}
