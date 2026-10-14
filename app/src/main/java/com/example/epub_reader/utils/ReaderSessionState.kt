package com.example.epub_reader.utils

class ReaderSessionState {
    var currentChapterIndex: Int = 0
    var currentPageIndex: Int = 0
    var totalPagesInChapter: Int = 1
    var isPendingGoToLastPage: Boolean = false
    var chapterPageCounts: IntArray = intArrayOf()

    fun reset() {
        currentChapterIndex = 0
        currentPageIndex = 0
        totalPagesInChapter = 1
        isPendingGoToLastPage = false
        chapterPageCounts = intArrayOf()
    }

    fun calculateGlobalTotalPages(): Int {
        return chapterPageCounts.sum().coerceAtLeast(1)
    }

    fun calculateGlobalCurrentPage(): Int {
        val totalBookPages = calculateGlobalTotalPages()
        val previousPagesSum = if (currentChapterIndex > 0 && currentChapterIndex <= chapterPageCounts.size) {
            chapterPageCounts.take(currentChapterIndex).sum()
        } else 0
        return (previousPagesSum + currentPageIndex + 1).coerceIn(1, totalBookPages)
    }
}
