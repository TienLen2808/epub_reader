package com.example.epub_reader.ui.main

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import com.example.epub_reader.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Base for full screen pages pushed from a tab (iOS "push" animation, light system bars,
 * edge-to-edge with the system bar insets applied as padding).
 */
abstract class IosFullScreenDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Epub_reader_IosFullScreenDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    /**
     * Status bar inset -> top padding of [topView] (the navigation bar container),
     * navigation bar inset -> bottom padding of [bottomView].
     */
    protected fun applySystemBarInsets(root: View, topView: View, bottomView: View) {
        val topInitial = topView.paddingTop
        val bottomInitial = bottomView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topView.updatePadding(top = topInitial + bars.top)
            bottomView.updatePadding(bottom = bottomInitial + bars.bottom)
            root.updatePadding(left = bars.left, right = bars.right)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Places the navigation bar title like UINavigationBar: centered in [bar] while it fits between
     * [leading] and [trailing]. Otherwise [backLabel] (the text of the back button) is hidden first,
     * then the title takes the room left between the two items ("…" when it is still too long).
     * [bar] is the FrameLayout that holds the three views.
     */
    protected fun fitNavigationTitle(
        bar: ViewGroup,
        title: TextView,
        leading: View?,
        trailing: View?,
        backLabel: View? = null
    ) {
        val update = object : Runnable {
            override fun run() {
                val barWidth = bar.width
                if (barWidth <= 0) return
                val params = title.layoutParams as? FrameLayout.LayoutParams ?: return

                val rtl = bar.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val startExtent = extentFromEdge(leading, barWidth, rtl, fromStart = true)
                val endExtent = extentFromEdge(trailing, barWidth, rtl, fromStart = false)
                val gap = (8 * bar.resources.displayMetrics.density).roundToInt()
                val side = max(startExtent, endExtent) + gap
                val text = title.text?.toString().orEmpty()
                val textWidth = ceil(title.paint.measureText(text)).toInt() +
                    title.totalPaddingLeft + title.totalPaddingRight

                val fitsCentered = textWidth <= barWidth - 2 * side
                if (!fitsCentered && backLabel != null && backLabel.visibility == View.VISIBLE) {
                    // The bar is laid out again without the back title, then this runs again
                    backLabel.visibility = View.GONE
                    return
                }

                val newWidth = if (fitsCentered) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
                val newStart = if (fitsCentered) side else startExtent + gap
                val newEnd = if (fitsCentered) side else endExtent + gap
                if (params.width != newWidth || params.marginStart != newStart ||
                    params.marginEnd != newEnd || params.gravity != Gravity.CENTER
                ) {
                    params.width = newWidth
                    params.marginStart = newStart
                    params.marginEnd = newEnd
                    params.gravity = Gravity.CENTER
                    title.layoutParams = params
                }
            }
        }
        // Runs again whenever one of the items changes size (text change, back title hidden...)
        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bar.removeCallbacks(update)
            bar.post(update)
        }
    }

    /** Width taken by [view] from the start (leading item) or the end (trailing item) of the bar */
    private fun extentFromEdge(view: View?, barWidth: Int, rtl: Boolean, fromStart: Boolean): Int {
        if (view == null || view.visibility == View.GONE) return 0
        val fromLeft = fromStart != rtl
        return if (fromLeft) view.right else barWidth - view.left
    }

    protected fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
}
