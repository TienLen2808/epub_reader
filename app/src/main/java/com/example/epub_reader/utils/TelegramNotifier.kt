package com.example.epub_reader.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

object TelegramNotifier {
    private const val TAG = "NEXA_Anime_bot"
    private const val CHAT_ID = "6223797753"

    @JvmStatic
    fun send(message: String, chatId: String = CHAT_ID) {
        try {
            val telegramBotToken = PreferencesHelper.getInstance().getString(Constants.TELEGRAM_BOT_TOKEN)
            if (telegramBotToken.isNullOrEmpty()) {
                return
            }

            val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
            val url = "https://api.telegram.org/bot$telegramBotToken/sendMessage?chat_id=$chatId&text=$encoded"

            Thread {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    conn.inputStream.use { }
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "send() failed: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.w(TAG, "send() build url failed: ${e.message}")
        }
    }

    @JvmStatic
    fun notifyMessage(pkg: String, appVersion: String?, reason: String?) {
        send("INFO: $pkg - $appVersion: $reason")
    }
}
