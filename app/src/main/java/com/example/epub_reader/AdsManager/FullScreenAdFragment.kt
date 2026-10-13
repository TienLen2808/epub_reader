package com.example.epub_reader.AdsManager

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogFullScreenAdBinding
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.dpToPx
import com.example.epub_reader.utils.removeSubviews
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FullScreenAdFragment : DialogFragment() {

    private lateinit var binding: DialogFullScreenAdBinding
    private var onClose: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_NoActionBar)
    }

    private var type: DisabledAdType = DisabledAdType.onboardingFullscreen
    private var countDownTimer: CountDownTimer? = null
    private var remaining: Int = 0

    private var isCreated: Boolean = false
    private var isAdPopulated: Boolean = false
    private var hasNotifiedClose: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogFullScreenAdBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawable(Color.WHITE.toDrawable())

        binding.apply {
            ViewCompat.setOnApplyWindowInsetsListener(main) { v, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, 0)
                insets
            }

            adLoading.visibility = View.VISIBLE
            cardRemaining.visibility = View.GONE
            btnClose.apply {
                visibility = View.GONE
                setOnClickListener {
                    onButtonClose()
                }
            }
        }

        context?.let {
            loadAd()
        }

        lifecycleScope.launch {
            delay(8000)
            if (!isAdPopulated) {
                onButtonClose()
            }
        }

        isCreated = true

        NativeAdsPool.getInstance().onAdSuccessLoaded = {
            lifecycleScope.launch {
                populateAd()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        notifyClosedOnce()
    }

    private fun isAlive(): Boolean {
        return isAdded && activity != null && !isDetached && !isRemoving && !isStateSaved
    }

    fun bindTo(type: DisabledAdType) {
        this.type = type
        if (isCreated) {
            loadAd()
        }
    }

    fun loadAd() {
        try {
            isAdPopulated = false
            binding.apply {
                cardRemaining.visibility = View.GONE
                adLoading.visibility = View.VISIBLE
                btnClose.apply {
                    visibility = View.GONE
                    isClickable = false
                    isFocusable = false
                }
            }

            if (NativeAdsPool.getInstance().hasLoadedAd()) {
                populateAd()
            } else {
                binding.adLoading.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun onButtonClose() {
        try {
            NativeAdsPool.getInstance().onAdSuccessLoaded = null
            if (this.isAdded && this.isVisible && !this.isDetached && this.view != null && !isStateSaved) {
                binding.adContainer.removeSubviews()
                if (!parentFragmentManager.isStateSaved) {
                    dismiss()
                } else {
                    dismissAllowingStateLoss()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyClosedOnce() {
        if (hasNotifiedClose) return
        hasNotifiedClose = true
        onClose?.invoke()
    }

    private fun populateAd() {
        try {
            activity?.runOnUiThread {
                if (isAlive()) {
                    NativeAdsPool.getInstance().availableItemAd?.let {
                        it.populateNativeAdView(R.layout.native_custom_ad_fullscreen, type, binding.adContainer)
                        if (binding.btnClose.isClickable == false && !isAdPopulated) {
                            isAdPopulated = true
                            binding.adContainer.post {
                                val offset = context?.dpToPx(50) ?: 100
                                for (child in binding.adContainer.children) {
                                    child.layoutParams.height = binding.adContainer.height - offset
                                    context?.let { ctx ->
                                        child.setBackgroundColor(ctx.resources.getColor(R.color.black))
                                    }
                                    child.visibility = View.VISIBLE
                                    if (child is ViewGroup) {
                                        for (sub in child.children) {
                                            sub.layoutParams.height = binding.adContainer.height - offset
                                            context?.let { ctx ->
                                                sub.setBackgroundColor(ctx.resources.getColor(R.color.black))
                                            }
                                        }
                                    }
                                }

                                binding.adLoading.visibility = View.GONE
                                startCountDownTimer()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCountDownTimer() {
        try {
            countDownTimer?.cancel()
            remaining = PreferencesHelper.getInstance().getInt(Constants.FULLSCREEN_NATIVE_X_VISIBLE, 5)
            val enableTime = PreferencesHelper.getInstance().getInt(Constants.FULLSCREEN_NATIVE_X_ENABLE, 3)
            binding.cardRemaining.visibility = View.VISIBLE

            countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    try {
                        remaining -= 1
                        binding.tvRemaining.text = context?.getString(R.string.d_seconds_remaining, remaining)

                        if (remaining == 0) {
                            binding.cardRemaining.visibility = View.GONE
                            binding.btnClose.visibility = View.VISIBLE
                            countDownTimer?.cancel()
                            countDownTimer = null

                            binding.circle.setPercentAnimated(100, enableTime * 1000L) {
                                binding.circle.visibility = View.GONE
                                binding.imageViewX.imageTintList = ColorStateList.valueOf(
                                    binding.imageViewX.context.getColor(R.color.white)
                                )
                                lifecycleScope.launch {
                                    delay(1000)
                                    binding.btnClose.apply {
                                        isClickable = true
                                        isFocusable = true
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onFinish() {}
            }
            countDownTimer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun show(manager: FragmentManager, onClose: (() -> Unit)? = null) {
        try {
            this.onClose = onClose
            show(manager, "FullScreenAdFragment")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}
