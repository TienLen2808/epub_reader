package com.example.epub_reader.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogReaderTocSheetBinding
import com.example.epub_reader.databinding.ItemReaderBookmarkBinding
import com.example.epub_reader.databinding.ItemReaderTocChapterBinding
import com.example.epub_reader.epub.EpubChapter
import com.example.epub_reader.utils.BookmarkItem
import com.example.epub_reader.utils.BookmarkManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class ReaderTocBottomSheet(
    private val bookTitle: String,
    private val chapters: List<EpubChapter>,
    private val activeChapterIndex: Int,
    private val globalCurrentPage: Int,
    private val globalTotalPages: Int,
    private val chapterPageCounts: IntArray,
    private val onChapterSelected: (Int) -> Unit,
    private val onBookmarkSelected: (BookmarkItem) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogReaderTocSheetBinding? = null
    private val binding get() = _binding!!

    private var selectedTab = TabType.TOC

    enum class TabType {
        TOC, NOTES, BOOKMARKS
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReaderTocSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return

        val displayMetrics = resources.displayMetrics
        val targetHeight = (displayMetrics.heightPixels * 0.82).toInt()

        bottomSheet.layoutParams.height = targetHeight
        bottomSheet.requestLayout()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = targetHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTocBookTitle.text = bookTitle
        val percentStr = String.format(Locale.US, "%.2f%%", (globalCurrentPage.toDouble() / globalTotalPages.toDouble() * 100))
        binding.tvTocProgressSubtitle.text = "$globalCurrentPage/$globalTotalPages  $percentStr"

        binding.btnCloseToc.setOnClickListener {
            dismiss()
        }

        binding.rvTocChapters.layoutManager = LinearLayoutManager(requireContext())

        setupTabs()
        showTocList()
    }

    private fun setupTabs() {
        binding.tabToc.setOnClickListener {
            selectedTab = TabType.TOC
            updateTabBackgrounds()
            showTocList()
        }
        binding.tabNotes.setOnClickListener {
            selectedTab = TabType.NOTES
            updateTabBackgrounds()
            showNotesList()
        }
        binding.tabBookmarks.setOnClickListener {
            selectedTab = TabType.BOOKMARKS
            updateTabBackgrounds()
            showBookmarksList()
        }
    }

    private fun updateTabBackgrounds() {
        binding.tabToc.setBackgroundResource(if (selectedTab == TabType.TOC) R.drawable.bg_segmented_tab_active else 0)
        binding.tabNotes.setBackgroundResource(if (selectedTab == TabType.NOTES) R.drawable.bg_segmented_tab_active else 0)
        binding.tabBookmarks.setBackgroundResource(if (selectedTab == TabType.BOOKMARKS) R.drawable.bg_segmented_tab_active else 0)
    }

    private fun showTocList() {
        val adapter = TocAdapter()
        binding.rvTocChapters.adapter = adapter
        if (activeChapterIndex in chapters.indices) {
            binding.rvTocChapters.scrollToPosition(activeChapterIndex)
        }
    }

    private fun showNotesList() {
        val bookmarks = BookmarkManager.instance.getBookmarks(bookTitle)
        binding.rvTocChapters.adapter = BookmarkAdapter(bookmarks)
    }

    private fun showBookmarksList() {
        val bookmarks = BookmarkManager.instance.getBookmarks(bookTitle)
        binding.rvTocChapters.adapter = BookmarkAdapter(bookmarks)
    }

    inner class TocAdapter : RecyclerView.Adapter<TocAdapter.TocViewHolder>() {

        inner class TocViewHolder(val binding: ItemReaderTocChapterBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TocViewHolder {
            val binding = ItemReaderTocChapterBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return TocViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TocViewHolder, position: Int) {
            val chapter = chapters[position]
            val titleStr = if (chapter.title.isNotBlank()) chapter.title else "Chương ${position + 1}"
            val isActive = position == activeChapterIndex

            holder.binding.tvTocChapterTitle.text = "${position + 1}. $titleStr"

            val startPage = if (position > 0 && position <= chapterPageCounts.size) {
                chapterPageCounts.take(position).sum() + 1
            } else 1
            holder.binding.tvTocStartPage.text = "$startPage"

            if (isActive) {
                val blueColor = android.graphics.Color.parseColor("#1E88E5")
                holder.binding.tvTocChapterTitle.setTextColor(blueColor)
                holder.binding.tvTocStartPage.setTextColor(blueColor)
                holder.binding.tvTocActiveGlobalPage.visibility = View.VISIBLE
                holder.binding.tvTocActiveGlobalPage.text = "$globalCurrentPage/$globalTotalPages"
            } else {
                val darkColor = android.graphics.Color.parseColor("#333333")
                val grayColor = android.graphics.Color.parseColor("#666666")
                holder.binding.tvTocChapterTitle.setTextColor(darkColor)
                holder.binding.tvTocStartPage.setTextColor(grayColor)
                holder.binding.tvTocActiveGlobalPage.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                onChapterSelected(position)
                dismiss()
            }
        }

        override fun getItemCount(): Int = chapters.size
    }

    inner class BookmarkAdapter(private val bookmarks: List<BookmarkItem>) : RecyclerView.Adapter<BookmarkAdapter.BookmarkViewHolder>() {

        inner class BookmarkViewHolder(val binding: ItemReaderBookmarkBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
            val binding = ItemReaderBookmarkBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return BookmarkViewHolder(binding)
        }

        override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
            val item = bookmarks[position]
            holder.binding.tvBookmarkSnippet.text = item.snippetText
            holder.binding.tvBookmarkChapter.text = "${item.chapterIndex + 1}. ${item.chapterTitle}"
            holder.binding.tvBookmarkTimestamp.text = item.timestampFormatted

            holder.itemView.setOnClickListener {
                onBookmarkSelected(item)
                dismiss()
            }
        }

        override fun getItemCount(): Int = bookmarks.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReaderTocBottomSheet"
    }
}
