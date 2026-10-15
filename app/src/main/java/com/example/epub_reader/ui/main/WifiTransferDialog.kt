package com.example.epub_reader.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogWifiTransferBinding
import com.example.epub_reader.databinding.ItemTransferFileBinding
import com.example.epub_reader.transfer.WifiTransferServer
import com.example.epub_reader.utils.ImportedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * "Chuyển Wifi": while this page is open the phone runs [WifiTransferServer].
 * The computer opens http://<phone ip>:<port> in a browser and uploads / downloads files.
 */
class WifiTransferDialog : IosFullScreenDialogFragment(), WifiTransferServer.Listener {

    private var _binding: DialogWifiTransferBinding? = null
    private val binding get() = _binding!!

    private var server: WifiTransferServer? = null
    private var isStarting = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogWifiTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root, binding.layoutTopBar, binding.scrollWifi)
        fitNavigationTitle(
            bar = binding.layoutWifiNavBar,
            title = binding.tvWifiTitle,
            leading = binding.btnWifiBack,
            trailing = null,
            backLabel = binding.tvWifiBackLabel
        )

        binding.btnWifiBack.setOnClickListener { dismiss() }
        binding.btnCopyAddress.setOnClickListener { copyAddress() }

        // Opening the port is done off the main thread; onDestroyView() stops it in any case
        val newServer = WifiTransferServer(requireContext(), this)
        server = newServer
        isStarting = true
        updateStatus()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { newServer.start() }
            isStarting = false
            updateStatus()
        }

        registerNetworkCallback()
    }

    override fun onStart() {
        super.onStart()
        // The transfer stops when the screen turns off: keep it on while this page is visible
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroyView() {
        unregisterNetworkCallback()
        server?.stop()
        server = null
        mainHandler.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    /** From the server thread */
    override fun onFileReceived(item: ImportedFileItem) {
        mainHandler.post { addReceivedRow(item) }
    }

    private fun updateStatus() {
        val b = _binding ?: return
        val port: Int? = server?.takeIf { it.isRunning }?.port
        val address: String? = if (port != null) {
            findLocalIpv4()?.let { ip -> "http://$ip:$port" }
        } else {
            null
        }

        val (statusText, statusColor) = when {
            isStarting -> getString(R.string.wifi_starting) to R.color.ios_placeholder
            port == null -> getString(R.string.wifi_server_failed) to R.color.ios_red
            address == null -> getString(R.string.wifi_status_no_wifi) to R.color.ios_red
            else -> getString(R.string.wifi_status_running) to R.color.ios_green
        }
        val color = ContextCompat.getColor(requireContext(), statusColor)
        b.tvWifiStatus.text = statusText
        b.tvWifiStatus.setTextColor(color)
        ViewCompat.setBackgroundTintList(b.viewWifiStatusDot, ColorStateList.valueOf(color))

        b.tvWifiAddress.text = address ?: "—"
        b.btnCopyAddress.isEnabled = address != null
        b.btnCopyAddress.alpha = if (address != null) 1f else 0.35f
    }

    private fun copyAddress() {
        val text = _binding?.tvWifiAddress?.text?.toString().orEmpty()
        if (!text.startsWith("http")) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.wifi_transfer), text))
        toast(getString(R.string.address_copied))
    }

    private fun addReceivedRow(item: ImportedFileItem) {
        val b = _binding ?: return
        b.tvNoReceived.isVisible = false
        val container = b.layoutReceivedFiles

        // Newest first: index 0 is the (hidden) "no file" text
        if (container.childCount > 1) {
            val separator = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    max(1, (0.6f * resources.displayMetrics.density).roundToInt())
                ).apply { marginStart = (50 * resources.displayMetrics.density).roundToInt() }
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.ios_row_separator))
            }
            container.addView(separator, 1)
        }

        val row = ItemTransferFileBinding.inflate(layoutInflater, container, false)
        row.imgTransferFileIcon.setImageResource(R.drawable.ic_received_check)
        row.tvTransferFileName.text = item.fileName
        row.tvTransferFileSub.text = item.fileSizeFormatted
        container.addView(row.root, 1)
    }

    // region Network

    private fun registerNetworkCallback() {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { updateStatus() }
            }

            override fun onLost(network: Network) {
                mainHandler.post { updateStatus() }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                mainHandler.post { updateStatus() }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }

    /** IPv4 of the Wi-Fi (or hotspot) interface, e.g. 192.168.1.12 */
    private fun findLocalIpv4(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<String, String>>()
            for (networkInterface in interfaces.toList()) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (address in networkInterface.inetAddresses.toList()) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        address.hostAddress?.let { candidates += networkInterface.name to it }
                    }
                }
            }
            candidates.firstOrNull { (name, _) -> WIFI_INTERFACE_PREFIXES.any { name.startsWith(it) } }?.second
                ?: candidates.firstOrNull()?.second
        } catch (_: Exception) {
            null
        }
    }

    // endregion

    companion object {
        const val TAG = "WifiTransferDialog"
        private val WIFI_INTERFACE_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "eth")
    }
}
