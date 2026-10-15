package com.example.epub_reader.utils

import java.util.Locale

object LocalizeHelper {
    val countryList = listOf<String>("en", "de", "es", "fr", "it", "pt", "ja", "ko", "zh", "vi", "ar", "hi", "in", "pl", "tl", "tr")

    fun numberOfLanguages(): Int {
        return countryList.size
    }

    fun languageAtIndex(index: Int): Locale {
        if (index < countryList.size) {
            return Locale(countryList[index])
        }
        return Locale.getDefault()
    }

    fun indexOfLanguage(locale: Locale) : Int {
        val lang = locale.language.split("_").first()
        return countryList.indexOf(lang)
    }
}
