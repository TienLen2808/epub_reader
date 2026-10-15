package com.example.epub_reader.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.annotation.StringRes
import com.example.epub_reader.R
import java.util.Locale

/** A language the book can be read aloud in */
class TtsLanguage(
    val code: String,
    val locale: Locale,
    /** Name written in the language itself ("ภาษาไทย", "中文"...) */
    val nativeName: String,
    /** Name in the app language ("Tiếng Thái") */
    @StringRes val nameRes: Int
)

object TtsLanguages {

    const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

    // Voice state of a language for the text-to-speech engine of the device
    const val VOICE_UNKNOWN = 0
    const val VOICE_READY = 1
    const val VOICE_NEEDS_DOWNLOAD = 2
    const val VOICE_NOT_SUPPORTED = 3

    val all: List<TtsLanguage> = listOf(
        TtsLanguage("vi", Locale.forLanguageTag("vi-VN"), "Tiếng Việt", R.string.tts_lang_vi),
        TtsLanguage("en", Locale.forLanguageTag("en-US"), "English", R.string.tts_lang_en),
        TtsLanguage("zh-CN", Locale.forLanguageTag("zh-CN"), "简体中文", R.string.tts_lang_zh_cn),
        TtsLanguage("zh-TW", Locale.forLanguageTag("zh-TW"), "繁體中文", R.string.tts_lang_zh_tw),
        TtsLanguage("th", Locale.forLanguageTag("th-TH"), "ภาษาไทย", R.string.tts_lang_th),
        TtsLanguage("ja", Locale.forLanguageTag("ja-JP"), "日本語", R.string.tts_lang_ja),
        TtsLanguage("ko", Locale.forLanguageTag("ko-KR"), "한국어", R.string.tts_lang_ko),
        TtsLanguage("fr", Locale.forLanguageTag("fr-FR"), "Français", R.string.tts_lang_fr),
        TtsLanguage("de", Locale.forLanguageTag("de-DE"), "Deutsch", R.string.tts_lang_de),
        TtsLanguage("es", Locale.forLanguageTag("es-ES"), "Español", R.string.tts_lang_es),
        TtsLanguage("pt", Locale.forLanguageTag("pt-BR"), "Português", R.string.tts_lang_pt),
        TtsLanguage("it", Locale.forLanguageTag("it-IT"), "Italiano", R.string.tts_lang_it),
        TtsLanguage("ru", Locale.forLanguageTag("ru-RU"), "Русский", R.string.tts_lang_ru),
        TtsLanguage("uk", Locale.forLanguageTag("uk-UA"), "Українська", R.string.tts_lang_uk),
        TtsLanguage("id", Locale.forLanguageTag("id-ID"), "Bahasa Indonesia", R.string.tts_lang_id),
        TtsLanguage("ms", Locale.forLanguageTag("ms-MY"), "Bahasa Melayu", R.string.tts_lang_ms),
        TtsLanguage("fil", Locale.forLanguageTag("fil-PH"), "Filipino", R.string.tts_lang_fil),
        TtsLanguage("km", Locale.forLanguageTag("km-KH"), "ភាសាខ្មែរ", R.string.tts_lang_km),
        TtsLanguage("lo", Locale.forLanguageTag("lo-LA"), "ພາສາລາວ", R.string.tts_lang_lo),
        TtsLanguage("my", Locale.forLanguageTag("my-MM"), "မြန်မာဘာသာ", R.string.tts_lang_my),
        TtsLanguage("hi", Locale.forLanguageTag("hi-IN"), "हिन्दी", R.string.tts_lang_hi),
        TtsLanguage("ar", Locale.forLanguageTag("ar"), "العربية", R.string.tts_lang_ar),
        TtsLanguage("tr", Locale.forLanguageTag("tr-TR"), "Türkçe", R.string.tts_lang_tr),
        TtsLanguage("pl", Locale.forLanguageTag("pl-PL"), "Polski", R.string.tts_lang_pl),
        TtsLanguage("nl", Locale.forLanguageTag("nl-NL"), "Nederlands", R.string.tts_lang_nl),
        TtsLanguage("sv", Locale.forLanguageTag("sv-SE"), "Svenska", R.string.tts_lang_sv),
        TtsLanguage("da", Locale.forLanguageTag("da-DK"), "Dansk", R.string.tts_lang_da),
        TtsLanguage("nb", Locale.forLanguageTag("nb-NO"), "Norsk", R.string.tts_lang_nb),
        TtsLanguage("fi", Locale.forLanguageTag("fi-FI"), "Suomi", R.string.tts_lang_fi),
        TtsLanguage("cs", Locale.forLanguageTag("cs-CZ"), "Čeština", R.string.tts_lang_cs),
        TtsLanguage("ro", Locale.forLanguageTag("ro-RO"), "Română", R.string.tts_lang_ro),
        TtsLanguage("hu", Locale.forLanguageTag("hu-HU"), "Magyar", R.string.tts_lang_hu),
        TtsLanguage("el", Locale.forLanguageTag("el-GR"), "Ελληνικά", R.string.tts_lang_el),
        TtsLanguage("he", Locale.forLanguageTag("he-IL"), "עברית", R.string.tts_lang_he)
    )

    fun find(code: String?): TtsLanguage? {
        if (code.isNullOrEmpty()) return null
        return all.firstOrNull { it.code == code }
    }

    /** Language of the device when it is in the list, otherwise English */
    fun defaultCode(locale: Locale = Locale.getDefault()): String =
        BookLanguageDetector.normalizeTag(locale.toLanguageTag()) ?: "en"

    // region Voices

    /** Voice state of every language. Each call goes to the engine service: run it off the main thread. */
    fun voiceStatuses(tts: TextToSpeech): Map<String, Int> =
        all.associate { it.code to voiceStatus(tts, it.locale) }

    fun voiceStatus(tts: TextToSpeech, locale: Locale): Int = try {
        when (tts.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> VOICE_READY
            TextToSpeech.LANG_MISSING_DATA -> VOICE_NEEDS_DOWNLOAD
            TextToSpeech.LANG_NOT_SUPPORTED -> VOICE_NOT_SUPPORTED
            else -> VOICE_UNKNOWN
        }
    } catch (_: Exception) {
        VOICE_UNKNOWN
    }

    /** System screen "Text-to-speech output" (choose the engine, download voices) */
    fun openTtsSettings(context: Context): Boolean {
        val intents = listOf(
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /** Voice download screen of the engine (falls back to the text-to-speech settings) */
    fun openVoiceDownload(context: Context, enginePackage: String?): Boolean {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!enginePackage.isNullOrEmpty()) intent.setPackage(enginePackage)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            openTtsSettings(context)
        }
    }

    @Suppress("DEPRECATION")
    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        false
    }

    fun openPlayStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    // endregion
}
