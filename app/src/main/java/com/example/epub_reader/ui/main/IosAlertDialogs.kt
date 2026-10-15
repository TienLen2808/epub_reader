package com.example.epub_reader.ui.main

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogIosActionsBinding
import com.example.epub_reader.databinding.DialogIosInputBinding
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small helpers that build iOS style alerts (UIAlertController look).
 */
object IosAlertDialogs {

    /** iOS alerts dim the screen only slightly */
    const val IOS_DIM_AMOUNT = 0.2f

    /** One button of [showActions] */
    class Action(
        val title: String,
        val destructive: Boolean = false,
        val onClick: () -> Unit
    )

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun prepareWindow(dialog: Dialog, context: Context) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(IOS_DIM_AMOUNT)
        }
        dialog.show()
        dialog.window?.setLayout(dp(context, 270), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Alert with a single text field + "Hủy" / [positiveText] buttons (used for "Đổi tên").
     * When [selectBaseName] is true only the part before the extension is selected, like iOS Files.
     */
    fun showInput(
        context: Context,
        title: String,
        initialText: String,
        positiveText: String,
        message: String? = null,
        selectBaseName: Boolean = true,
        onConfirm: (String) -> Unit
    ): Dialog {
        val binding = DialogIosInputBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(false)

        binding.root.clipToOutline = true
        binding.tvInputTitle.text = title
        if (message.isNullOrBlank()) {
            binding.tvInputMessage.visibility = View.GONE
        } else {
            binding.tvInputMessage.text = message
            binding.tvInputMessage.visibility = View.VISIBLE
        }
        binding.btnInputPositive.text = positiveText

        binding.etInput.imeOptions = EditorInfo.IME_ACTION_DONE
        binding.etInput.setText(initialText)
        val dot = initialText.lastIndexOf('.')
        if (selectBaseName && dot > 0) {
            binding.etInput.setSelection(0, dot)
        } else {
            binding.etInput.selectAll()
        }

        val confirm = {
            val value = binding.etInput.text?.toString()?.trim().orEmpty()
            if (value.isNotEmpty()) {
                dialog.dismiss()
                onConfirm(value)
            }
        }
        binding.btnInputPositive.setOnClickListener { confirm() }
        binding.btnInputNegative.setOnClickListener { dialog.dismiss() }
        binding.etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirm()
                true
            } else {
                false
            }
        }

        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        prepareWindow(dialog, context)
        binding.etInput.requestFocus()
        return dialog
    }

    /**
     * Alert with a vertical list of buttons + a bold "cancel" button at the bottom
     * (UIAlertController with several actions).
     */
    fun showActions(
        context: Context,
        title: String?,
        message: String?,
        actions: List<Action>,
        cancelText: String
    ): Dialog {
        val binding = DialogIosActionsBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        binding.root.clipToOutline = true

        bindHeader(binding, title, message)

        actions.forEach { action ->
            addButton(context, binding.layoutActionButtons, action.title, action.destructive, bold = false) {
                dialog.dismiss()
                action.onClick()
            }
        }
        addButton(context, binding.layoutActionButtons, cancelText, destructive = false, bold = true) {
            dialog.dismiss()
        }

        prepareWindow(dialog, context)
        return dialog
    }

    /** Simple alert with one bold button ("OK") */
    fun showMessage(
        context: Context,
        title: String?,
        message: String?,
        buttonText: String,
        onDismiss: (() -> Unit)? = null
    ): Dialog {
        val binding = DialogIosActionsBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        binding.root.clipToOutline = true

        bindHeader(binding, title, message)
        addButton(context, binding.layoutActionButtons, buttonText, destructive = false, bold = true) {
            dialog.dismiss()
        }
        if (onDismiss != null) {
            dialog.setOnDismissListener { onDismiss() }
        }

        prepareWindow(dialog, context)
        return dialog
    }

    private fun bindHeader(binding: DialogIosActionsBinding, title: String?, message: String?) {
        val hasTitle = !title.isNullOrBlank()
        val hasMessage = !message.isNullOrBlank()
        binding.tvActionsTitle.visibility = if (hasTitle) View.VISIBLE else View.GONE
        binding.tvActionsTitle.text = title.orEmpty()
        binding.tvActionsMessage.visibility = if (hasMessage) View.VISIBLE else View.GONE
        binding.tvActionsMessage.text = message.orEmpty()
        if (hasMessage && !hasTitle) {
            binding.tvActionsMessage.setPadding(
                binding.tvActionsMessage.paddingLeft,
                dp(binding.root.context, 19),
                binding.tvActionsMessage.paddingRight,
                binding.tvActionsMessage.paddingBottom
            )
        }
        binding.viewActionsHeaderSpace.visibility = if (hasTitle || hasMessage) View.VISIBLE else View.GONE
    }

    private fun addButton(
        context: Context,
        container: LinearLayout,
        label: String,
        destructive: Boolean,
        bold: Boolean,
        onClick: () -> Unit
    ) {
        val separator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                max(1, (0.6f * context.resources.displayMetrics.density).roundToInt())
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.ios_alert_separator))
        }
        container.addView(separator)

        val button = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(context, 12), 0, dp(context, 12), 0)
            textSize = 17f
            setTextColor(ContextCompat.getColor(context, if (destructive) R.color.ios_red else R.color.ios_blue))
            typeface = if (bold) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            setBackgroundResource(R.drawable.bg_ios_alert_button)
            text = label
            setOnClickListener { onClick() }
        }
        container.addView(button)
    }
}
