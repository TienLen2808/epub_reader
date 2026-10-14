package com.example.epub_reader.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookmarkManager private constructor() {

    companion object {
        val instance: BookmarkManager by lazy { BookmarkManager() }
        private const val PREF_KEY_BOOKMARKS = "reader_bookmarks_json_"
    }

    private val gson = Gson()

    fun getBookmarks(bookTitle: String): List<BookmarkItem> {
        val jsonStr = PreferencesHelper.getInstance().getString(PREF_KEY_BOOKMARKS + bookTitle) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<BookmarkItem>>() {}.type
            gson.fromJson<List<BookmarkItem>>(jsonStr, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isBookmarked(bookTitle: String, chapterIndex: Int, pageIndexInChapter: Int): Boolean {
        val list = getBookmarks(bookTitle)
        return list.any { it.chapterIndex == chapterIndex && it.pageIndexInChapter == pageIndexInChapter }
    }

    fun toggleBookmark(
        bookTitle: String,
        chapterIndex: Int,
        chapterTitle: String,
        pageIndexInChapter: Int,
        globalPage: Int,
        globalTotalPages: Int,
        snippetText: String
    ): Boolean {
        val list = getBookmarks(bookTitle).toMutableList()
        val existingIndex = list.indexOfFirst { it.chapterIndex == chapterIndex && it.pageIndexInChapter == pageIndexInChapter }

        val isAdded: Boolean
        if (existingIndex >= 0) {
            list.removeAt(existingIndex)
            isAdded = false
        } else {
            val timeStr = SimpleDateFormat("HH:mm dd/MM/yy", Locale.getDefault()).format(Date())
            val item = BookmarkItem(
                id = "${chapterIndex}_${pageIndexInChapter}_${System.currentTimeMillis()}",
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                pageIndexInChapter = pageIndexInChapter,
                globalPage = globalPage,
                globalTotalPages = globalTotalPages,
                snippetText = snippetText,
                timestampFormatted = timeStr
            )
            list.add(0, item)
            isAdded = true
        }

        saveBookmarks(bookTitle, list)
        return isAdded
    }

    fun removeBookmark(bookTitle: String, bookmarkId: String) {
        val list = getBookmarks(bookTitle).toMutableList()
        list.removeAll { it.id == bookmarkId }
        saveBookmarks(bookTitle, list)
    }

    private fun saveBookmarks(bookTitle: String, list: List<BookmarkItem>) {
        val jsonStr = gson.toJson(list)
        PreferencesHelper.getInstance().putString(PREF_KEY_BOOKMARKS + bookTitle, jsonStr)
    }
}
