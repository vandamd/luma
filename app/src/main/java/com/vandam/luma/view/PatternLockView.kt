package com.vandam.luma.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.vandam.luma.helper.performAppTapHapticFeedback
import kotlin.math.sqrt

class PatternLockView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val dotPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }

        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
            }

        private val dotRadiusPx: Float = dpToPx(7f)
        private val dotMarginPx: Float = dpToPx(27f)
        private val cellSizePx: Float = dotRadiusPx * 2f + dotMarginPx * 2f
        private val hitRadiusMultiplier = 1.8f

        private val selectedDots = mutableListOf<Int>()
        private var currentTouchX = 0f
        private var currentTouchY = 0f
        private var isDrawing = false

        var onPatternCompleteListener: ((List<Int>, List<PointF>) -> Unit)? = null

        fun setDarkMode(isDark: Boolean) {
            val color = if (isDark) Color.WHITE else Color.BLACK
            dotPaint.color = color
            linePaint.color = color
            invalidate()
        }

        fun resetPattern() {
            selectedDots.clear()
            isDrawing = false
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val size = (cellSizePx * 3).toInt()
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val center = getDotCenter(row, col)
                    canvas.drawCircle(center.x, center.y, dotRadiusPx, dotPaint)
                }
            }

            if (selectedDots.isNotEmpty()) {
                for (i in 0 until selectedDots.size - 1) {
                    val from = getDotCenterFromIndex(selectedDots[i])
                    val to = getDotCenterFromIndex(selectedDots[i + 1])
                    canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
                }

                if (isDrawing) {
                    val lastDot = getDotCenterFromIndex(selectedDots.last())
                    canvas.drawLine(lastDot.x, lastDot.y, currentTouchX, currentTouchY, linePaint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    selectedDots.clear()
                    isDrawing = true
                    currentTouchX = event.x
                    currentTouchY = event.y
                    val hitDot = findDotAt(event.x, event.y)
                    if (hitDot != null) {
                        selectedDots.add(hitDot)
                        performAppTapHapticFeedback(context)
                    }
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    currentTouchX = event.x
                    currentTouchY = event.y
                    val hitDot = findDotAt(event.x, event.y)
                    if (hitDot != null && !selectedDots.contains(hitDot)) {
                        selectedDots.add(hitDot)
                        performAppTapHapticFeedback(context)
                    }
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDrawing && selectedDots.isNotEmpty()) {
                        val screenCoords = getScreenCoordinates()
                        onPatternCompleteListener?.invoke(selectedDots.toList(), screenCoords)
                    }
                    isDrawing = false
                    invalidate()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun findDotAt(
            x: Float,
            y: Float,
        ): Int? {
            val hitRadius = dotRadiusPx * hitRadiusMultiplier
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val center = getDotCenter(row, col)
                    val distance = sqrt((x - center.x) * (x - center.x) + (y - center.y) * (y - center.y))
                    if (distance <= hitRadius) {
                        return row * 3 + col
                    }
                }
            }
            return null
        }

        private fun getDotCenter(
            row: Int,
            col: Int,
        ): PointF =
            PointF(
                col * cellSizePx + cellSizePx / 2f,
                row * cellSizePx + cellSizePx / 2f,
            )

        private fun getDotCenterFromIndex(index: Int): PointF = getDotCenter(index / 3, index % 3)

        private fun getScreenCoordinates(): List<PointF> {
            val location = IntArray(2)
            getLocationOnScreen(location)
            return selectedDots.map { index ->
                val local = getDotCenterFromIndex(index)
                PointF(location[0] + local.x, location[1] + local.y)
            }
        }

        private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    }
