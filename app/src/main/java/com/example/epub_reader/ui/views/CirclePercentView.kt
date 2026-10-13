package com.example.epub_reader.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View

class CirclePercentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    fun setPercentAnimated(percent: Int, duration: Long, onEnd: () -> Unit) {
        onEnd()
    }
}
