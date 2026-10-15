package com.example.epub_reader.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.epub_reader.R
import com.example.epub_reader.ReaderActivity
import com.example.epub_reader.databinding.FragmentFilesBinding
import com.example.epub_reader.databinding.ItemMainFileBinding
import com.example.epub_reader.epub.BookHolder
import com.example.epub_reader.epub.EpubBook
import com.example.epub_reader.epub.EpubChapter
import com.example.epub_reader.epub.EpubParser
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.ImportedFileItem
import com.example.epub_reader.utils.LocalFileStore
import com.example.epub_reader.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen ("Các tập tin") – iOS look & feel:
 * - Nav bar: Search | "Các tập tin" | + | Sửa  (edit mode: Select all | "Các tập tin" | Xong)
 * - Free space + segmented control Tên / Thể loại / Thời gian
 * - File rows with (i) button -> iOS alert with file actions
 * - Edit mode: selection circles, gray selected rows, bottom bar
 *   Chia sẻ / di chuyển đến thư mục / Sao chép / Zip / Xóa bỏ
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    /** All imported files, sorted with [activeSortType] */
    private var allFiles = listOf<ImportedFileItem>()

    /** Files shown in the list (after the search filter) */
    private var fileList = listOf<ImportedFileItem>()

    private var activeSortType = SortType.NAME

    private var isEditMode = false
    private var isSearchMode = false
    private var searchQuery = ""
    private val selectedFileIds = mutableSetOf<String>()

    private val fileAdapter = FileAdapter()
    private var inputDialog: Dialog? = null
    private val coverCache = LruCache<String, Bitmap>(30)
    private val editInterpolator = DecelerateInterpolator(1.6f)
    private var editShiftPx = 0f

    enum class SortType {
        NAME, TYPE, TIME
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importFileFromUri(uri)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchFilePicker()
        } else {
            toast(getString(R.string.storage_permission_required))
        }
    }

    /** Back: leave edit mode / search mode first (like tapping "Xong" / "Hủy") */
    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                isEditMode -> setEditMode(false)
                isSearchMode -> closeSearch()
                else -> isEnabled = false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editShiftPx = EDIT_SHIFT_DP * resources.displayMetrics.density

        binding.rvMainFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMainFiles.adapter = fileAdapter
        (binding.rvMainFiles.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

        childFragmentManager.setFragmentResultListener(
            FileActionOptionsDialog.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            handleFileAction(result)
        }

        // Keep the selected segment thumb in sync with the segment size
        binding.layoutSortSegment.addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                v.post { moveSortThumb(animate = false) }
            }
        }

        updateFreeStorageSubtitle()
        setupListeners()
        setupSearch()
        updateSortTabs(animate = false)
        updateEditModeUI(animate = false)
        loadFileList()
    }

    override fun onResume() {
        super.onResume()
        // Files can change on other screens (passcode lock, cover saved after reading...)
        updateFreeStorageSubtitle()
        loadFileList()
        updateBackCallback()
    }

    override fun onPause() {
        super.onPause()
        // Another tab is shown (ViewPager2) or another screen is on top
        backPressedCallback.isEnabled = false
    }

    // region Header / nav bar

    private fun updateFreeStorageSubtitle() {
        val b = _binding ?: return
        val freeText = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            formatStorageSize(stat.availableBytes)
        } catch (_: Exception) {
            "--"
        }
        b.tvFreeStorageSubtitle.text = "${getString(R.string.free_space)}\n$freeText"
    }

    /** "14.67 GB" (decimal units, like iOS) */
    private fun formatStorageSize(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        return if (gb >= 1.0) {
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.2f MB", bytes / 1_000_000.0)
        }
    }

    private fun setupListeners() {
        binding.btnAddFile.setOnClickListener { checkPermissionAndImport() }
        binding.btnMainSearch.setOnClickListener { openSearch() }
        binding.btnEditFiles.setOnClickListener { setEditMode(!isEditMode) }
        binding.btnSelectAll.setOnClickListener { toggleSelectAll() }

        binding.tabSortName.setOnClickListener { selectSort(SortType.NAME) }
        binding.tabSortType.setOnClickListener { selectSort(SortType.TYPE) }
        binding.tabSortTime.setOnClickListener { selectSort(SortType.TIME) }

        binding.btnActionShare.setOnClickListener { shareFiles(selectedItems()) }
        binding.btnActionMove.setOnClickListener { showMoveToFolder() }
        binding.btnActionCopy.setOnClickListener { duplicateFiles(selectedItems()) }
        binding.btnActionZip.setOnClickListener { zipFiles(selectedItems()) }
        binding.btnActionDelete.setOnClickListener { deleteSelectedFiles() }
    }

    private fun updateBackCallback() {
        backPressedCallback.isEnabled = isResumed && (isEditMode || isSearchMode)
    }

    // endregion

    // region Search

    private fun setupSearch() {
        binding.etSearchFiles.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                _binding?.btnClearSearch?.isVisible = searchQuery.isNotEmpty()
                applySortAndFilter()
            }
        })
        binding.etSearchFiles.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v)
                true
            } else {
                false
            }
        }
        binding.btnClearSearch.setOnClickListener { binding.etSearchFiles.setText("") }
        binding.btnCancelSearch.setOnClickListener { closeSearch() }
    }

    private fun openSearch() {
        if (isEditMode || isSearchMode) return
        isSearchMode = true
        fadeView(binding.layoutNavNormal, visible = false, animate = true)
        fadeView(binding.layoutNavSearch, visible = true, animate = true)
        binding.etSearchFiles.requestFocus()
        showKeyboard(binding.etSearchFiles)
        updateBackCallback()
    }

    private fun closeSearch() {
        val b = _binding ?: return
        if (!isSearchMode) return
        isSearchMode = false
        hideKeyboard(b.etSearchFiles)
        b.etSearchFiles.setText("")
        b.etSearchFiles.clearFocus()
        fadeView(b.layoutNavSearch, visible = false, animate = true)
        fadeView(b.layoutNavNormal, visible = true, animate = true)
        updateBackCallback()
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // endregion

    // region Sort (segmented control)

    private fun selectSort(type: SortType) {
        if (activeSortType == type) return
        activeSortType = type
        updateSortTabs(animate = true)
        applySortAndFilter()
    }

    private fun updateSortTabs(animate: Boolean) {
        val b = _binding ?: return
        val index = activeSortType.ordinal
        listOf(b.tabSortName, b.tabSortType, b.tabSortTime).forEachIndexed { i, tab ->
            tab.typeface = if (i == index) TYPEFACE_SEMIBOLD else TYPEFACE_REGULAR
        }
        // iOS hides the dividers next to the selected segment
        b.viewSortDivider1.visibility = if (index == 2) View.VISIBLE else View.INVISIBLE
        b.viewSortDivider2.visibility = if (index == 0) View.VISIBLE else View.INVISIBLE
        moveSortThumb(animate)
    }

    private fun moveSortThumb(animate: Boolean) {
        val b = _binding ?: return
        val target = when (activeSortType) {
            SortType.NAME -> b.tabSortName
            SortType.TYPE -> b.tabSortType
            SortType.TIME -> b.tabSortTime
        }
        if (target.width == 0) return
        val thumb = b.viewSortThumb
        if (thumb.layoutParams.width != target.width) {
            thumb.layoutParams = thumb.layoutParams.apply { width = target.width }
        }
        // thumb and tabs container share the same origin (inside the segment padding)
        val x = target.left.toFloat()
        thumb.animate().cancel()
        if (animate) {
            thumb.animate()
                .translationX(x)
                .setDuration(SEGMENT_ANIM_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            thumb.translationX = x
        }
    }

    // endregion

    // region Edit mode

    private fun setEditMode(enabled: Boolean) {
        if (isEditMode == enabled || _binding == null) return
        isEditMode = enabled
        if (!enabled) selectedFileIds.clear()
        updateEditModeUI(animate = true)
        fileAdapter.notifyItemRangeChanged(0, fileAdapter.itemCount, PAYLOAD_EDIT_MODE)
        updateBackCallback()
    }

    private fun updateEditModeUI(animate: Boolean) {
        val b = _binding ?: return
        b.btnEditFiles.setText(if (isEditMode) R.string.done else R.string.edit)
        b.btnEditFiles.typeface = if (isEditMode) TYPEFACE_SEMIBOLD else TYPEFACE_REGULAR

        // Left button: Search <-> Select all
        fadeView(b.btnMainSearch, visible = !isEditMode, animate = animate)
        fadeView(b.btnSelectAll, visible = isEditMode, animate = animate)
        // "+" disappears in edit mode
        fadeView(b.btnAddFile, visible = !isEditMode, animate = animate, hiddenVisibility = View.INVISIBLE)
        // Free space + sort control disappear, the gray band stays
        fadeView(b.layoutSortHeader, visible = !isEditMode, animate = animate, hiddenVisibility = View.INVISIBLE)
        b.layoutSortHeader.isEnabled = !isEditMode
        b.tabSortName.isEnabled = !isEditMode
        b.tabSortType.isEnabled = !isEditMode
        b.tabSortTime.isEnabled = !isEditMode

        showEditActionBar(isEditMode, animate)
        updateSelectionUI()
    }

    private fun showEditActionBar(visible: Boolean, animate: Boolean) {
        val b = _binding ?: return
        val bar = b.layoutEditActionBar
        bar.animate().cancel()
        val barHeight = if (bar.height > 0) bar.height.toFloat() else ACTION_BAR_HEIGHT_DP * resources.displayMetrics.density

        if (!animate) {
            bar.translationY = 0f
            bar.visibility = if (visible) View.VISIBLE else View.GONE
        } else if (visible) {
            bar.visibility = View.VISIBLE
            bar.translationY = barHeight
            bar.animate()
                .translationY(0f)
                .setDuration(EDIT_ANIM_DURATION)
                .setInterpolator(editInterpolator)
                .start()
        } else {
            bar.animate()
                .translationY(barHeight)
                .setDuration(EDIT_ANIM_DURATION)
                .setInterpolator(editInterpolator)
                .withEndAction {
                    bar.visibility = View.GONE
                    bar.translationY = 0f
                }
                .start()
        }

        // Keep the last row reachable above the bar
        b.rvMainFiles.setPadding(
            b.rvMainFiles.paddingLeft,
            b.rvMainFiles.paddingTop,
            b.rvMainFiles.paddingRight,
            if (visible) barHeight.toInt() else 0
        )
    }

    private fun updateSelectionUI() {
        val b = _binding ?: return
        val hasSelection = isEditMode && selectedFileIds.isNotEmpty()
        listOf(
            b.btnActionShare,
            b.btnActionMove,
            b.btnActionCopy,
            b.btnActionZip,
            b.btnActionDelete
        ).forEach {
            it.isEnabled = hasSelection
            it.alpha = if (hasSelection) 1f else DISABLED_ALPHA
        }
    }

    private fun toggleSelectAll() {
        if (!isEditMode || fileList.isEmpty()) return
        val allSelected = fileList.all { selectedFileIds.contains(it.id) }
        if (allSelected) {
            selectedFileIds.clear()
        } else {
            fileList.forEach { selectedFileIds.add(it.id) }
        }
        fileAdapter.notifyItemRangeChanged(0, fileAdapter.itemCount, PAYLOAD_SELECTION)
        updateSelectionUI()
    }

    private fun toggleSelection(item: ImportedFileItem, position: Int) {
        if (!selectedFileIds.add(item.id)) {
            selectedFileIds.remove(item.id)
        }
        fileAdapter.notifyItemChanged(position, PAYLOAD_SELECTION)
        updateSelectionUI()
    }

    private fun selectedItems(): List<ImportedFileItem> =
        allFiles.filter { selectedFileIds.contains(it.id) }

    private fun fadeView(
        view: View,
        visible: Boolean,
        animate: Boolean,
        hiddenVisibility: Int = View.GONE
    ) {
        view.animate().cancel()
        if (!animate) {
            view.alpha = if (visible) 1f else 0f
            view.visibility = if (visible) View.VISIBLE else hiddenVisibility
            return
        }
        if (visible) {
            if (view.visibility != View.VISIBLE) {
                view.alpha = 0f
                view.visibility = View.VISIBLE
            }
            view.animate().alpha(1f).setDuration(FADE_DURATION).start()
        } else {
            if (view.visibility != View.VISIBLE) return
            view.animate()
                .alpha(0f)
                .setDuration(FADE_DURATION)
                .withEndAction { view.visibility = hiddenVisibility }
                .start()
        }
    }

    // endregion

    // region List data

    private fun loadFileList() {
        allFiles = FileItemManager.instance.getFiles()
            .filterNot { it.id.startsWith("sample_") }
        applySortAndFilter()
    }

    private fun applySortAndFilter() {
        allFiles = when (activeSortType) {
            SortType.NAME -> allFiles.sortedWith(
                compareBy<ImportedFileItem> { nameSortKey(it.fileName) }.thenBy { it.fileName }
            )
            SortType.TYPE -> allFiles.sortedWith(
                compareBy<ImportedFileItem> { it.extension.lowercase(Locale.ROOT) }
                    .thenBy { nameSortKey(it.fileName) }
            )
            SortType.TIME -> allFiles.sortedWith(
                compareByDescending<ImportedFileItem> { importedAt(it) }
                    .thenBy { nameSortKey(it.fileName) }
            )
        }

        val query = normalizeText(searchQuery.trim())
        fileList = if (query.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { normalizeText(it.fileName).contains(query) }
        }

        val existingIds = allFiles.map { it.id }.toSet()
        selectedFileIds.retainAll(existingIds)

        val b = _binding ?: return
        fileAdapter.submit(fileList)
        val isEmpty = fileList.isEmpty()
        b.layoutEmptyState.isVisible = isEmpty
        b.tvEmptyState.setText(if (searchQuery.isNotBlank()) R.string.no_results else R.string.no_files_yet)
        updateSelectionUI()
    }

    /** Lowercase without Vietnamese accents ("Tưởng" -> "tuong") for searching */
    private fun normalizeText(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
    }

    /** Same order as the iOS app: "ImportFiles(1).txt", "ImportFiles.txt", "[Dịch] ..." */
    private fun nameSortKey(name: String): String = normalizeText(name).uppercase(Locale.ROOT)

    private fun importedAt(item: ImportedFileItem): Long {
        item.id.removePrefix("file_").substringBefore('_').toLongOrNull()?.let { return it }
        return try {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).parse(item.dateFormatted)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun todayString(): String = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())

    /** Old items were saved as "2.3 kB" – show them like the iOS app: "2.3KB" */
    private fun displaySize(raw: String): String = raw.replace(" ", "").replace("kB", "KB")

    // endregion

    // region Import

    private fun checkPermissionAndImport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchFilePicker()
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
                launchFilePicker()
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun launchFilePicker() {
        try {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/epub+zip",
                    "text/plain",
                    "application/pdf",
                    "*/*"
                )
            )
        } catch (_: Exception) {
            toast(getString(R.string.error_launch_file_picker))
        }
    }

    private fun importFileFromUri(uri: Uri) {
        val context = context ?: return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        var name = "UnknownFile"
        var sizeBytes = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) name = cursor.getString(nameIdx) ?: "UnknownFile"
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        } catch (_: Exception) {
        }

        val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase(Locale.ROOT) else "epub"

        val item = ImportedFileItem(
            id = "file_${System.currentTimeMillis()}",
            fileName = name,
            fileUriStr = uri.toString(),
            fileSizeFormatted = if (sizeBytes > 0) LocalFileStore.formatFileSize(sizeBytes) else "--",
            dateFormatted = todayString(),
            extension = ext
        )

        FileItemManager.instance.addFile(item)
        PreferencesHelper.getInstance().putString("last_opened_epub_uri", uri.toString())

        if (ext == "epub") {
            parseAndOpenEpub(item)
        } else {
            loadFileList()
            toast(getString(R.string.added_file, name))
        }
    }

    // endregion

    // region Open

    private fun onFileClicked(item: ImportedFileItem) {
        val passcode = item.passcode
        if (passcode.isNullOrEmpty()) {
            openFile(item)
            return
        }
        if (childFragmentManager.findFragmentByTag(PasscodeEntryDialog.TAG) != null) return
        PasscodeEntryDialog(PasscodeEntryDialog.Mode.VERIFY) { code ->
            if (code == passcode) {
                openFile(item)
            } else {
                toast(getString(R.string.wrong_password_exclamation))
            }
        }.show(childFragmentManager, PasscodeEntryDialog.TAG)
    }

    private fun openFile(item: ImportedFileItem) {
        if (item.fileUriStr.isBlank()) {
            toast(getString(R.string.file_not_found, item.fileName))
            return
        }
        when (item.extension.lowercase(Locale.ROOT)) {
            "epub" -> parseAndOpenEpub(item)
            "txt" -> openTextFile(item)
            else -> openWithOtherApp(item)
        }
    }

    private fun parseAndOpenEpub(item: ImportedFileItem) {
        val appContext = context?.applicationContext ?: return
        val uri = Uri.parse(item.fileUriStr)
        viewLifecycleOwner.lifecycleScope.launch {
            val epubBook = withContext(Dispatchers.IO) {
                try {
                    EpubParser.parse(appContext, uri)
                } catch (_: Exception) {
                    null
                }
            }
            if (epubBook == null) {
                toast(getString(R.string.error_read_epub))
                return@launch
            }

            BookHolder.currentBook = epubBook

            val cover = epubBook.coverBitmap
            if (cover != null) {
                val base64 = withContext(Dispatchers.Default) { bitmapToBase64(cover) }
                val existingItem = FileItemManager.instance.getFiles()
                    .firstOrNull { it.id == item.id || it.fileUriStr == item.fileUriStr }
                if (existingItem != null && base64.isNotEmpty() && existingItem.coverBase64 != base64) {
                    FileItemManager.instance.addFile(existingItem.copy(coverBase64 = base64))
                }
            }

            loadFileList()
            startActivity(Intent(requireContext(), ReaderActivity::class.java))
        }
    }

    /** TXT files are opened in the same reader (one "chapter" per ~20k characters) */
    private fun openTextFile(item: ImportedFileItem) {
        val appContext = context?.applicationContext ?: return
        val uri = Uri.parse(item.fileUriStr)
        val partFormat = getString(R.string.txt_part)
        viewLifecycleOwner.lifecycleScope.launch {
            val book = withContext(Dispatchers.IO) {
                buildBookFromText(appContext, uri, item.fileName, partFormat)
            }
            if (book == null || book.chapters.isEmpty()) {
                toast(getString(R.string.cannot_open_file, item.fileName))
                return@launch
            }
            BookHolder.currentBook = book
            startActivity(Intent(requireContext(), ReaderActivity::class.java))
        }
    }

    private fun buildBookFromText(
        context: Context,
        uri: Uri,
        fileName: String,
        partFormat: String
    ): EpubBook? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return null

        val text = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }

        val paragraphs = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return null

        val title = fileName.substringBeforeLast('.', fileName)
        val chapters = mutableListOf<EpubChapter>()
        val html = StringBuilder()

        fun flushChapter() {
            if (html.isEmpty()) return
            val index = chapters.size + 1
            chapters += EpubChapter(
                id = "txt_$index",
                title = String.format(Locale.getDefault(), partFormat, index),
                href = "txt_$index.xhtml",
                contentHtml = html.toString()
            )
            html.setLength(0)
        }

        paragraphs.forEach { paragraph ->
            html.append("<p>").append(escapeHtml(paragraph)).append("</p>\n")
            if (html.length >= TXT_CHAPTER_LENGTH) flushChapter()
        }
        flushChapter()

        if (chapters.size == 1) {
            chapters[0] = chapters[0].copy(title = title)
        }

        return EpubBook(
            title = title,
            author = "",
            chapters = chapters,
            filePath = uri.toString()
        )
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun openWithOtherApp(item: ImportedFileItem) {
        val uri = shareableUri(item)
        if (uri == null) {
            toast(getString(R.string.file_not_found, item.fileName))
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(item, uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.cannot_open_file, item.fileName))
        } catch (_: Exception) {
            toast(getString(R.string.cannot_open_file, item.fileName))
        }
    }

    // endregion

    // region File actions

    private fun showFileActionOptionsDialog(item: ImportedFileItem) {
        if (childFragmentManager.findFragmentByTag(FileActionOptionsDialog.TAG) != null) return
        FileActionOptionsDialog.newInstance(item)
            .show(childFragmentManager, FileActionOptionsDialog.TAG)
    }

    private fun handleFileAction(result: Bundle) {
        val fileId = result.getString(FileActionOptionsDialog.RESULT_FILE_ID) ?: return
        val actionName = result.getString(FileActionOptionsDialog.RESULT_ACTION) ?: return
        val action = FileActionOptionsDialog.Action.values().firstOrNull { it.name == actionName } ?: return
        val item = FileItemManager.instance.getFiles().firstOrNull { it.id == fileId } ?: return

        when (action) {
            FileActionOptionsDialog.Action.RENAME -> showRenameDialog(item)
            FileActionOptionsDialog.Action.MOVE -> showMoveToFolder()
            FileActionOptionsDialog.Action.SEND_OPEN -> shareFiles(listOf(item))
            FileActionOptionsDialog.Action.UPLOAD_DRIVE -> uploadToGoogleDrive(item)
            FileActionOptionsDialog.Action.COPY -> duplicateFiles(listOf(item))
            FileActionOptionsDialog.Action.DELETE -> {
                deleteFile(item)
                selectedFileIds.remove(item.id)
                loadFileList()
                toast(getString(R.string.deleted_file, item.fileName))
            }
        }
    }

    private fun showRenameDialog(target: ImportedFileItem) {
        inputDialog?.dismiss()
        inputDialog = IosAlertDialogs.showInput(
            context = requireContext(),
            title = getString(R.string.rename),
            initialText = target.fileName,
            positiveText = getString(R.string.save)
        ) { newName ->
            if (newName == target.fileName || _binding == null) return@showInput
            val otherNames = FileItemManager.instance.getFiles()
                .filter { it.id != target.id }
                .map { it.fileName }
            val finalName = LocalFileStore.uniqueFileName(newName, otherNames)
            FileItemManager.instance.addFile(target.copy(fileName = finalName))
            loadFileList()
            toast(getString(R.string.renamed_to, finalName))
        }
    }

    private fun showMoveToFolder() {
        toast(getString(R.string.move_files))
    }

    private fun shareFiles(items: List<ImportedFileItem>) {
        if (items.isEmpty()) return
        val pairs = items.mapNotNull { item -> shareableUri(item)?.let { item to it } }
        if (pairs.isEmpty()) {
            toast(getString(R.string.file_not_found, items.first().fileName))
            return
        }
        val uris = ArrayList(pairs.map { it.second })

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeOf(pairs.first().first, uris.first())
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

        val title = if (items.size == 1) {
            items.first().fileName
        } else {
            getString(R.string.share_multiple_files, items.size.toString())
        }
        try {
            startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
            toast(getString(R.string.cannot_open_file, items.first().fileName))
        }
    }

    private fun uploadToGoogleDrive(item: ImportedFileItem) {
        val uri = shareableUri(item)
        if (uri == null) {
            toast(getString(R.string.file_not_found, item.fileName))
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeOf(item, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(GOOGLE_DRIVE_PACKAGE)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Google Drive not installed -> normal share sheet
            shareFiles(listOf(item))
        } catch (_: Exception) {
            shareFiles(listOf(item))
        }
    }

    /** "Sao chép": duplicates the files -> "Name(1).ext" (stored in the app folder) */
    private fun duplicateFiles(items: List<ImportedFileItem>) {
        if (items.isEmpty()) return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val failedNames = mutableListOf<String>()
            val created = withContext(Dispatchers.IO) {
                val usedNames = FileItemManager.instance.getFiles().map { it.fileName }.toMutableSet()
                val baseTime = System.currentTimeMillis()
                items.mapIndexedNotNull { index, item ->
                    val timestamp = baseTime + index
                    val newName = LocalFileStore.uniqueFileName(item.fileName, usedNames)
                    val dest = LocalFileStore.newFileFor(appContext, newName, timestamp)
                    val source = try {
                        Uri.parse(item.fileUriStr)
                    } catch (_: Exception) {
                        null
                    }
                    if (source == null || !LocalFileStore.copyUriToFile(appContext, source, dest)) {
                        failedNames += item.fileName
                        null
                    } else {
                        usedNames += newName
                        item.copy(
                            id = "file_$timestamp",
                            fileName = newName,
                            fileUriStr = Uri.fromFile(dest).toString(),
                            fileSizeFormatted = LocalFileStore.formatFileSize(dest.length()),
                            dateFormatted = todayString()
                        )
                    }
                }
            }

            created.forEach { FileItemManager.instance.addFile(it) }
            if (isEditMode) setEditMode(false)
            loadFileList()

            if (failedNames.isEmpty()) {
                toast(getString(R.string.copied_files, created.size))
            } else {
                toast(getString(R.string.cannot_copy_file, failedNames.first()))
            }
        }
    }

    /** "Zip": one file -> "Name.zip", several files -> "Archive.zip" */
    private fun zipFiles(items: List<ImportedFileItem>) {
        if (items.isEmpty()) return
        val appContext = context?.applicationContext ?: return
        val archiveBase = if (items.size == 1) {
            items.first().fileName.substringBeforeLast('.', items.first().fileName)
        } else {
            getString(R.string.archive_name)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val created = withContext(Dispatchers.IO) {
                val usedNames = FileItemManager.instance.getFiles().map { it.fileName }
                val zipName = LocalFileStore.uniqueFileName("$archiveBase.zip", usedNames)
                val timestamp = System.currentTimeMillis()
                val dest = LocalFileStore.newFileFor(appContext, zipName, timestamp)
                val sources = items.mapNotNull { item ->
                    try {
                        item.fileName to Uri.parse(item.fileUriStr)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (LocalFileStore.zip(appContext, sources, dest)) {
                    ImportedFileItem(
                        id = "file_$timestamp",
                        fileName = zipName,
                        fileUriStr = Uri.fromFile(dest).toString(),
                        fileSizeFormatted = LocalFileStore.formatFileSize(dest.length()),
                        dateFormatted = todayString(),
                        extension = "zip"
                    )
                } else {
                    null
                }
            }

            if (created == null) {
                toast(getString(R.string.zip_failed))
                return@launch
            }
            FileItemManager.instance.addFile(created)
            if (isEditMode) setEditMode(false)
            loadFileList()
            toast(getString(R.string.zipped_to, created.fileName))
        }
    }

    private fun deleteSelectedFiles() {
        val items = selectedItems()
        if (items.isEmpty()) {
            toast(getString(R.string.select_at_least_one_file))
            return
        }
        items.forEach { deleteFile(it) }
        selectedFileIds.clear()
        setEditMode(false)
        loadFileList()
        toast(getString(R.string.deleted_selected_files))
    }

    private fun deleteFile(item: ImportedFileItem) {
        FileItemManager.instance.removeFile(item.id)
        val uri = try {
            Uri.parse(item.fileUriStr)
        } catch (_: Exception) {
            null
        }
        // Only our own copies are deleted from disk; imported files stay untouched
        context?.let { LocalFileStore.deleteLocalCopy(it, uri) }
    }

    /** content:// is shared as is; our own file:// copies go through FileProvider */
    private fun shareableUri(item: ImportedFileItem): Uri? {
        val ctx = context ?: return null
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
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun mimeTypeOf(item: ImportedFileItem, uri: Uri? = null): String {
        if (uri != null && uri.scheme == "content") {
            val type = try {
                context?.contentResolver?.getType(uri)
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

    // endregion

    // region Helpers

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun coverFor(item: ImportedFileItem): Bitmap? {
        if (!SHOW_BOOK_COVERS) return null
        val base64 = item.coverBase64
        if (base64.isNullOrBlank()) return null
        val key = "${item.id}:${base64.length}"
        coverCache.get(key)?.let { return it }
        return base64ToBitmap(base64)?.also { coverCache.put(key, it) }
    }

    // endregion

    private inner class FileAdapter : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        private var items: List<ImportedFileItem> = emptyList()

        inner class FileViewHolder(val binding: ItemMainFileBinding) : RecyclerView.ViewHolder(binding.root)

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<ImportedFileItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val itemBinding = ItemMainFileBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return FileViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            bind(holder, position, emptyList())
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: MutableList<Any>) {
            bind(holder, position, payloads)
        }

        private fun bind(holder: FileViewHolder, position: Int, payloads: List<Any>) {
            if (position !in items.indices) return
            val item = items[position]
            when {
                payloads.isEmpty() -> {
                    bindContent(holder, item)
                    bindSelection(holder, item)
                    bindEditOffset(holder, animate = false)
                }
                payloads.contains(PAYLOAD_EDIT_MODE) -> {
                    bindSelection(holder, item)
                    bindEditOffset(holder, animate = true)
                }
                else -> bindSelection(holder, item)
            }
        }

        private fun bindContent(holder: FileViewHolder, item: ImportedFileItem) {
            val b = holder.binding
            b.tvFileName.text = item.fileName
            b.tvFileSize.text = displaySize(item.fileSizeFormatted)
            b.tvFileDate.text = item.dateFormatted
            b.imgFileLock.isVisible = !item.passcode.isNullOrEmpty()
            bindFileIcon(b, item)

            b.root.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in items.indices) return@setOnClickListener
                val current = items[pos]
                if (isEditMode) {
                    toggleSelection(current, pos)
                } else {
                    onFileClicked(current)
                }
            }

            // Long press = enter edit mode with this file selected
            b.root.setOnLongClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in items.indices || isEditMode || isSearchMode) {
                    return@setOnLongClickListener false
                }
                selectedFileIds.add(items[pos].id)
                setEditMode(true)
                true
            }

            b.btnFileInfo.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION || pos !in items.indices || isEditMode) {
                    return@setOnClickListener
                }
                showFileActionOptionsDialog(items[pos])
            }
        }

        private fun bindFileIcon(b: ItemMainFileBinding, item: ImportedFileItem) {
            val ext = item.extension.lowercase(Locale.ROOT)
            b.tvFileBadge.text = ext.uppercase(Locale.ROOT).ifEmpty { "FILE" }

            val (strokeColorRes, fillColorRes) = when (ext) {
                "pdf" -> R.color.file_red to R.color.file_red_fill
                "doc", "docx", "rtf", "odt", "pages" -> R.color.file_blue to R.color.file_blue_fill
                "zip", "rar", "7z", "tar", "gz" -> R.color.file_orange to R.color.file_orange_fill
                else -> R.color.file_green to R.color.file_green_fill
            }
            val ctx = b.root.context
            val strokeColor = ContextCompat.getColor(ctx, strokeColorRes)
            ImageViewCompat.setImageTintList(b.imgFileIconStroke, ColorStateList.valueOf(strokeColor))
            ImageViewCompat.setImageTintList(
                b.imgFileIconFill,
                ColorStateList.valueOf(ContextCompat.getColor(ctx, fillColorRes))
            )
            ViewCompat.setBackgroundTintList(b.tvFileBadge, ColorStateList.valueOf(strokeColor))

            val cover = coverFor(item)
            if (cover != null) {
                b.imgCoverAvatar.setImageBitmap(cover)
                b.imgCoverAvatar.isVisible = true
                b.layoutFileIcon.isVisible = false
            } else {
                b.imgCoverAvatar.setImageDrawable(null)
                b.imgCoverAvatar.isVisible = false
                b.layoutFileIcon.isVisible = true
            }
        }

        /** Check circle, gray background, blue corner and separator of a row */
        private fun bindSelection(holder: FileViewHolder, item: ImportedFileItem) {
            val b = holder.binding
            val isSelected = isEditMode && selectedFileIds.contains(item.id)
            b.imgSelectCheck.setImageResource(
                if (isSelected) R.drawable.ic_check_circle_selected else R.drawable.ic_check_circle_unselected
            )
            b.root.isActivated = isSelected
            b.imgSelectedCorner.isVisible = isSelected
            b.viewRowSeparator.visibility = if (isSelected) View.INVISIBLE else View.VISIBLE
            // In edit mode the (i) button is pushed out of the row: let taps reach the row
            b.btnFileInfo.isEnabled = !isEditMode
            b.btnFileInfo.isClickable = !isEditMode
        }

        /** Edit mode: content slides right, check circle slides in, (i) fades out */
        private fun bindEditOffset(holder: FileViewHolder, animate: Boolean) {
            val b = holder.binding
            val direction = if (b.root.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
            val contentX = if (isEditMode) editShiftPx * direction else 0f
            val checkX = if (isEditMode) 0f else -editShiftPx * direction
            val checkAlpha = if (isEditMode) 1f else 0f
            val infoAlpha = if (isEditMode) 0f else 1f

            b.layoutRowContent.animate().cancel()
            b.imgSelectCheck.animate().cancel()
            b.btnFileInfo.animate().cancel()

            if (animate) {
                b.layoutRowContent.animate()
                    .translationX(contentX)
                    .setDuration(EDIT_ANIM_DURATION)
                    .setInterpolator(editInterpolator)
                    .start()
                b.imgSelectCheck.animate()
                    .translationX(checkX)
                    .alpha(checkAlpha)
                    .setDuration(EDIT_ANIM_DURATION)
                    .setInterpolator(editInterpolator)
                    .start()
                b.btnFileInfo.animate()
                    .alpha(infoAlpha)
                    .setDuration(EDIT_ANIM_DURATION)
                    .start()
            } else {
                b.layoutRowContent.translationX = contentX
                b.imgSelectCheck.translationX = checkX
                b.imgSelectCheck.alpha = checkAlpha
                b.btnFileInfo.alpha = infoAlpha
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // PasscodeEntryDialog takes a lambda in its constructor and cannot be re-created by the
        // FragmentManager after a configuration change -> remove it before the state is saved.
        childFragmentManager.findFragmentByTag(PasscodeEntryDialog.TAG)?.let { dialog ->
            try {
                childFragmentManager.beginTransaction().remove(dialog).commitNowAllowingStateLoss()
            } catch (_: Exception) {
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        inputDialog?.dismiss()
        inputDialog = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PAYLOAD_EDIT_MODE = "payload_edit_mode"
        private const val PAYLOAD_SELECTION = "payload_selection"

        /** Row content shift in edit mode (UITableView editing indent) */
        private const val EDIT_SHIFT_DP = 40f
        private const val ACTION_BAR_HEIGHT_DP = 59f
        private const val EDIT_ANIM_DURATION = 280L
        private const val FADE_DURATION = 200L
        private const val SEGMENT_ANIM_DURATION = 250L
        private const val DISABLED_ALPHA = 0.35f

        private const val TXT_CHAPTER_LENGTH = 20_000
        private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

        /** EPUB covers replace the generic icon after the book has been opened once. Set to false for icons only. */
        private const val SHOW_BOOK_COVERS = true

        private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
        private val TYPEFACE_SEMIBOLD: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        private val TYPEFACE_REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
}
