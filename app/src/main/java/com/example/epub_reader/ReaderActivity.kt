package com.example.epub_reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.epub_reader.databinding.ActivityReaderBinding
import com.example.epub_reader.databinding.DialogReaderSettingsBinding
import com.example.epub_reader.epub.BookHolder
import com.example.epub_reader.epub.EpubBook
import com.example.epub_reader.utils.PreferencesHelper
import kotlin.math.abs

class ReaderActivity : LocalizeBaseActivity() {

    private lateinit var binding: ActivityReaderBinding
    private var book: EpubBook? = null

    private var currentChapterIndex = 0
    private var currentPageIndex = 0
    private var totalPagesInChapter = 1
    private var isPendingGoToLastPage = false

    private var fontSizeSp = 18
    private var readerTheme = ThemeMode.LIGHT

    enum class ThemeMode {
        LIGHT, SEPIA, DARK
    }

    private var areToolbarsVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.readerRoot)

        book = BookHolder.currentBook
        if (book == null || book?.chapters.isNullOrEmpty()) {
            Toast.makeText(this, "No book content available to read", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val savedIndex = PreferencesHelper.getInstance().getInt("read_pos_${book?.title}", 0)
        if (savedIndex in 0 until (book?.chapters?.size ?: 0)) {
            currentChapterIndex = savedIndex
        }

        fontSizeSp = PreferencesHelper.getInstance().getInt("reader_font_size", 18)
        val savedThemeStr = PreferencesHelper.getInstance().getString("reader_theme") ?: "LIGHT"
        readerTheme = try {
            ThemeMode.valueOf(savedThemeStr)
        } catch (_: Exception) {
            ThemeMode.LIGHT
        }

        setupWebView()
        setupListeners()
        setupSwipeGesture()
        setupToolbarOverlay()
        loadCurrentChapter()
    }

    private fun setupToolbarOverlay() {
        binding.headerToolbar.post {
            val h = binding.headerToolbar.height.toFloat().let { if (it > 0) it else 200f }
            binding.headerToolbar.translationY = -h
        }
        binding.bottomNav.post {
            val h = binding.bottomNav.height.toFloat().let { if (it > 0) it else 200f }
            binding.bottomNav.translationY = h
        }
    }

    private fun toggleToolbars() {
        if (areToolbarsVisible) {
            hideToolbars()
        } else {
            showToolbars()
        }
    }

    private fun showToolbars() {
        areToolbarsVisible = true
        binding.headerToolbar.animate()
            .translationY(0f)
            .setDuration(250)
            .start()
        binding.bottomNav.animate()
            .translationY(0f)
            .setDuration(250)
            .start()
    }

    private fun hideToolbars() {
        if (!areToolbarsVisible) return
        areToolbarsVisible = false
        val headerH = binding.headerToolbar.height.toFloat().let { if (it > 0) it else 200f }
        val bottomH = binding.bottomNav.height.toFloat().let { if (it > 0) it else 200f }

        binding.headerToolbar.animate()
            .translationY(-headerH)
            .setDuration(250)
            .start()
        binding.bottomNav.animate()
            .translationY(bottomH)
            .setDuration(250)
            .start()
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        binding.webViewContent.settings.javaScriptEnabled = true
        binding.webViewContent.settings.domStorageEnabled = true
        binding.webViewContent.settings.builtInZoomControls = false
        binding.webViewContent.settings.displayZoomControls = false
        binding.webViewContent.isVerticalScrollBarEnabled = false
        binding.webViewContent.isHorizontalScrollBarEnabled = false

        binding.webViewContent.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.webViewContent.postDelayed({
                    calculatePageCountAndRender()
                }, 200)
            }
        }
    }

    private fun calculatePageCountAndRender() {
        binding.webViewContent.evaluateJavascript("getPageCount();") { resultStr ->
            val count = resultStr?.toIntOrNull() ?: 1
            totalPagesInChapter = if (count < 1) 1 else count

            if (isPendingGoToLastPage) {
                isPendingGoToLastPage = false
                currentPageIndex = totalPagesInChapter - 1
            } else if (currentPageIndex >= totalPagesInChapter) {
                currentPageIndex = totalPagesInChapter - 1
            }

            updatePagePosition()
            binding.progressBar.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 60
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        hideToolbars()
                        if (diffX < 0) {
                            goToNextPage()
                        } else {
                            goToPrevPage()
                        }
                        return true
                    }
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = binding.webViewContent.width
                if (width <= 0) return false
                val x = e.x
                val leftZone = width * 0.30f
                val rightZone = width * 0.70f

                if (x < leftZone) {
                    hideToolbars()
                    goToPrevPage()
                    return true
                } else if (x > rightZone) {
                    hideToolbars()
                    goToNextPage()
                    return true
                } else {
                    toggleToolbars()
                    return true
                }
            }
        })

        binding.webViewContent.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPrevChapter.setOnClickListener {
            goToPrevPage()
        }

        binding.btnNextChapter.setOnClickListener {
            goToNextPage()
        }

        binding.btnToc.setOnClickListener {
            showTableOfContentsDialog()
        }

        binding.btnReaderSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun goToNextPage() {
        val chaptersCount = book?.chapters?.size ?: 0
        if (currentPageIndex < totalPagesInChapter - 1) {
            currentPageIndex++
            updatePagePosition()
        } else if (currentChapterIndex < chaptersCount - 1) {
            currentChapterIndex++
            currentPageIndex = 0
            loadCurrentChapter()
        }
    }

    private fun goToPrevPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--
            updatePagePosition()
        } else if (currentChapterIndex > 0) {
            currentChapterIndex--
            isPendingGoToLastPage = true
            loadCurrentChapter()
        }
    }

    private fun loadCurrentChapter() {
        val currentBook = book ?: return
        val chapters = currentBook.chapters
        if (currentChapterIndex !in chapters.indices) return

        val chapter = chapters[currentChapterIndex]
        binding.progressBar.visibility = View.VISIBLE

        val displayTitle = if (chapter.title.isNotBlank()) chapter.title else currentBook.title
        binding.tvBookTitle.text = displayTitle
        binding.tvSmallChapterHeader.text = displayTitle

        val htmlContent = formatChapterHtml(chapter.contentHtml, chapter.href)
        binding.webViewContent.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)

        PreferencesHelper.getInstance().putInt("read_pos_${currentBook.title}", currentChapterIndex)
        updateThemeColors()
    }

    private fun updatePagePosition() {
        val chaptersCount = book?.chapters?.size ?: 0
        binding.webViewContent.evaluateJavascript("scrollToPage($currentPageIndex);", null)

        val pageDisplay = "${currentPageIndex + 1}/$totalPagesInChapter"
        val chapterDisplay = "Ch. ${currentChapterIndex + 1}/$chaptersCount"
        binding.tvChapterProgress.text = "$pageDisplay  •  $chapterDisplay"
        binding.tvSmallPageProgress.text = pageDisplay

        binding.btnPrevChapter.isEnabled = !(currentChapterIndex == 0 && currentPageIndex == 0)
        binding.btnNextChapter.isEnabled = !(currentChapterIndex == chaptersCount - 1 && currentPageIndex == totalPagesInChapter - 1)
    }

    private fun formatChapterHtml(bodyHtml: String, chapterHref: String): String {
        val (bgColorHex, textColorHex, headingColorHex) = when (readerTheme) {
            ThemeMode.LIGHT -> Triple("#FFFFFF", "#1A1A1A", "#111111")
            ThemeMode.SEPIA -> Triple("#FBF0D9", "#5F4B32", "#3D2E1E")
            ThemeMode.DARK -> Triple("#121212", "#E0E0E0", "#FFFFFF")
        }

        var cleaned = inlineImagesInHtml(bodyHtml, chapterHref)
        if (cleaned.contains("<body", ignoreCase = true)) {
            cleaned = cleaned.replace(Regex("<body.*?>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</body>", RegexOption.IGNORE_CASE), "")
        }

        // Convert hyperlinks into plain text
        cleaned = cleaned.replace(Regex("<a\\b[^>]*>", RegexOption.IGNORE_CASE), "<span>")
            .replace(Regex("</a>", RegexOption.IGNORE_CASE), "</span>")

        // Strip numeric prefixes like "112. ", "113. " at the start of list items or tags
        cleaned = cleaned.replace(Regex("(<li[^>]*>\\s*)\\d+\\.\\s*", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("(<p[^>]*>\\s*)\\d+\\.\\s*", RegexOption.IGNORE_CASE), "$1")

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * {
                        box-sizing: border-box;
                        -webkit-tap-highlight-color: transparent;
                    }
                    html, body {
                        width: 100vw;
                        height: 100vh;
                        margin: 0;
                        padding: 0;
                        overflow: hidden;
                        background-color: $bgColorHex;
                        color: $textColorHex;
                        font-family: -apple-system, sans-serif;
                        font-size: ${fontSizeSp}px;
                        line-height: 1.2;
                    }
                    #reader-viewport {
                        width: 100vw;
                        height: 100vh;
                        margin: 0;
                        padding: 0 16px;
                        overflow: hidden;
                        position: relative;
                        box-sizing: border-box;
                    }
                    #reader-content {
                        box-sizing: border-box;
                        height: calc(100vh - 16px);
                        margin-top: 8px;
                        margin-bottom: 8px;
                        padding: 0;
                        column-width: calc(100vw - 32px);
                        column-gap: 32px;
                        column-fill: auto;
                        word-wrap: break-word;
                        word-break: break-word;
                        transition: transform 0.25s ease-out;
                        transform: translateX(0vw);
                    }
                    #reader-content * {
                        max-width: calc(100vw - 32px) !important;
                        box-sizing: border-box;
                    }
                    #reader-content h1, 
                    #reader-content h2, 
                    #reader-content h3, 
                    #reader-content h4, 
                    #reader-content h5, 
                    #reader-content h6, 
                    #reader-content p, 
                    #reader-content div {
                        margin-left: 0;
                        margin-right: 0;
                    }
                    #reader-content ol, 
                    #reader-content ul, 
                    #reader-content li {
                        list-style: none !important;
                        list-style-type: none !important;
                        padding-left: 4px !important;
                        margin-top: 0.4em !important;
                        margin-left: 0 !important;
                        margin-right: 0 !important;
                    }
                    img {
                        max-width: calc(100vw - 32px) !important;
                        max-height: calc(100vh - 48px) !important;
                        height: auto !important;
                        object-fit: contain;
                        display: block;
                        margin: 8px auto;
                    }
                    p {
                        margin-bottom: 0.8em;
                        text-align: justify;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: $headingColorHex;
                        font-weight: bold !important;
                        font-size: 1.15em !important;
                        margin-top: 0.2em !important;
                        margin-bottom: 0.0em !important;
                    }
                    a, a:link, a:visited, a:hover, a:active {
                        color: inherit !important;
                        text-decoration: none !important;
                        pointer-events: none !important;
                        cursor: default !important;
                    }
                </style>
                <script>
                    function getPageCount() {
                        var content = document.getElementById('reader-content');
                        if (!content) return 1;
                        var scrollW = content.scrollWidth;
                        var clientW = window.innerWidth;
                        if (clientW <= 0) return 1;
                        var pages = Math.round(scrollW / clientW);
                        return pages < 1 ? 1 : pages;
                    }

                    function scrollToPage(pageIndex) {
                        var content = document.getElementById('reader-content');
                        if (!content) return;
                        var offsetVw = -(pageIndex * 100);
                        content.style.transform = 'translateX(' + offsetVw + 'vw)';
                    }
                </script>
            </head>
            <body>
                <div id="reader-viewport">
                    <div id="reader-content">
                        $cleaned
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun inlineImagesInHtml(bodyHtml: String, chapterHref: String): String {
        val currentBook = book ?: return bodyHtml
        if (currentBook.images.isEmpty()) return bodyHtml

        val chapterDir = if (chapterHref.contains("/")) chapterHref.substringBeforeLast("/") + "/" else ""

        fun resolveImagePath(src: String): String {
            val cleanSrc = src.trim()
            val combined = if (cleanSrc.startsWith("/")) cleanSrc.removePrefix("/") else chapterDir + cleanSrc
            return normalizePath(combined)
        }

        val imgRegex = Regex("""(<img\b[^>]*?\bsrc=["'])([^"']+)(["'][^>]*>)""", RegexOption.IGNORE_CASE)
        var resultHtml = imgRegex.replace(bodyHtml) { matchResult ->
            val prefix = matchResult.groupValues[1]
            val rawSrc = matchResult.groupValues[2]
            val suffix = matchResult.groupValues[3]

            if (rawSrc.startsWith("data:", ignoreCase = true) || rawSrc.startsWith("http:", ignoreCase = true) || rawSrc.startsWith("https:", ignoreCase = true)) {
                matchResult.value
            } else {
                val targetPath = resolveImagePath(rawSrc)
                val bytes = currentBook.images[targetPath]
                    ?: currentBook.images[targetPath.lowercase()]
                    ?: currentBook.images.entries.firstOrNull { it.key.endsWith(rawSrc, ignoreCase = true) }?.value

                if (bytes != null) {
                    val mimeType = when {
                        rawSrc.endsWith(".png", ignoreCase = true) -> "image/png"
                        rawSrc.endsWith(".gif", ignoreCase = true) -> "image/gif"
                        rawSrc.endsWith(".webp", ignoreCase = true) -> "image/webp"
                        rawSrc.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                        else -> "image/jpeg"
                    }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    "$prefix data:$mimeType;base64,$base64 $suffix"
                } else {
                    matchResult.value
                }
            }
        }

        val svgImageRegex = Regex("""(<image\b[^>]*?\b(?:xlink:href|href)=["'])([^"']+)(["'][^>]*>)""", RegexOption.IGNORE_CASE)
        resultHtml = svgImageRegex.replace(resultHtml) { matchResult ->
            val prefix = matchResult.groupValues[1]
            val rawSrc = matchResult.groupValues[2]
            val suffix = matchResult.groupValues[3]

            if (rawSrc.startsWith("data:", ignoreCase = true)) {
                matchResult.value
            } else {
                val targetPath = resolveImagePath(rawSrc)
                val bytes = currentBook.images[targetPath]
                    ?: currentBook.images[targetPath.lowercase()]
                    ?: currentBook.images.entries.firstOrNull { it.key.endsWith(rawSrc, ignoreCase = true) }?.value

                if (bytes != null) {
                    val mimeType = when {
                        rawSrc.endsWith(".png", ignoreCase = true) -> "image/png"
                        rawSrc.endsWith(".gif", ignoreCase = true) -> "image/gif"
                        rawSrc.endsWith(".webp", ignoreCase = true) -> "image/webp"
                        rawSrc.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
                        else -> "image/jpeg"
                    }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    "$prefix data:$mimeType;base64,$base64 $suffix"
                } else {
                    matchResult.value
                }
            }
        }

        return resultHtml
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

    private fun updateThemeColors() {
        val (rootBg, toolbarBg, textColor, subTextColor) = when (readerTheme) {
            ThemeMode.LIGHT -> Quadruple(Color.parseColor("#FFFFFF"), Color.parseColor("#FAFAFA"), Color.parseColor("#000000"), Color.parseColor("#888888"))
            ThemeMode.SEPIA -> Quadruple(Color.parseColor("#FBF0D9"), Color.parseColor("#F4E6C8"), Color.parseColor("#5F4B32"), Color.parseColor("#8C765C"))
            ThemeMode.DARK -> Quadruple(Color.parseColor("#121212"), Color.parseColor("#1E1E1E"), Color.parseColor("#FFFFFF"), Color.parseColor("#AAAAAA"))
        }

        binding.readerRoot.setBackgroundColor(rootBg)
        binding.headerToolbar.setBackgroundColor(toolbarBg)
        binding.bottomNav.setBackgroundColor(toolbarBg)
        binding.tvBookTitle.setTextColor(textColor)
        binding.tvChapterProgress.setTextColor(textColor)
        binding.tvSmallChapterHeader.setTextColor(subTextColor)
        binding.tvSmallPageProgress.setTextColor(subTextColor)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun showTableOfContentsDialog() {
        val chapters = book?.chapters ?: return
        val chapterTitles = chapters.mapIndexed { index, chapter ->
            val titleStr = if (chapter.title.isNotBlank()) chapter.title else "Chapter ${index + 1}"
            if (index == currentChapterIndex) "► $titleStr" else "   $titleStr"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Table of Contents (${chapters.size} chapters)")
            .setItems(chapterTitles) { _, which ->
                currentChapterIndex = which
                currentPageIndex = 0
                loadCurrentChapter()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        val dialogBinding = DialogReaderSettingsBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvFontSizeValue.text = "$fontSizeSp sp"

        dialogBinding.btnFontDecrease.setOnClickListener {
            if (fontSizeSp > 12) {
                fontSizeSp -= 2
                dialogBinding.tvFontSizeValue.text = "$fontSizeSp sp"
                PreferencesHelper.getInstance().putInt("reader_font_size", fontSizeSp)
                loadCurrentChapter()
            }
        }

        dialogBinding.btnFontIncrease.setOnClickListener {
            if (fontSizeSp < 32) {
                fontSizeSp += 2
                dialogBinding.tvFontSizeValue.text = "$fontSizeSp sp"
                PreferencesHelper.getInstance().putInt("reader_font_size", fontSizeSp)
                loadCurrentChapter()
            }
        }

        dialogBinding.btnThemeLight.setOnClickListener {
            readerTheme = ThemeMode.LIGHT
            PreferencesHelper.getInstance().putString("reader_theme", "LIGHT")
            loadCurrentChapter()
            dialog.dismiss()
        }

        dialogBinding.btnThemeSepia.setOnClickListener {
            readerTheme = ThemeMode.SEPIA
            PreferencesHelper.getInstance().putString("reader_theme", "SEPIA")
            loadCurrentChapter()
            dialog.dismiss()
        }

        dialogBinding.btnThemeDark.setOnClickListener {
            readerTheme = ThemeMode.DARK
            PreferencesHelper.getInstance().putString("reader_theme", "DARK")
            loadCurrentChapter()
            dialog.dismiss()
        }

        dialog.show()
    }
}
