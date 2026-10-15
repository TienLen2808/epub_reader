package com.example.epub_reader.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.ReaderActivity
import com.example.epub_reader.databinding.FragmentFilesBinding
import com.example.epub_reader.databinding.ItemMainFileBinding
import com.example.epub_reader.epub.BookHolder
import com.example.epub_reader.epub.EpubParser
import com.example.epub_reader.ui.ReaderSearchBottomSheet
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.ImportedFileItem
import com.example.epub_reader.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private var fileList = mutableListOf<ImportedFileItem>()
    private var activeSortType = SortType.NAME

    private var isEditMode = false
    private val selectedFileIds = mutableSetOf<String>()

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
            Toast.makeText(
                requireContext(),
                "Storage permission is required to import files",
                Toast.LENGTH_SHORT
            ).show()
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

        binding.rvMainFiles.layoutManager = LinearLayoutManager(requireContext())

        updateFreeStorageSubtitle()
        setupListeners()
        loadFileList()
    }

    private fun updateFreeStorageSubtitle() {
        try {
            val stat = Environment.getDataDirectory()
            val freeBytes = stat.freeSpace
            val formatted = Formatter.formatShortFileSize(requireContext(), freeBytes)
            binding.tvFreeStorageSubtitle.text = "Không gian trống\n$formatted"
        } catch (_: Exception) {
            binding.tvFreeStorageSubtitle.text = "Không gian trống\n14.67 GB"
        }
    }

    private fun setupListeners() {
        binding.btnAddFile.setOnClickListener { checkPermissionAndImport() }

        binding.btnMainSearch.setOnClickListener {
            val currentBook = BookHolder.currentBook
            val sheet = ReaderSearchBottomSheet(
                book = currentBook,
                onSearchResultClicked = { _, _ ->
                    if (currentBook != null) {
                        val intent = Intent(requireContext(), ReaderActivity::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(requireContext(), "Hãy chọn một tập tin để mở", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            sheet.show(parentFragmentManager, ReaderSearchBottomSheet.TAG)
        }

        binding.btnEditFiles.setOnClickListener {
            toggleEditMode()
        }

        binding.btnSelectAll.setOnClickListener {
            if (selectedFileIds.size == fileList.size) {
                selectedFileIds.clear()
            } else {
                selectedFileIds.addAll(fileList.map { it.id })
            }
            binding.rvMainFiles.adapter?.notifyDataSetChanged()
        }

        binding.tabSortName.setOnClickListener {
            activeSortType = SortType.NAME
            updateSortTabs()
            sortAndRenderFileList()
        }

        binding.tabSortType.setOnClickListener {
            activeSortType = SortType.TYPE
            updateSortTabs()
            sortAndRenderFileList()
        }

        binding.tabSortTime.setOnClickListener {
            activeSortType = SortType.TIME
            updateSortTabs()
            sortAndRenderFileList()
        }

        binding.btnActionDelete.setOnClickListener {
            if (selectedFileIds.isNotEmpty()) {
                selectedFileIds.forEach { FileItemManager.instance.removeFile(it) }
                selectedFileIds.clear()
                isEditMode = false
                updateEditModeUI()
                loadFileList()
                Toast.makeText(requireContext(), "Đã xóa các tập tin được chọn", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Hãy chọn ít nhất 1 tập tin để xóa", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnActionShare.setOnClickListener {
            if (selectedFileIds.isNotEmpty()) {
                Toast.makeText(requireContext(), "Chia sẻ ${selectedFileIds.size} tập tin", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnActionMove.setOnClickListener {
            Toast.makeText(requireContext(), "Di chuyển tập tin", Toast.LENGTH_SHORT).show()
        }

        binding.btnActionCopy.setOnClickListener {
            Toast.makeText(requireContext(), "Sao chép tập tin", Toast.LENGTH_SHORT).show()
        }

        binding.btnActionZip.setOnClickListener {
            Toast.makeText(requireContext(), "Nén Zip tập tin", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (!isEditMode) {
            selectedFileIds.clear()
        }
        updateEditModeUI()
        binding.rvMainFiles.adapter?.notifyDataSetChanged()
    }

    private fun updateEditModeUI() {
        if (isEditMode) {
            binding.btnEditFiles.text = "Xong"
            binding.btnMainSearch.visibility = View.GONE
            binding.btnSelectAll.visibility = View.VISIBLE
            binding.layoutEditActionBar.visibility = View.VISIBLE
        } else {
            binding.btnEditFiles.text = "Sửa"
            binding.btnSelectAll.visibility = View.GONE
            binding.btnMainSearch.visibility = View.VISIBLE
            binding.layoutEditActionBar.visibility = View.GONE
        }
    }

    private fun updateSortTabs() {
        binding.tabSortName.setBackgroundResource(if (activeSortType == SortType.NAME) R.drawable.bg_segmented_tab_active else 0)
        binding.tabSortType.setBackgroundResource(if (activeSortType == SortType.TYPE) R.drawable.bg_segmented_tab_active else 0)
        binding.tabSortTime.setBackgroundResource(if (activeSortType == SortType.TIME) R.drawable.bg_segmented_tab_active else 0)
    }

    private fun loadFileList() {
        fileList = FileItemManager.instance.getFiles()
            .filterNot { it.id.startsWith("sample_") }
            .toMutableList()

        sortAndRenderFileList()
    }

    private fun sortAndRenderFileList() {
        when (activeSortType) {
            SortType.NAME -> fileList.sortBy { it.fileName.lowercase() }
            SortType.TYPE -> fileList.sortBy { it.extension.lowercase() }
            SortType.TIME -> fileList.sortByDescending { it.dateFormatted }
        }

        if (fileList.isEmpty()) {
            binding.rvMainFiles.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvMainFiles.visibility = View.VISIBLE
            binding.rvMainFiles.adapter = FileAdapter(fileList)
        }
    }

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
            Toast.makeText(requireContext(), "Cannot launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFileFromUri(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        var name = "UnknownFile"
        var sizeBytes = 0L

        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) name = cursor.getString(nameIdx) ?: "UnknownFile"
                if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val sizeStr = if (sizeBytes > 0) Formatter.formatShortFileSize(requireContext(), sizeBytes) else "1.0MB"
        val dateStr = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else "epub"

        val item = ImportedFileItem(
            id = "file_${System.currentTimeMillis()}",
            fileName = name,
            fileUriStr = uri.toString(),
            fileSizeFormatted = sizeStr,
            dateFormatted = dateStr,
            extension = ext
        )

        FileItemManager.instance.addFile(item)
        PreferencesHelper.getInstance().putString("last_opened_epub_uri", uri.toString())

        if (ext == "epub") {
            parseAndOpenEpub(uri)
        } else {
            loadFileList()
            Toast.makeText(requireContext(), "Đã thêm tệp $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFileActionOptionsDialog(item: ImportedFileItem) {
        val dialog = FileActionOptionsDialog(
            item = item,
            onRename = { target -> showRenameDialog(target) },
            onShare = { target -> shareFile(target) },
            onDelete = { target ->
                FileItemManager.instance.removeFile(target.id)
                loadFileList()
                Toast.makeText(requireContext(), "Đã xóa tệp ${target.fileName}", Toast.LENGTH_SHORT).show()
            }
        )
        dialog.show(parentFragmentManager, FileActionOptionsDialog.TAG)
    }

    private fun showRenameDialog(target: ImportedFileItem) {
        val input = EditText(requireContext())
        input.setText(target.fileName)
        input.setSelection(target.fileName.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Đổi tên tập tin")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    val updated = target.copy(fileName = newName)
                    FileItemManager.instance.addFile(updated)
                    loadFileList()
                    Toast.makeText(requireContext(), "Đã đổi tên thành $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun shareFile(target: ImportedFileItem) {
        if (target.fileUriStr.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(target.fileUriStr))
                }
                startActivity(Intent.createChooser(intent, "Gửi/Chia sẻ tập tin"))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Chia sẻ tệp ${target.fileName}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Chia sẻ tệp ${target.fileName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bitmapToBase64(bitmap: android.graphics.Bitmap): String {
        return try {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
            android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    private fun base64ToBitmap(base64Str: String): android.graphics.Bitmap? {
        return try {
            val bytes = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAndOpenEpub(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val epubBook = EpubParser.parse(requireContext(), uri)
            withContext(Dispatchers.Main) {
                if (epubBook != null) {
                    BookHolder.currentBook = epubBook

                    if (epubBook.coverBitmap != null) {
                        val base64 = bitmapToBase64(epubBook.coverBitmap)
                        val existingItem = FileItemManager.instance.getFiles().firstOrNull { it.fileUriStr == uri.toString() }
                        if (existingItem != null) {
                            val updatedItem = existingItem.copy(coverBase64 = base64)
                            FileItemManager.instance.addFile(updatedItem)
                        }
                    }

                    loadFileList()
                    val intent = Intent(requireContext(), ReaderActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Error: Could not read EPUB file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class FileAdapter(private val items: List<ImportedFileItem>) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        inner class FileViewHolder(val binding: ItemMainFileBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val binding = ItemMainFileBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return FileViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvFileName.text = item.fileName
            holder.binding.tvFileSize.text = item.fileSizeFormatted
            holder.binding.tvFileDate.text = item.dateFormatted
            holder.binding.tvFileBadge.text = item.extension.uppercase()

            if (isEditMode) {
                holder.binding.imgSelectCheck.visibility = View.VISIBLE
                holder.binding.btnFileInfo.visibility = View.GONE
                val isSelected = selectedFileIds.contains(item.id)
                holder.binding.imgSelectCheck.setImageResource(
                    if (isSelected) R.drawable.bg_circle_blue else R.drawable.bg_circle_white
                )
            } else {
                holder.binding.imgSelectCheck.visibility = View.GONE
                holder.binding.btnFileInfo.visibility = View.VISIBLE
            }

            val coverBitmap = if (!item.coverBase64.isNullOrBlank()) base64ToBitmap(item.coverBase64) else null
            if (coverBitmap != null) {
                holder.binding.imgCoverAvatar.setImageBitmap(coverBitmap)
                holder.binding.imgCoverAvatar.visibility = View.VISIBLE
                holder.binding.tvFileBadge.visibility = View.GONE
            } else {
                holder.binding.imgCoverAvatar.visibility = View.GONE
                holder.binding.tvFileBadge.visibility = View.VISIBLE
            }

            val badgeColor = when (item.extension.lowercase()) {
                "pdf" -> android.graphics.Color.parseColor("#E53935")
                "txt" -> android.graphics.Color.parseColor("#2E7D32")
                else -> android.graphics.Color.parseColor("#388E3C")
            }
            holder.binding.tvFileBadge.setTextColor(badgeColor)

            holder.itemView.setOnClickListener {
                if (isEditMode) {
                    if (selectedFileIds.contains(item.id)) {
                        selectedFileIds.remove(item.id)
                    } else {
                        selectedFileIds.add(item.id)
                    }
                    notifyItemChanged(position)
                } else {
                    if (item.fileUriStr.isNotBlank()) {
                        try {
                            val uri = Uri.parse(item.fileUriStr)
                            parseAndOpenEpub(uri)
                        } catch (_: Exception) {
                            Toast.makeText(requireContext(), "Mở tệp ${item.fileName}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Mở tệp ${item.fileName}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            holder.binding.btnFileInfo.setOnClickListener {
                showFileActionOptionsDialog(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
