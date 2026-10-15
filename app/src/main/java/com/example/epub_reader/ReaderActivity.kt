package com.example.epub_reader

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.AdsManager.AdsManager
import com.example.epub_reader.AdsManager.ApplovinNativeAdManager
import com.example.epub_reader.AdsManager.FullScreenAdFragment
import com.example.epub_reader.databinding.ActivityReaderBinding
import com.example.epub_reader.epub.BookHolder
import com.example.epub_reader.epub.EpubBook
import com.example.epub_reader.ui.ReaderProgressPanelController
import com.example.epub_reader.ui.ReaderSearchBottomSheet
import com.example.epub_reader.ui.ReaderSettingsPanelController
import com.example.epub_reader.ui.ReaderTocBottomSheet
import com.example.epub_reader.ui.TtsLanguageBottomSheet
import com.example.epub_reader.ui.main.IosAlertDialogs
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.BookLanguageDetector
import com.example.epub_reader.utils.BookmarkManager
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.ReaderSessionState
import com.example.epub_reader.utils.ReaderThemeManager
import com.example.epub_reader.utils.TtsLanguage
import com.example.epub_reader.utils.TtsLanguages
import com.example.epub_reader.utils.dpToPx
import com.example.epub_reader.utils.getStatusBarHeight
import com.example.epub_reader.utils.removeSubviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class ReaderActivity : LocalizeBaseActivity() {

    private lateinit var binding: ActivityReaderBinding
    private var book: EpubBook? = null

    private val themeManager = ReaderThemeManager.instance
    private val readerState = ReaderSessionState()
    private lateinit var settingsPanelController: ReaderSettingsPanelController
    private lateinit var progressPanelController: ReaderProgressPanelController
    private var activeSearchKeyword: String? = null

    private var textToSpeech: TextToSpeech? = null
    private var isTtsSpeaking = false
    private var isWaitingTtsForChapterLoad = false
    private var ttsRetryCount = 0
    private var pageTurnCounter = 0

    // Reading language (the user picks it before the book is read aloud)
    private enum class TtsEngineState { INITIALIZING, READY, FAILED }
    private var ttsEngineState = TtsEngineState.INITIALIZING
    /** null = engine chosen in the system settings */
    private var ttsEnginePackage: String? = null
    /** Language picked while the engine was still starting */
    private var pendingTtsLanguageCode: String? = null
    private var detectedLanguageCode: String? = null
    private var isLanguageDetected = false
    private var isOpeningLanguagePicker = false
    private var ttsAlertDialog: Dialog? = null

    enum class ThemeMode {
        LIGHT, DARK, REALTIME, SEPIA
    }

    private var areToolbarsVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val statusBarHeight = getStatusBarHeight()
        binding.tvSmallChapterHeader.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = if (statusBarHeight >= 50) statusBarHeight - dpToPx(30) else dpToPx(24)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { _, insets ->
            val bottomSystemInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            binding.adBannerContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset
            }
            binding.webViewContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = if (statusBarHeight >= 50) statusBarHeight - dpToPx(20) else dpToPx(30)
                bottomMargin = bottomSystemInset + dpToPx(70)
            }
            binding.tvSmallPageProgress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset + dpToPx(78)
            }

            // Adjust bottomNav height and bottom padding so all items sit above bottomSystemInset
            binding.bottomNav.updateLayoutParams<ViewGroup.LayoutParams> {
                height = dpToPx(64) + bottomSystemInset
            }
            binding.bottomNav.setPadding(
                binding.bottomNav.paddingLeft,
                binding.bottomNav.paddingTop,
                binding.bottomNav.paddingRight,
                bottomSystemInset
            )

            insets
        }

        book = BookHolder.currentBook
        if (book == null || book?.chapters.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_no_book_content), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val savedIndex = themeManager.getChapterPosition(book?.title ?: "")
        if (savedIndex in 0 until (book?.chapters?.size ?: 0)) {
            readerState.currentChapterIndex = savedIndex
        }

        initChapterPageEstimates()
        initTts()
        setupSettingsPanelController()
        setupProgressPanelController()
        setupWebView()
        setupListeners()
        setupTtsLanguagePicker()
        setupSwipeGesture()
        setupToolbarOverlay()
        loadCurrentChapter()
    }

    private fun setupSettingsPanelController() {
        settingsPanelController = ReaderSettingsPanelController(
            binding = binding.readerSettingsPanel,
            onFontSizeChanged = { newSize ->
                themeManager.fontSizeSp = newSize
                initChapterPageEstimates()
                loadCurrentChapter()
            },
            onThemeChanged = { newTheme ->
                themeManager.themeMode = newTheme
                loadCurrentChapter()
            },
            onLineSpacingChanged = { newRatio ->
                themeManager.lineSpacingRatio = newRatio
                loadCurrentChapter()
            }
        )
        settingsPanelController.initListeners(themeManager.fontSizeSp, themeManager.lineSpacingRatio)
    }

    private fun setupProgressPanelController() {
        progressPanelController = ReaderProgressPanelController(
            binding = binding.readerProgressPanel,
            onSeekToChapter = { chapterIdx ->
                readerState.currentChapterIndex = chapterIdx
                readerState.currentPageIndex = 0
                loadCurrentChapter()
            },
            onPrevChapter = {
                if (readerState.currentChapterIndex > 0) {
                    readerState.currentChapterIndex--
                    readerState.currentPageIndex = 0
                    loadCurrentChapter()
                }
            },
            onNextChapter = {
                val total = book?.chapters?.size ?: 0
                if (readerState.currentChapterIndex < total - 1) {
                    readerState.currentChapterIndex++
                    readerState.currentPageIndex = 0
                    loadCurrentChapter()
                }
            }
        )
        progressPanelController.initListeners()
    }

    override fun loadAd() {
        super.loadAd()
        if (canShowBanner(DisabledAdType.previewBanner)) {
            val nativeManager = ApplovinNativeAdManager(this)
            nativeManager.loadApplovinNativeAd(
                context = this,
                layoutResource = R.layout.native_custom_ad_banner,
                adPosition = AdPosition.preview
            ) { success ->
                if (success) {
                    nativeManager.populateNativeAdView(
                        layoutResource = R.layout.native_custom_ad_banner,
                        type = DisabledAdType.previewBanner,
                        adFrame = binding.adBannerContainer,
                        onAdReady = {
                            adjustLayoutForAdState(isAdLoaded = true)
                        }
                    )
                } else {
                    adjustLayoutForAdState(isAdLoaded = false)
                }
            }
        } else {
            adjustLayoutForAdState(isAdLoaded = false)
        }
    }

    private fun adjustLayoutForAdState(isAdLoaded: Boolean) {
        val insets = ViewCompat.getRootWindowInsets(binding.readerRoot)
        val bottomSystemInset = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0

        if (isAdLoaded) {
            binding.adBannerContainer.visibility = View.VISIBLE
            binding.webViewContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset + dpToPx(70)
            }
            binding.tvSmallPageProgress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset + dpToPx(78)
            }
        } else {
            binding.adBannerContainer.removeSubviews()
            binding.adBannerContainer.visibility = View.GONE
            binding.webViewContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset + dpToPx(10)
            }
            binding.tvSmallPageProgress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomSystemInset + dpToPx(16)
            }
        }
    }

    private fun setupToolbarOverlay() {
        val statusBarHeight = getStatusBarHeight()
        binding.headerToolbar.updateLayoutParams<ViewGroup.LayoutParams> {
            height = (statusBarHeight * 2 / 3) + dpToPx(40)
        }
        binding.headerToolbar.setPadding(
            binding.headerToolbar.paddingLeft,
            (statusBarHeight * 2 / 3),
            binding.headerToolbar.paddingRight,
            binding.headerToolbar.paddingBottom
        )

        binding.headerToolbar.post {
            val h = binding.headerToolbar.height.toFloat().let { if (it > 0) it else (statusBarHeight + dpToPx(56)).toFloat() }
            binding.headerToolbar.translationY = -h
        }
        binding.bottomNav.post {
            val bottomH = binding.bottomNav.height.toFloat().let { if (it > 0) it else dpToPx(56).toFloat() }
            val distanceToHide = bottomH + dpToPx(30)
            binding.bottomNav.translationY = distanceToHide
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
        if (::settingsPanelController.isInitialized) {
            settingsPanelController.hide()
        }
        if (::progressPanelController.isInitialized) {
            progressPanelController.hide()
        }
        if (!areToolbarsVisible) return
        areToolbarsVisible = false
        val headerH = binding.headerToolbar.height.toFloat().let { if (it > 0) it else 200f }
        val bottomH = binding.bottomNav.height.toFloat().let { if (it > 0) it else dpToPx(56).toFloat() }
        val distanceToHide = bottomH + dpToPx(30)

        binding.headerToolbar.animate()
            .translationY(-headerH)
            .setDuration(250)
            .start()
        binding.bottomNav.animate()
            .translationY(distanceToHide)
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

    private fun initChapterPageEstimates() {
        val chapters = book?.chapters ?: return
        if (readerState.chapterPageCounts.size != chapters.size) {
            readerState.chapterPageCounts = IntArray(chapters.size)
        }
        val charsPerPage = (themeManager.fontSizeSp * 50).coerceAtLeast(400)
        for (i in chapters.indices) {
            val len = chapters[i].contentHtml.length
            val est = (len / charsPerPage).coerceAtLeast(1)
            readerState.chapterPageCounts[i] = est
        }
    }

    private fun calculatePageCountAndRender() {
        binding.webViewContent.evaluateJavascript("getPageCount();") { resultStr ->
            val count = resultStr?.toIntOrNull() ?: 1
            readerState.totalPagesInChapter = if (count < 1) 1 else count

            if (readerState.chapterPageCounts.isNotEmpty() && readerState.currentChapterIndex in readerState.chapterPageCounts.indices) {
                readerState.chapterPageCounts[readerState.currentChapterIndex] = readerState.totalPagesInChapter
            }

            if (readerState.isPendingGoToLastPage) {
                readerState.isPendingGoToLastPage = false
                readerState.currentPageIndex = readerState.totalPagesInChapter - 1
                updatePagePosition()
            } else {
                binding.webViewContent.evaluateJavascript("scrollToSearchTargetIfNeeded();") { targetPageStr ->
                    val targetPage = targetPageStr?.toIntOrNull() ?: -1
                    if (targetPage >= 0) {
                        readerState.currentPageIndex = targetPage.coerceIn(0, readerState.totalPagesInChapter - 1)
                    } else if (readerState.currentPageIndex >= readerState.totalPagesInChapter) {
                        readerState.currentPageIndex = readerState.totalPagesInChapter - 1
                    }
                    updatePagePosition()
                }
            }

            binding.progressBar.visibility = View.GONE

            if (isWaitingTtsForChapterLoad) {
                isWaitingTtsForChapterLoad = false
                binding.webViewContent.postDelayed({
                    speakCurrentPageText()
                }, 500)
            }
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

        binding.btnSearch.setOnClickListener {
            if (::settingsPanelController.isInitialized) settingsPanelController.hide()
            if (::progressPanelController.isInitialized) progressPanelController.hide()

            val sheet = ReaderSearchBottomSheet(
                book = book,
                onSearchResultClicked = { chapterIdx, keyword ->
                    activeSearchKeyword = keyword
                    readerState.currentChapterIndex = chapterIdx
                    readerState.currentPageIndex = 0
                    loadCurrentChapter()
                }
            )
            sheet.show(supportFragmentManager, ReaderSearchBottomSheet.TAG)
        }

        binding.btnAudio.setOnClickListener {
            toggleTts()
        }

        binding.btnBookmark.setOnClickListener {
            val currentBook = book ?: return@setOnClickListener
            val chapters = currentBook.chapters
            if (readerState.currentChapterIndex !in chapters.indices) return@setOnClickListener

            val chapter = chapters[readerState.currentChapterIndex]
            val chapterTitle = if (chapter.title.isNotBlank()) {
                chapter.title
            } else {
                getString(R.string.chapter_number, readerState.currentChapterIndex + 1)
            }

            val totalBookPages = readerState.calculateGlobalTotalPages()
            val globalCurrentPage = readerState.calculateGlobalCurrentPage()

            val cleanText = try {
                HtmlCompat.fromHtml(chapter.contentHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    .toString()
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } catch (_: Exception) {
                chapter.contentHtml
                    .replace(Regex("<.*?>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            val snippet = if (cleanText.length > 120) cleanText.take(120) + "..." else cleanText

            val isAdded = BookmarkManager.instance.toggleBookmark(
                bookTitle = currentBook.title,
                chapterIndex = readerState.currentChapterIndex,
                chapterTitle = chapterTitle,
                pageIndexInChapter = readerState.currentPageIndex,
                globalPage = globalCurrentPage,
                globalTotalPages = totalBookPages,
                snippetText = snippet
            )

            updateBookmarkIconState()

            val msg = getString(
                if (isAdded) R.string.bookmark_added else R.string.bookmark_removed,
                globalCurrentPage,
                totalBookPages
            )
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnToc.setOnClickListener {
            showTableOfContentsDialog()
        }

        binding.btnProgress.setOnClickListener {
            showProgressDialog()
        }

        binding.btnTheme.setOnClickListener {
            toggleLightDarkTheme()
        }

        binding.btnReaderSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun toggleLightDarkTheme() {
        themeManager.themeMode = when (themeManager.themeMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.REALTIME
            ThemeMode.REALTIME -> ThemeMode.LIGHT
            else -> ThemeMode.LIGHT
        }
        loadCurrentChapter()
    }

    private fun showProgressDialog() {
        if (::settingsPanelController.isInitialized) {
            settingsPanelController.hide()
        }
        if (::progressPanelController.isInitialized) {
            progressPanelController.toggle()
        }
    }

    private fun goToNextPage() {
        val chaptersCount = book?.chapters?.size ?: 0
        if (readerState.currentPageIndex < readerState.totalPagesInChapter - 1) {
            readerState.currentPageIndex++
            updatePagePosition()
        } else if (readerState.currentChapterIndex < chaptersCount - 1) {
            readerState.currentChapterIndex++
            readerState.currentPageIndex = 0
            loadCurrentChapter()
        }
    }

    private fun goToPrevPage() {
        if (readerState.currentPageIndex > 0) {
            readerState.currentPageIndex--
            updatePagePosition()
        } else if (readerState.currentChapterIndex > 0) {
            readerState.currentChapterIndex--
            readerState.isPendingGoToLastPage = true
            loadCurrentChapter()
        }
    }

    private fun loadCurrentChapter() {
        val currentBook = book ?: return
        val chapters = currentBook.chapters
        if (readerState.currentChapterIndex !in chapters.indices) return

        if (isTtsSpeaking) {
            isWaitingTtsForChapterLoad = true
        }

        val chapter = chapters[readerState.currentChapterIndex]
        binding.progressBar.visibility = View.VISIBLE

        val displayTitle = if (chapter.title.isNotBlank()) chapter.title else currentBook.title
        binding.tvSmallChapterHeader.text = displayTitle

        val htmlContent = formatChapterHtml(chapter.contentHtml, chapter.href)
        binding.webViewContent.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)

        themeManager.saveChapterPosition(currentBook.title, readerState.currentChapterIndex)
        updateThemeColors()
    }

    private fun updatePagePosition() {
        binding.webViewContent.evaluateJavascript("scrollToPage(${readerState.currentPageIndex});", null)

        val totalBookPages = readerState.calculateGlobalTotalPages()
        val globalCurrentPage = readerState.calculateGlobalCurrentPage()

        binding.tvSmallPageProgress.text = "$globalCurrentPage/$totalBookPages"
        updateBookmarkIconState()

        loadAd()

        pageTurnCounter++
        if (pageTurnCounter >= 10) {
            pageTurnCounter = 0
            showFullScreenNativeAd()
        }

        if (::progressPanelController.isInitialized) {
            val chapters = book?.chapters ?: emptyList()
            val currentTitle = if (chapters.isNotEmpty() && readerState.currentChapterIndex in chapters.indices) {
                chapters[readerState.currentChapterIndex].title
            } else ""
            progressPanelController.updateData(
                chapterIndex = readerState.currentChapterIndex,
                totalChapters = chapters.size,
                chapterTitle = currentTitle,
                globalPage = globalCurrentPage,
                globalTotalPages = totalBookPages
            )
        }
    }

    private fun showFullScreenNativeAd() {
        if (!canShowBanner(DisabledAdType.onboardingFullscreen)) return
        try {
            val fullScreenAd = FullScreenAdFragment()
            fullScreenAd.bindTo(DisabledAdType.onboardingFullscreen)
            fullScreenAd.show(supportFragmentManager) {
                // Resume reading after full-screen ad closes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateBookmarkIconState() {
        val currentBook = book ?: return
        val isBookmarked = BookmarkManager.instance.isBookmarked(
            bookTitle = currentBook.title,
            chapterIndex = readerState.currentChapterIndex,
            pageIndexInChapter = readerState.currentPageIndex
        )

        if (isBookmarked) {
            val yellowColor = Color.parseColor("#FFC107")
            binding.btnBookmark.setColorFilter(yellowColor)
        } else {
            binding.btnBookmark.clearColorFilter()
        }
    }

    private fun highlightSearchKeywordInHtml(bodyHtml: String, keyword: String): String {
        if (keyword.isBlank() || keyword.length < 2) return bodyHtml
        return try {
            var isFirst = true
            val textNodeRegex = Regex("(?<=^|>)([^<]+)(?=<|$)", RegexOption.IGNORE_CASE)
            textNodeRegex.replace(bodyHtml) { matchResult ->
                val textNode = matchResult.value
                val keywordRegex = Regex("(${Regex.escape(keyword)})", RegexOption.IGNORE_CASE)
                keywordRegex.replace(textNode) { kwMatch ->
                    val idAttr = if (isFirst) {
                        isFirst = false
                        "id=\"search-target\" "
                    } else ""
                    "<mark ${idAttr}style=\"background-color: #FFA726; color: #000000; font-weight: bold; border-radius: 2px; padding: 2px 4px; display: inline;\">${kwMatch.value}</mark>"
                }
            }
        } catch (_: Exception) {
            bodyHtml
        }
    }

    private fun formatChapterHtml(bodyHtml: String, chapterHref: String): String {
        val config = themeManager.getCurrentThemeConfig()

        var cleaned = inlineImagesInHtml(bodyHtml, chapterHref)

        val kw = activeSearchKeyword
        if (!kw.isNullOrBlank()) {
            cleaned = highlightSearchKeywordInHtml(cleaned, kw)
        }

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
                        background-color: ${config.webViewBgColorHex};
                        color: ${config.textColorHex};
                        font-family: -apple-system, sans-serif;
                        font-size: ${themeManager.fontSizeSp}px;
                        line-height: ${themeManager.lineSpacingRatio};
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
                    #reader-content h6 {
                        color: ${config.headingColorHex};
                        font-weight: 600 !important;
                        font-size: 1.4em !important;
                        margin-top: 0.3em !important;
                        margin-bottom: 0.4em !important;
                        margin-left: 0 !important;
                        padding-left: 0 !important;
                        text-indent: 0 !important;
                        text-align: left !important;
                    }
                    #reader-content ol, 
                    #reader-content ul {
                        list-style: none !important;
                        list-style-type: none !important;
                        padding-left: 4px !important;
                        margin-left: 0 !important;
                        margin-right: 0 !important;
                        margin-top: 0.4em !important;
                    }
                    #reader-content p {
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                        margin-left: 0 !important;
                        margin-right: 0 !important;
                        margin-bottom: 0.6em !important;
                        line-height: ${themeManager.lineSpacingRatio} !important;
                        text-align: left !important;
                        text-indent: 2em !important;
                    }
                    #reader-content li, 
                    #reader-content div {
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                        margin-left: 0 !important;
                        margin-right: 0 !important;
                        margin-bottom: 0.5em !important;
                        line-height: ${themeManager.lineSpacingRatio} !important;
                        text-align: left !important;
                    }
                    #reader-content span,
                    #reader-content a {
                        padding-left: 0 !important;
                        padding-right: 0 !important;
                        margin-left: 0 !important;
                        margin-right: 0 !important;
                        line-height: ${themeManager.lineSpacingRatio} !important;
                    }
                    /* Thụt đầu dòng cho đoạn văn: p, div "lá", và đoạn tách bởi <br> */
                    #reader-content p,
                    #reader-content div:not(:has(p, div, ul, ol, li, table, h1, h2, h3, h4, h5, h6)),
                    #reader-content .rd-para {
                        text-indent: 0.6em !important;
                    }
                    #reader-content .rd-para {
                        display: block;
                        margin-bottom: 0.6em;
                    }
                    #reader-content p:has(img),
                    #reader-content div:has(img),
                    #reader-content .rd-para:has(img) {
                        text-indent: 0 !important;
                    }
                    /* Mục lục / danh sách: giữ thẳng hàng, không thụt */
                    #reader-content li p, 
                    #reader-content li div,
                    #reader-content li span,
                    #reader-content li .rd-para,
                    #reader-content li {
                        text-indent: 0 !important;
                        text-align: left !important;
                        padding-left: 0 !important;
                    }
                    img {
                        max-width: calc(100vw - 32px) !important;
                        max-height: calc(100vh - 48px) !important;
                        height: auto !important;
                        object-fit: contain;
                        display: block !important;
                        margin-left: auto !important;
                        margin-right: auto !important;
                        margin-top: 8px !important;
                        margin-bottom: 8px !important;
                    }
                    p {
                        margin-bottom: 0.5em !important;
                        text-align: left !important;
                        text-indent: 1.5em !important;
                    }
                    p:has(img), div:has(img) {
                        text-indent: 0 !important;
                        text-align: center !important;
                    }
                    a, a:link, a:visited, a:hover, a:active {
                        color: inherit !important;
                        text-decoration: none !important;
                        pointer-events: none !important;
                        cursor: default !important;
                    }
                </style>
                <script>
                    function splitBreaks() {
                        var root = document.getElementById('reader-content');
                        if (!root) return;
                        var blockTags = {P:1, DIV:1, UL:1, OL:1, LI:1, TABLE:1, H1:1, H2:1, H3:1, H4:1, H5:1, H6:1};
                        var holders = Array.prototype.slice.call(root.querySelectorAll('p, div, li'));
                        holders.forEach(function (el) {
                            var hasBr = false;
                            for (var n = el.firstChild; n; n = n.nextSibling) {
                                if (n.nodeName === 'BR') { hasBr = true; break; }
                            }
                            if (!hasBr) return;
                            if (el.closest('li, ol, ul')) return;
                            var nodes = Array.prototype.slice.call(el.childNodes);
                            var frag = document.createDocumentFragment();
                            var cur = null;
                            nodes.forEach(function (n) {
                                if (n.nodeName === 'BR') { cur = null; return; }
                                if (n.nodeType === 1 && blockTags[n.nodeName]) {
                                    frag.appendChild(n);
                                    cur = null;
                                    return;
                                }
                                if (n.nodeType === 3 && !cur && !n.textContent.trim()) return;
                                if (!cur) {
                                    cur = document.createElement('span');
                                    cur.className = 'rd-para';
                                    frag.appendChild(cur);
                                }
                                cur.appendChild(n);
                            });
                            while (el.firstChild) el.removeChild(el.firstChild);
                            el.appendChild(frag);
                        });
                    }
                    document.addEventListener('DOMContentLoaded', splitBreaks);

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

                    function scrollToSearchTargetIfNeeded() {
                        var target = document.getElementById('search-target');
                        if (!target) return -1;
                        var clientW = window.innerWidth;
                        if (clientW <= 0) return -1;
                        var offsetLeft = target.offsetLeft;
                        var pageIdx = Math.floor(offsetLeft / clientW);
                        return pageIdx < 0 ? 0 : pageIdx;
                    }

                    function getCurrentPageText() {
                        var root = document.getElementById('reader-content');
                        if (!root) return '';
                        var clientW = window.innerWidth;
                        if (clientW <= 0) return root.innerText || '';
                        
                        var nodes = root.querySelectorAll('p, div, li, h1, h2, h3, h4, h5, h6');
                        var currentText = '';
                        var pageLeft = Math.abs(parseFloat(root.style.transform.replace(/[^\d.-]/g, '')) || 0);
                        
                        nodes.forEach(function(node) {
                            var offset = node.offsetLeft;
                            if (offset >= pageLeft - 40 && offset < pageLeft + clientW + 40) {
                                var txt = (node.innerText || '').trim();
                                if (txt.length > 0) {
                                    // Pause between paragraphs (CJK / Thai punctuation counts as an end too)
                                    if (!/[.!?:;…。！？：；」』”）]$/.test(txt)) {
                                        txt += '.';
                                    }
                                    currentText += txt + ' ';
                                }
                            }
                        });
                        
                        var result = currentText.trim();
                        if (!result) result = root.innerText || '';
                        return result.replace(/[\n\r\t]/g, ' ').replace(/\s+/g, ' ');
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
        val config = themeManager.getCurrentThemeConfig()

        binding.webViewContent.setBackgroundColor(config.webViewBgColorInt)
        binding.readerRoot.setBackgroundColor(Color.parseColor("#FFFFFF"))
        binding.headerToolbar.setBackgroundColor(Color.parseColor("#FAFAFA"))
        binding.bottomNav.setBackgroundColor(Color.parseColor("#FAFAFA"))
        binding.tvSmallChapterHeader.setTextColor(Color.parseColor("#888888"))
        binding.tvSmallPageProgress.setTextColor(Color.parseColor("#888888"))

        when (themeManager.themeMode) {
            ThemeMode.LIGHT -> {
                binding.tvThemeLabel.text = getString(R.string.theme_mode_light)
                binding.imgThemeIcon.setImageResource(R.drawable.ic_light_mode)
            }
            ThemeMode.DARK -> {
                binding.tvThemeLabel.text = getString(R.string.theme_mode_dark)
                binding.imgThemeIcon.setImageResource(R.drawable.ic_dark_mode)
            }
            ThemeMode.REALTIME -> {
                binding.tvThemeLabel.text = getString(R.string.theme_mode_auto)
                binding.imgThemeIcon.setImageResource(R.drawable.ic_real_time_mode)
            }
            else -> {
                binding.tvThemeLabel.text = getString(R.string.theme_mode_light)
                binding.imgThemeIcon.setImageResource(R.drawable.ic_light_mode)
            }
        }
    }

    private fun showTableOfContentsDialog() {
        if (::settingsPanelController.isInitialized) settingsPanelController.hide()
        if (::progressPanelController.isInitialized) progressPanelController.hide()

        val chapters = book?.chapters ?: return
        val totalBookPages = readerState.calculateGlobalTotalPages()
        val globalCurrentPage = readerState.calculateGlobalCurrentPage()

        val sheet = ReaderTocBottomSheet(
            bookTitle = book?.title ?: "EPUB Reader",
            chapters = chapters,
            activeChapterIndex = readerState.currentChapterIndex,
            globalCurrentPage = globalCurrentPage,
            globalTotalPages = totalBookPages,
            chapterPageCounts = readerState.chapterPageCounts,
            onChapterSelected = { selectedIndex ->
                readerState.currentChapterIndex = selectedIndex
                readerState.currentPageIndex = 0
                loadCurrentChapter()
            },
            onBookmarkSelected = { bookmarkItem ->
                readerState.currentChapterIndex = bookmarkItem.chapterIndex
                readerState.currentPageIndex = bookmarkItem.pageIndexInChapter
                loadCurrentChapter()
            }
        )
        sheet.show(supportFragmentManager, ReaderTocBottomSheet.TAG)
    }

    private fun showSettingsDialog() {
        if (::progressPanelController.isInitialized) {
            progressPanelController.hide()
        }
        if (::settingsPanelController.isInitialized) {
            settingsPanelController.toggle()
        }
    }

    // region Text-to-speech

    private fun initTts() {
        ttsEngineState = TtsEngineState.INITIALIZING
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
                textToSpeech?.setAudioAttributes(audioAttributes)
                ttsEngineState = TtsEngineState.READY

                // A language was picked while the engine was starting
                val pending = pendingTtsLanguageCode
                pendingTtsLanguageCode = null
                if (pending != null && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    startTtsInLanguage(pending)
                }
            } else {
                ttsEngineState = TtsEngineState.FAILED
                // Next attempt uses the engine of the system settings again
                ttsEnginePackage = null
                pendingTtsLanguageCode = null
                Toast.makeText(this, getString(R.string.error_init_tts), Toast.LENGTH_SHORT).show()
            }
        }
        val enginePackage = ttsEnginePackage
        textToSpeech = if (enginePackage != null) {
            TextToSpeech(this, listener, enginePackage)
        } else {
            TextToSpeech(this, listener)
        }
    }

    /** Headphone button: stop reading, or choose the language then start reading */
    private fun toggleTts() {
        if (isTtsSpeaking) {
            stopTts()
            Toast.makeText(this, getString(R.string.tts_stopped), Toast.LENGTH_SHORT).show()
        } else {
            showTtsLanguagePicker()
        }
    }

    private fun stopTts() {
        isTtsSpeaking = false
        textToSpeech?.stop()
        updateAudioButtonState()
    }

    /** The headphone icon turns blue while the book is read aloud */
    private fun updateAudioButtonState() {
        if (!::binding.isInitialized) return
        if (isTtsSpeaking) {
            binding.btnAudio.setColorFilter(ContextCompat.getColor(this, R.color.ios_blue))
        } else {
            binding.btnAudio.clearColorFilter()
        }
    }

    private fun setupTtsLanguagePicker() {
        supportFragmentManager.setFragmentResultListener(TtsLanguageBottomSheet.REQUEST_KEY, this) { _, result ->
            val code = result.getString(TtsLanguageBottomSheet.RESULT_CODE) ?: return@setFragmentResultListener
            startTtsInLanguage(code)
        }
    }

    /**
     * "Ngôn ngữ đọc" sheet: the language detected from the book (or the one chosen last time
     * for this book) is already checked.
     */
    private fun showTtsLanguagePicker() {
        val currentBook = book ?: return
        if (isOpeningLanguagePicker) return
        if (supportFragmentManager.findFragmentByTag(TtsLanguageBottomSheet.TAG) != null) return
        isOpeningLanguagePicker = true
        val engine = if (ttsEngineState == TtsEngineState.READY) textToSpeech else null

        lifecycleScope.launch {
            try {
                if (!isLanguageDetected) {
                    detectedLanguageCode = withContext(Dispatchers.Default) {
                        try {
                            BookLanguageDetector.detect(currentBook)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    isLanguageDetected = true
                }
                val voiceStatuses = if (engine != null) {
                    withContext(Dispatchers.Default) { TtsLanguages.voiceStatuses(engine) }
                } else {
                    emptyMap<String, Int>()
                }
                if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@launch
                if (supportFragmentManager.findFragmentByTag(TtsLanguageBottomSheet.TAG) != null) return@launch

                val previous = savedTtsLanguageCode()
                val detected = detectedLanguageCode
                TtsLanguageBottomSheet.newInstance(
                    selected = previous ?: detected ?: TtsLanguages.defaultCode(),
                    detected = detected,
                    previous = previous,
                    voiceStatuses = voiceStatuses
                ).show(supportFragmentManager, TtsLanguageBottomSheet.TAG)
            } finally {
                isOpeningLanguagePicker = false
            }
        }
    }

    private fun startTtsInLanguage(code: String) {
        val language = TtsLanguages.find(code) ?: return
        saveTtsLanguageCode(code)

        when (ttsEngineState) {
            TtsEngineState.INITIALIZING -> {
                pendingTtsLanguageCode = code
                Toast.makeText(this, getString(R.string.tts_preparing), Toast.LENGTH_SHORT).show()
                return
            }
            TtsEngineState.FAILED -> {
                // Start the engine again (it may have been installed / enabled in the meantime)
                pendingTtsLanguageCode = code
                textToSpeech?.shutdown()
                initTts()
                if (ttsEngineState == TtsEngineState.INITIALIZING) {
                    Toast.makeText(this, getString(R.string.tts_preparing), Toast.LENGTH_SHORT).show()
                }
                return
            }
            TtsEngineState.READY -> Unit
        }

        val tts = textToSpeech ?: return
        val result = try {
            tts.setLanguage(language.locale)
        } catch (_: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> showVoiceMissingAlert(language)
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                if (!switchToGoogleEngine(code)) showVoiceNotSupportedAlert(language)
            }
            else -> {
                isTtsSpeaking = true
                ttsRetryCount = 0
                updateAudioButtonState()
                Toast.makeText(
                    this,
                    getString(R.string.tts_started_language, getString(language.nameRes)),
                    Toast.LENGTH_SHORT
                ).show()
                speakCurrentPageText()
            }
        }
    }

    /**
     * The engine of the device (e.g. Samsung) has no voice for this language:
     * use Google's engine when it is installed, then try again.
     */
    private fun switchToGoogleEngine(code: String): Boolean {
        val google = TtsLanguages.GOOGLE_TTS_PACKAGE
        if (ttsEnginePackage == google) return false
        val currentEngine = try {
            textToSpeech?.defaultEngine
        } catch (_: Exception) {
            null
        }
        if (currentEngine == google || !TtsLanguages.isInstalled(this, google)) return false

        ttsEnginePackage = google
        pendingTtsLanguageCode = code
        textToSpeech?.shutdown()
        initTts()
        return true
    }

    private fun showVoiceMissingAlert(language: TtsLanguage) {
        val name = getString(language.nameRes)
        ttsAlertDialog?.dismiss()
        ttsAlertDialog = IosAlertDialogs.showActions(
            context = this,
            title = getString(R.string.tts_voice_missing_title),
            message = getString(R.string.tts_voice_missing_message, name),
            actions = listOf(
                IosAlertDialogs.Action(getString(R.string.tts_download_voice)) {
                    val engine = ttsEnginePackage ?: try {
                        textToSpeech?.defaultEngine
                    } catch (_: Exception) {
                        null
                    }
                    TtsLanguages.openVoiceDownload(this, engine)
                },
                IosAlertDialogs.Action(getString(R.string.tts_choose_other_language)) {
                    showTtsLanguagePicker()
                }
            ),
            cancelText = getString(R.string.cancel)
        )
    }

    private fun showVoiceNotSupportedAlert(language: TtsLanguage) {
        val name = getString(language.nameRes)
        val actions = mutableListOf<IosAlertDialogs.Action>()
        if (!TtsLanguages.isInstalled(this, TtsLanguages.GOOGLE_TTS_PACKAGE)) {
            actions += IosAlertDialogs.Action(getString(R.string.tts_install_google)) {
                TtsLanguages.openPlayStore(this, TtsLanguages.GOOGLE_TTS_PACKAGE)
            }
        }
        actions += IosAlertDialogs.Action(getString(R.string.tts_open_settings)) {
            TtsLanguages.openTtsSettings(this)
        }
        actions += IosAlertDialogs.Action(getString(R.string.tts_choose_other_language)) {
            showTtsLanguagePicker()
        }
        ttsAlertDialog?.dismiss()
        ttsAlertDialog = IosAlertDialogs.showActions(
            context = this,
            title = getString(R.string.tts_voice_unsupported_title, name),
            message = getString(R.string.tts_voice_unsupported_message, name),
            actions = actions,
            cancelText = getString(R.string.cancel)
        )
    }

    /** The reading language is remembered for each book */
    private fun ttsLanguageKey(): String? = book?.title?.let { "$TTS_LANGUAGE_KEY_PREFIX$it" }

    private fun savedTtsLanguageCode(): String? {
        val key = ttsLanguageKey() ?: return null
        val code = try {
            PreferencesHelper.getInstance().getString(key)
        } catch (_: Exception) {
            null
        }
        return if (TtsLanguages.find(code) != null) code else null
    }

    private fun saveTtsLanguageCode(code: String) {
        val key = ttsLanguageKey() ?: return
        try {
            PreferencesHelper.getInstance().putString(key, code)
        } catch (_: Exception) {
        }
    }

    // endregion

    private fun speakCurrentPageText() {
        if (!isTtsSpeaking) return

        binding.webViewContent.evaluateJavascript("getCurrentPageText();") { resultRaw ->
            var cleanText = ""
            if (!resultRaw.isNullOrBlank() && resultRaw != "null" && resultRaw != "\"\"") {
                cleanText = try {
                    val unquoted = if (resultRaw.startsWith("\"") && resultRaw.endsWith("\"") && resultRaw.length > 2) {
                        resultRaw.substring(1, resultRaw.length - 1)
                    } else resultRaw

                    unquoted.replace("\\n", " ")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                        .trim()
                } catch (_: Exception) {
                    ""
                }
            }

            if (cleanText.isBlank()) {
                val chapters = book?.chapters ?: emptyList()
                if (readerState.currentChapterIndex in chapters.indices) {
                    val rawHtml = chapters[readerState.currentChapterIndex].contentHtml
                    cleanText = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
            }

            if (cleanText.isBlank() && ttsRetryCount < 3) {
                ttsRetryCount++
                binding.webViewContent.postDelayed({ speakCurrentPageText() }, 400)
                return@evaluateJavascript
            }
            ttsRetryCount = 0

            if (cleanText.isNotBlank()) {
                val chunks = cleanText.chunked(2000)
                val lastUtteranceId = "reader_tts_${System.currentTimeMillis()}_last"

                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == lastUtteranceId) {
                            runOnUiThread {
                                if (isTtsSpeaking) {
                                    val lastChapter = readerState.currentChapterIndex
                                    val lastPage = readerState.currentPageIndex

                                    goToNextPage()

                                    if (readerState.currentChapterIndex == lastChapter) {
                                        if (readerState.currentPageIndex != lastPage) {
                                            binding.webViewContent.postDelayed({
                                                speakCurrentPageText()
                                            }, 600)
                                        } else {
                                            stopTts()
                                            Toast.makeText(this@ReaderActivity, getString(R.string.tts_finished), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            if (isTtsSpeaking && ttsRetryCount < 2) {
                                ttsRetryCount++
                                binding.webViewContent.postDelayed({ speakCurrentPageText() }, 500)
                            } else {
                                Toast.makeText(this@ReaderActivity, getString(R.string.tts_error_audio), Toast.LENGTH_SHORT).show()
                                stopTts()
                            }
                        }
                    }
                })

                chunks.forEachIndexed { index, chunk ->
                    val chunkId = if (index == chunks.size - 1) lastUtteranceId else "reader_tts_${System.currentTimeMillis()}_$index"
                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
                    val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    val speakResult = textToSpeech?.speak(chunk, queueMode, params, chunkId)
                    if (speakResult == TextToSpeech.ERROR) {
                        Toast.makeText(this, getString(R.string.tts_error_voice), Toast.LENGTH_SHORT).show()
                        stopTts()
                        return@evaluateJavascript
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.tts_error_no_text), Toast.LENGTH_SHORT).show()
                stopTts()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopTts()
    }

    override fun onDestroy() {
        ttsAlertDialog?.dismiss()
        ttsAlertDialog = null
        stopTts()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TTS_LANGUAGE_KEY_PREFIX = "tts_language_"
    }
}