package com.example.epub_reader.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogUsbTransferBinding
import com.example.epub_reader.databinding.ItemTransferFileBinding
import com.example.epub_reader.utils.LibraryImporter
import com.example.epub_reader.utils.LocalFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Chia sẻ tệp tin iTunes" on Android: the user copies files from a computer (USB, MTP)
 * into Android/data/<package>/files/Import, then imports them into "Các tập tin".
 * The folder is watched while this page is visible.
 */
class UsbTransferDialog : IosFullScreenDialogFragment() {

    private var _binding: DialogUsbTransferBinding? = null
    private val binding get() = _binding!!

    private var pendingFiles: List<File> = emptyList()
    private var lastSignature = ""
    private var importing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUsbTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root, binding.layoutTopBar, binding.layoutUsbBottom)
        fitNavigationTitle(
            bar = binding.layoutUsbNavBar,
            title = binding.tvUsbTitle,
            leading = binding.btnUsbBack,
            trailing = binding.btnUsbRefresh,
            backLabel = binding.tvUsbBackLabel
        )

        binding.btnUsbBack.setOnClickListener { dismiss() }
        binding.btnCopyPath.setOnClickListener { copyPath() }
        binding.btnUsbRefresh.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { loadPending(force = true) }
        }
        binding.btnUsbImport.setOnClickListener { importPending() }

        val appContext = requireContext().applicationContext
        // Read here: the activity context has the language chosen in the app
        val readmeText = getString(R.string.usb_readme_text) + "\n"
        viewLifecycleOwner.lifecycleScope.launch {
            val path = withContext(Dispatchers.IO) {
                // Help file, also makes the folder visible on the computer (MTP) right away
                LibraryImporter.writeUsbReadme(appContext, readmeText)?.let { readme ->
                    MediaScannerConnection.scanFile(appContext, arrayOf(readme.absolutePath), arrayOf("text/plain"), null)
                }
                LibraryImporter.usbInboxDisplayPath(appContext)
            }
            _binding?.tvUsbPath?.text = path
        }

        // Watch the folder while the page is visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    loadPending(force = false)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private suspend fun loadPending(force: Boolean) {
        val appContext = context?.applicationContext ?: return
        val files = withContext(Dispatchers.IO) {
            val dir = LibraryImporter.usbInboxDir(appContext) ?: return@withContext emptyList<File>()
            dir.listFiles()
                ?.filter { it.isFile && !it.isHidden && !LibraryImporter.isUsbReadme(it) }
                ?.sortedBy { it.name.lowercase(Locale.ROOT) }
                ?: emptyList()
        }
        val now = System.currentTimeMillis()
        val signature = files.joinToString("|") { "${it.name}:${it.length()}:${isStable(it, now)}" }
        pendingFiles = files
        if (force || signature != lastSignature) {
            lastSignature = signature
            renderPending(now)
        } else {
            updateImportButton(now)
        }
    }

    /** A file that is still being copied from the computer keeps changing: wait a few seconds */
    private fun isStable(file: File, now: Long): Boolean = now - file.lastModified() > STABLE_AFTER_MS

    private fun renderPending(now: Long) {
        val b = _binding ?: return
        val container = b.layoutPendingFiles
        // Keep only the "no file" text (index 0)
        while (container.childCount > 1) container.removeViewAt(1)
        b.tvNoPending.isVisible = pendingFiles.isEmpty()

        pendingFiles.forEachIndexed { index, file ->
            if (index > 0) container.addView(separator())
            val row = ItemTransferFileBinding.inflate(layoutInflater, container, false)
            row.imgTransferFileIcon.setImageResource(R.drawable.ic_file_doc_stroke)
            row.tvTransferFileName.text = file.name
            val size = LocalFileStore.formatFileSize(file.length())
            row.tvTransferFileSub.text = if (isStable(file, now)) size else "$size · ${getString(R.string.usb_copying)}"
            container.addView(row.root)
        }
        updateImportButton(now)
    }

    private fun updateImportButton(now: Long) {
        val b = _binding ?: return
        val count = pendingFiles.count { isStable(it, now) }
        b.btnUsbImport.isEnabled = count > 0 && !importing
        b.btnUsbImport.text = when {
            importing -> getString(R.string.transfer_importing)
            count > 0 -> getString(R.string.usb_import_count, count)
            else -> getString(R.string.usb_import_none)
        }
    }

    private fun importPending() {
        if (importing) return
        val appContext = context?.applicationContext ?: return
        val now = System.currentTimeMillis()
        val files = pendingFiles.filter { isStable(it, now) }
        if (files.isEmpty()) return

        importing = true
        updateImportButton(now)
        viewLifecycleOwner.lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                files.mapNotNull { LibraryImporter.importLocalFile(appContext, it, deleteSource = true) }
            }
            importing = false
            toast(
                if (imported.isEmpty()) {
                    getString(R.string.transfer_import_failed)
                } else {
                    getString(R.string.transfer_imported_files, imported.size)
                }
            )
            loadPending(force = true)
        }
    }

    private fun copyPath() {
        val text = _binding?.tvUsbPath?.text?.toString().orEmpty()
        if (text.isBlank() || text == "…") return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.itunes_file_sharing), text))
        toast(getString(R.string.path_copied))
    }

    private fun separator(): View {
        val density = resources.displayMetrics.density
        return View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                max(1, (0.6f * density).roundToInt())
            ).apply { marginStart = (50 * density).roundToInt() }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_row_separator))
        }
    }

    companion object {
        const val TAG = "UsbTransferDialog"
        private const val POLL_INTERVAL_MS = 2000L
        private const val STABLE_AFTER_MS = 3000L
    }
}
