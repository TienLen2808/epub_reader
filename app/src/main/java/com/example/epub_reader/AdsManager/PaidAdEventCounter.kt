package com.example.epub_reader.AdsManager

import android.os.Build
import android.util.Log
import com.example.epub_reader.BaseApp
import com.example.epub_reader.utils.RemoteConfig
import com.example.epub_reader.utils.TelegramNotifier
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.DoubleAdder

object PaidAdEventCounter {

    enum class Kind {
        APP_OPEN, NATIVE, BANNER, MREC, INTERSTITIAL, REWARDED
    }

    private val counters: Map<Kind, AtomicInteger> =
        Kind.values().associateWith { AtomicInteger(0) }

    private val revenues: Map<Kind, DoubleAdder> =
        Kind.values().associateWith { DoubleAdder() }

    private val revenueFormat = DecimalFormat(
        "0.000000",
        DecimalFormatSymbols(Locale.US).apply { decimalSeparator = ',' }
    )

    fun increment(kind: Kind, revenue: Double = 0.0) {
        counters.getValue(kind).incrementAndGet()
        revenues.getValue(kind).add(revenue)
    }

    fun logSummary(tag: String = "PaidAdEventCounter") {
        val summary = Kind.values().joinToString(", ") { kind ->
            "${kind.name}=${counters.getValue(kind).get()}/${formatRevenue(revenues.getValue(kind).sum())}"
        }
        val totalCount = counters.values.sumOf { it.get() }
        val totalRevenue = revenues.values.sumOf { it.sum() }
        val message = "Paid ad events since splash (count/revenue): $summary, total=$totalCount/${formatRevenue(totalRevenue)}, activityTrace: ${BaseApp.instance.activityTrace}"

        logToTelegram(message)
    }

    private fun formatRevenue(value: Double): String = "$${revenueFormat.format(value)}"

    private fun logToTelegram(adsInfo: String) {
        try {
            val packageInfo = BaseApp.instance.packageManager.getPackageInfo(BaseApp.instance.packageName, 0)
            val versionName = packageInfo.versionName

            val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val osVersion = Build.VERSION.RELEASE
            val apiLevel = Build.VERSION.SDK_INT

            val deviceInfo = "$brand $model (Android $osVersion, API $apiLevel), App ${packageInfo.packageName} $versionName, key_added: ${RemoteConfig.key_added} (Organic: ${RemoteConfig.i})"
            TelegramNotifier.send("$deviceInfo\n\n$adsInfo")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
