package com.example.epub_reader.utils

import android.app.Activity
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.epub_reader.R

class Utils {
    companion object {
        fun isInternetAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun restoreWatchedAdCount(context: Context) {
        }
    }

    object NetworkDialogManager {
        private var dialog: Dialog? = null

        fun Context.findActivity(): Activity? = when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

        fun showNoInternetDialog(context: Context) {
            if (context !is Activity || context.isFinishing || context.isDestroyed) {
                return
            }
            if (dialog?.isShowing == true && dialog?.window?.decorView?.windowToken != null) return

            val newDialog = Dialog(context).apply {
                setContentView(R.layout.no_internet_dialog)
                setCancelable(false)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                findViewById<Button>(R.id.btnRetry)?.setOnClickListener {
                    dismiss()
                    dialog = null
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    context.startActivity(intent)
                }
            }
            try {
                newDialog.show()
                dialog = newDialog
            } catch (e: Exception) {
                Log.w("Dialog", "Cannot show dialog")
            }
        }

        fun hideNoInternetDialog() {
            dialog?.let { dlg ->
                val activity = dlg.context.findActivity()
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    dialog = null
                    return
                }
                activity.runOnUiThread {
                    try {
                        if (dlg.isShowing && dlg.window?.decorView?.windowToken != null) {
                            dlg.dismiss()
                        }
                        dialog = null
                    } catch (e: Exception) {
                        Log.w("DialogDismiss", "Dismiss dialog failed: ${e.message}")
                    }
                }
            } ?: run {
                dialog = null
            }
        }

        fun observeInternet(
            context: Context,
            onAvailable: () -> Unit,
            onLost: () -> Unit
        ): Any {
            val appContext = context.applicationContext
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            fun isOnline(): Boolean {
                val active = cm.activeNetworkInfo
                return active != null && active.isConnectedOrConnecting
            }

            if (isOnline()) {
                onAvailable()
            } else {
                onLost()
            }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        onAvailable()
                    }

                    override fun onLost(network: Network) {
                        onLost()
                    }
                }

                cm.registerNetworkCallback(networkRequest, callback)
                callback
            } else {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (isOnline()) {
                            onAvailable()
                        } else {
                            onLost()
                        }
                    }
                }
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                )
                receiver
            }
        }

        fun unregisterInternetObserver(context: Context?, observer: Any?) {
            if (context == null || observer == null) return
            val appContext = context.applicationContext
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            when (observer) {
                is ConnectivityManager.NetworkCallback -> {
                    try {
                        cm.unregisterNetworkCallback(observer)
                    } catch (_: Exception) { }
                }
                is BroadcastReceiver -> {
                    try {
                        appContext.unregisterReceiver(observer)
                    } catch (_: Exception) { }
                }
            }
        }
    }
}

fun Activity.isValidActivity(): Boolean {
    return !(this.isDestroyed || this.isFinishing)
}

fun Activity.safeRunOnUiThread(action: () -> Unit) {
    if (isValidActivity()) {
        runOnUiThread(action)
    }
}

fun Context.getStatusBarHeight(): Int {
    var result = 0
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
        result = resources.getDimensionPixelSize(resourceId)
    }
    return result
}

fun View.getGlobalCenter(): Point {
    val rect = Rect()
    getGlobalVisibleRect(rect)
    return Point(rect.right - rect.width() / 2, rect.bottom - rect.height() / 2)
}

fun ViewGroup.removeSubviews() {
    removeAllViews()
    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    requestLayout()
}

fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}

fun String.netKey() = if (contains("applovin", ignoreCase = true) || contains("max", ignoreCase = true)) "applovin"
else if (contains("bidmachine", ignoreCase = true)) "bidmachine"
else if (contains("exchange", ignoreCase = true)) "dt_exchange"
else if (contains("facebook", ignoreCase = true)) "facebook"
else if (contains("admob", ignoreCase = true)) "admob"
else if (contains("google", ignoreCase = true) || contains("gam", ignoreCase = true)) "google"
else if (contains("inmobi", ignoreCase = true)) "inmobi"
else if (contains("ironsource", ignoreCase = true)) "ironsource"
else if (contains("liftoff", ignoreCase = true)) "liftoff_monetize"
else if (contains("mintegral", ignoreCase = true)) "mintegral"
else if (contains("pangle", ignoreCase = true)) "pangle"
else if (contains("unity", ignoreCase = true)) "unity"
else "other"

fun String.isAdmobLike() = if (com.example.epub_reader.BuildConfig.DEBUG) true
else contains("admob", true) ||
        contains("google", true) ||
        contains("gam", true) ||
        contains("meta", true) ||
        contains("fan", true) ||
        contains("facebook", true)
