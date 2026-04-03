package com.vandam.luma.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class BaselineFrameLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        override fun getBaseline(): Int {
            val child = getChildAt(0) ?: return super.getBaseline()
            val baseline = child.baseline
            return if (baseline != -1) baseline + child.top else 0
        }
    }
