package com.example.epub_reader.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FileItemManager private constructor() {

    companion object {
        val instance: FileItemManager by lazy { FileItemManager() }
        private const val PREF_KEY_IMPORTED_FILES = "imported_files_json_list"
    }

    private val gson = Gson()

    fun getFiles(): List<ImportedFileItem> {
        val jsonStr = PreferencesHelper.getInstance().getString(PREF_KEY_IMPORTED_FILES) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ImportedFileItem>>() {}.type
            gson.fromJson<List<ImportedFileItem>>(jsonStr, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addFile(item: ImportedFileItem) {
        val list = getFiles().toMutableList()
        list.removeAll { it.fileUriStr == item.fileUriStr || it.fileName == item.fileName }
        list.add(0, item)
        saveFiles(list)
    }

    fun removeFile(id: String) {
        val list = getFiles().toMutableList()
        list.removeAll { it.id == id }
        saveFiles(list)
    }

    private fun saveFiles(list: List<ImportedFileItem>) {
        val jsonStr = gson.toJson(list)
        PreferencesHelper.getInstance().putString(PREF_KEY_IMPORTED_FILES, jsonStr)
    }
}
