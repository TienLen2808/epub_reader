package com.example.epub_reader.AdsManager.adCallbacks

interface AdLoadedCallback {
    fun adLoaded(success: Boolean?)
    fun adClicked(click: Boolean)
}
