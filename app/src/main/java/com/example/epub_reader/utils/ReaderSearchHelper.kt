package com.example.epub_reader.utils

import androidx.core.text.HtmlCompat
import com.example.epub_reader.epub.EpubBook

class ReaderSearchHelper {

    /**
     * @param untitledChapter title shown for a chapter without title (1-based number), e.g. "Chapter 3"
     */
    fun searchBook(
        book: EpubBook?,
        query: String,
        untitledChapter: (Int) -> String = { number -> "Chapter $number" }
    ): List<SearchResultSection> {
        if (book == null) return emptyList()
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || cleanQuery.length < 2) return emptyList()

        val sectionList = mutableListOf<SearchResultSection>()

        book.chapters.forEachIndexed { chapterIdx, chapter ->
            // Decode HTML entities (e.g. &#x1A1;, &#x309; -> ơ) and convert to clean plain text
            val plainText = try {
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

            val chapterTitle = if (chapter.title.isNotBlank()) chapter.title else untitledChapter(chapterIdx + 1)
            val matches = mutableListOf<SearchResultItem>()

            var index = plainText.indexOf(cleanQuery, ignoreCase = true)
            var count = 0
            while (index >= 0 && count < 10) {
                val start = (index - 40).coerceAtLeast(0)
                val end = (index + cleanQuery.length + 60).coerceAtMost(plainText.length)
                val snippet = (if (start > 0) "... " else "") +
                        plainText.substring(start, end).trim() +
                        (if (end < plainText.length) " ..." else "")

                matches.add(
                    SearchResultItem(
                        chapterIndex = chapterIdx,
                        chapterTitle = chapterTitle,
                        snippetText = snippet,
                        queryKeyword = cleanQuery
                    )
                )

                count++
                index = plainText.indexOf(cleanQuery, startIndex = index + cleanQuery.length, ignoreCase = true)
            }

            if (matches.isNotEmpty()) {
                val headerName = "${chapterIdx + 1}. ${chapterTitle.uppercase()}"
                sectionList.add(SearchResultSection(headerName, chapterIdx, matches))
            }
        }

        return sectionList
    }
}
