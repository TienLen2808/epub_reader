package com.example.epub_reader.utils

import android.content.Context
import java.util.Locale

class PreferencesHelper private constructor(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "MyAppPreferences"
        @Volatile
        private var instance: PreferencesHelper? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = PreferencesHelper(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): PreferencesHelper {
            return instance ?: throw IllegalStateException(
                "PreferencesHelper not initialized. Call init(context) in Application.onCreate()"
            )
        }
    }

    fun putString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).commit()
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).commit()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun putFloat(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).commit()
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        sharedPreferences.edit().putLong(key, value).commit()
    }

    fun getLong(key: String, defaultValue: Long = 0): Long {
        return sharedPreferences.getLong(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).commit()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun putStringList(key: String, value: List<String>) {
        sharedPreferences.edit().putStringSet(key, value.toSet()).commit()
    }

    fun getStringList(key: String): List<String> {
        val set = sharedPreferences.getStringSet(key, emptySet())
        return if (set != null) set.toList() else listOf()
    }

    fun remove(key: String) {
        sharedPreferences.edit().remove(key).commit()
    }

    fun putLocale(locale: Locale?) {
        if (locale == null) {
            remove(Constants.SYSTEM_DEFAULT_LANGUAGE)
        } else {
            putString(Constants.SYSTEM_DEFAULT_LANGUAGE, locale.toLanguageTag())
        }
    }

    fun getLocale(): Locale? {
        return try {
            val tag = getString(Constants.SYSTEM_DEFAULT_LANGUAGE, null)
            tag?.let { Locale.forLanguageTag(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
