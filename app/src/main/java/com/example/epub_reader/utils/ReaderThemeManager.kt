package com.example.epub_reader.utils

import android.graphics.Color
import com.example.epub_reader.ReaderActivity.ThemeMode

data class ReaderThemeConfig(
    val themeMode: ThemeMode,
    val webViewBgColorHex: String,
    val textColorHex: String,
    val headingColorHex: String,
    val webViewBgColorInt: Int
)

class ReaderThemeManager private constructor() {

    companion object {
        private const val KEY_FONT_SIZE = "reader_font_size"
        private const val KEY_THEME_MODE = "reader_theme"
        private const val KEY_LINE_SPACING = "reader_line_spacing"

        val instance: ReaderThemeManager by lazy { ReaderThemeManager() }

        val LIGHT_THEME = ReaderThemeConfig(
            themeMode = ThemeMode.LIGHT,
            webViewBgColorHex = "#FFFFFF",
            textColorHex = "#1A1A1A",
            headingColorHex = "#111111",
            webViewBgColorInt = Color.parseColor("#FFFFFF")
        )

        val SEPIA_THEME = ReaderThemeConfig(
            themeMode = ThemeMode.SEPIA,
            webViewBgColorHex = "#FBF0D9",
            textColorHex = "#5F4B32",
            headingColorHex = "#3D2E1E",
            webViewBgColorInt = Color.parseColor("#FBF0D9")
        )

        val DARK_THEME = ReaderThemeConfig(
            themeMode = ThemeMode.DARK,
            webViewBgColorHex = "#121212",
            textColorHex = "#E0E0E0",
            headingColorHex = "#FFFFFF",
            webViewBgColorInt = Color.parseColor("#121212")
        )
    }

    var fontSizeSp: Int
        get() = PreferencesHelper.getInstance().getInt(KEY_FONT_SIZE, 18)
        set(value) {
            PreferencesHelper.getInstance().putInt(KEY_FONT_SIZE, value)
        }

    var themeMode: ThemeMode
        get() {
            val str = PreferencesHelper.getInstance().getString(KEY_THEME_MODE) ?: "LIGHT"
            return try {
                ThemeMode.valueOf(str)
            } catch (_: Exception) {
                ThemeMode.LIGHT
            }
        }
        set(value) {
            PreferencesHelper.getInstance().putString(KEY_THEME_MODE, value.name)
        }

    var lineSpacingRatio: Float
        get() = PreferencesHelper.getInstance().getFloat(KEY_LINE_SPACING, 1.4f)
        set(value) {
            PreferencesHelper.getInstance().putFloat(KEY_LINE_SPACING, value)
        }

    fun getCurrentThemeConfig(): ReaderThemeConfig {
        return when (themeMode) {
            ThemeMode.LIGHT -> LIGHT_THEME
            ThemeMode.SEPIA -> SEPIA_THEME
            ThemeMode.DARK -> DARK_THEME
        }
    }

    fun saveChapterPosition(bookTitle: String, chapterIndex: Int) {
        PreferencesHelper.getInstance().putInt("read_pos_$bookTitle", chapterIndex)
    }

    fun getChapterPosition(bookTitle: String): Int {
        return PreferencesHelper.getInstance().getInt("read_pos_$bookTitle", 0)
    }
}
