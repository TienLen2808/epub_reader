package com.example.epub_reader.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogReaderSearchSheetBinding
import com.example.epub_reader.databinding.ItemSearchResultEntryBinding
import com.example.epub_reader.databinding.ItemSearchSectionHeaderBinding
import com.example.epub_reader.epub.EpubBook
import com.example.epub_reader.utils.ReaderSearchHelper
import com.example.epub_reader.utils.SearchResultItem
import com.example.epub_reader.utils.SearchResultSection
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ReaderSearchBottomSheet(
    private val book: EpubBook?,
    private val onSearchResultClicked: (chapterIndex: Int, keyword: String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogReaderSearchSheetBinding? = null
    private val binding get() = _binding!!

    private val searchHelper = ReaderSearchHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogReaderSearchSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return

        val displayMetrics = resources.displayMetrics
        val targetHeight = (displayMetrics.heightPixels * 0.95).toInt()

        bottomSheet.layoutParams.height = targetHeight
        bottomSheet.requestLayout()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = targetHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancelTop.setOnClickListener { dismiss() }

        binding.btnSubmitSearch.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotBlank()) {
                performSearch(query)
                hideKeyboard()
            }
        }

        binding.btnSearchIcon.setOnClickListener {
            val query = binding.etSearchQuery.text.toString().trim()
            if (query.isNotBlank()) {
                performSearch(query)
                hideKeyboard()
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchQuery.setText("")
        }

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etSearchQuery.text.toString().trim()
                if (query.isNotBlank()) {
                    performSearch(query)
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearchQuery.windowToken, 0)
    }

    private fun performSearch(query: String) {
        val sections = searchHelper.searchBook(book, query) { number ->
            getString(R.string.chapter_number, number)
        }
        binding.rvSearchResults.adapter = SearchAdapter(sections)
    }

    inner class SearchAdapter(sections: List<SearchResultSection>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val typeHeader = 0
        private val typeItem = 1

        private val flatList = mutableListOf<Any>()

        init {
            sections.forEach { section ->
                flatList.add(section.sectionHeader)
                flatList.addAll(section.results)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (flatList[position] is String) typeHeader else typeItem
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == typeHeader) {
                val binding = ItemSearchSectionHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                HeaderViewHolder(binding)
            } else {
                val binding = ItemSearchResultEntryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ItemViewHolder(binding)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderViewHolder) {
                holder.binding.tvSectionHeaderTitle.text = flatList[position] as String
            } else if (holder is ItemViewHolder) {
                val item = flatList[position] as SearchResultItem
                val snippet = item.snippetText
                val query = item.queryKeyword

                val spannable = SpannableString(snippet)
                val startIdx = snippet.indexOf(query, ignoreCase = true)
                if (startIdx >= 0) {
                    val endIdx = startIdx + query.length
                    val orangeColor = android.graphics.Color.parseColor("#F57C00")
                    spannable.setSpan(
                        ForegroundColorSpan(orangeColor),
                        startIdx,
                        endIdx,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                holder.binding.tvSearchResultSnippet.text = spannable

                holder.itemView.setOnClickListener {
                    onSearchResultClicked(item.chapterIndex, item.queryKeyword)
                    dismiss()
                }
            }
        }

        override fun getItemCount(): Int = flatList.size

        inner class HeaderViewHolder(val binding: ItemSearchSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
        inner class ItemViewHolder(val binding: ItemSearchResultEntryBinding) : RecyclerView.ViewHolder(binding.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReaderSearchBottomSheet"
    }
}
