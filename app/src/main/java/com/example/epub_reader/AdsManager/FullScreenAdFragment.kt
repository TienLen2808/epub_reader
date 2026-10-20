package com.example.epub_reader.AdsManager

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogFullScreenAdBinding
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full screen native ad (onboarding pages, reader).
 *
 * The ad comes from NativeAdsPool. Once it is on screen, "N seconds remaining" counts down
 * (remote config FULLSCREEN_NATIVE_X_VISIBLE, 5 s at most), then the close button appears and works right away.
 * Back closes the screen at any time. Without a loaded ad after [AD_WAIT_MS] the screen closes by itself.
 * [show]'s onClose is called once in every case.
 */
class FullScreenAdFragment : DialogFragment() {

    private var _binding: DialogFullScreenAdBinding? = null
    private var onClose: (() -> Unit)? = null
    private var type: DisabledAdType = DisabledAdType.onboardingFullscreen
    private var countDownTimer: CountDownTimer? = null
    private var isAdPopulated = false
    private var hasNotifiedClose = false
    private val onPoolAdLoaded: () -> Unit = { activity?.runOnUiThread { populateAd() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        // Recreated by the system (rotation, restored process): the caller's callback is gone, close it
        if (savedInstanceState != null) dismissAllowingStateLoss()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogFullScreenAdBinding.inflate(inflater, container, false)
        _binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("AdLog", "📺 [FullScreenAdDialog] Dialog onViewCreated. Type: $type")
        val binding = _binding ?: return

        dialog?.window?.setBackgroundDrawable(Color.WHITE.toDrawable())
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.tvRemaining.isVisible = false
        binding.btnClose.isVisible = false
        binding.btnClose.setOnClickListener { close() }
        binding.adLoading.isVisible = true

        NativeAdsPool.getInstance().onAdSuccessLoaded = onPoolAdLoaded
        populateAd()

        viewLifecycleOwner.lifecycleScope.launch {
            delay(AD_WAIT_MS)
            if (!isAdPopulated) close()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        countDownTimer?.cancel()
        countDownTimer = null
        val pool = NativeAdsPool.getInstance()
        if (pool.onAdSuccessLoaded === onPoolAdLoaded) pool.onAdSuccessLoaded = null
        // The native ad goes back to the pool
        _binding?.adContainer?.removeAllViews()
        _binding = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        notifyClosedOnce()
    }

    fun bindTo(type: DisabledAdType) {
        this.type = type
        if (_binding != null) populateAd()
    }

    private fun populateAd() {
        val binding = _binding ?: return
        if (isAdPopulated || !isAdded) return
        val pool = NativeAdsPool.getInstance()
        if (!pool.hasAvailableItemAd && !pool.hasLoadedAd()) return // shown when one loads (onAdSuccessLoaded)
        pool.availableItemAd?.populateNativeAdView(
            layoutResource = R.layout.native_custom_ad_fullscreen,
            type = type,
            adFrame = binding.adContainer,
            onAdReady = { onAdShown() }
        )
    }

    private fun onAdShown() {
        val binding = _binding ?: return
        if (isAdPopulated) return
        isAdPopulated = true
        Log.d("AdLog", "✅ [FullScreenAdDialog] SHOWING Full Screen Native Ad on screen!")
        binding.adLoading.isVisible = false
        startCloseCountdown()
    }

    private fun startCloseCountdown() {
        val binding = _binding ?: return
        val seconds = PreferencesHelper.getInstance()
            .getInt(Constants.FULLSCREEN_NATIVE_X_VISIBLE, 5)
            .coerceIn(0, MAX_CLOSE_DELAY_S)
        if (seconds == 0) {
            showCloseButton()
            return
        }
        binding.tvRemaining.text = getString(R.string.d_seconds_remaining, seconds)
        binding.tvRemaining.isVisible = true
        countDownTimer = object : CountDownTimer(seconds * 1000L, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                val left = ((millisUntilFinished + 999) / 1000).toInt()
                val tv = _binding?.tvRemaining ?: return
                if (isAdded) tv.text = getString(R.string.d_seconds_remaining, left)
            }

            override fun onFinish() {
                showCloseButton()
            }
        }.start()
    }

    /** The X works as soon as it is visible (no "visible but not clickable yet" phase) */
    private fun showCloseButton() {
        val binding = _binding ?: return
        binding.tvRemaining.isVisible = false
        binding.btnClose.isVisible = true
    }

    private fun close() {
        Log.d("AdLog", "🙈 [FullScreenAdDialog] Closing Full Screen Native Dialog")
        if (!isAdded) return
        if (parentFragmentManager.isStateSaved) dismissAllowingStateLoss() else dismiss()
    }

    private fun notifyClosedOnce() {
        if (hasNotifiedClose) return
        hasNotifiedClose = true
        onClose?.invoke()
    }

    fun show(manager: FragmentManager, onClose: (() -> Unit)? = null) {
        this.onClose = onClose
        try {
            show(manager, TAG)
        } catch (e: Exception) {
            e.printStackTrace()
            notifyClosedOnce()
        }
    }

    private companion object {
        const val TAG = "FullScreenAdFragment"
        /** No native ad loaded after this long: the screen closes by itself */
        const val AD_WAIT_MS = 5_000L
        /** Upper limit for the remote "X visible after" setting */
        const val MAX_CLOSE_DELAY_S = 5
    }
}

fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}
