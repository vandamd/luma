package app.luma.listener

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs

private const val TAG = "SwipeTouchListener"

/**
 * Unified swipe, tap, and long press touch listener.
 *
 * @param context The context
 * @param view Optional view reference for view-specific callbacks (onClick, onLongClick with view)
 */
internal open class SwipeTouchListener(
    context: Context?,
    private val view: View? = null,
    private val confirmSingleTap: Boolean = false,
    private val consumeTouchEvents: Boolean = false,
) : OnTouchListener {
    private val gestureDetector: GestureDetector

    override fun onTouch(
        v: View,
        motionEvent: MotionEvent,
    ): Boolean {
        // Manage pressed state if we have a view reference
        if (view != null) {
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> view.isPressed = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.isPressed = false
            }
        }

        return gestureDetector.onTouchEvent(motionEvent) || consumeTouchEvents
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        private val swipeThreshold: Int = 100
        private val swipeVelocityThreshold: Int = 100

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!confirmSingleTap && view != null) {
                onClick(view)
            }
            return super.onSingleTapUp(e)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (confirmSingleTap && view != null) {
                onClick(view)
            }
            return super.onSingleTapConfirmed(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            if (view != null) onLongClick(view) else onLongClick()
            super.onLongPress(e)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (event1 == null) return false
            try {
                val diffY = event2.y - event1.y
                val diffX = event2.x - event1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > swipeVelocityThreshold) {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                    }
                } else {
                    if (abs(diffY) > swipeThreshold && abs(velocityY) > swipeVelocityThreshold) {
                        if (diffY < 0) onSwipeUp() else onSwipeDown()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing fling gesture", e)
            }
            return false
        }
    }

    // Swipe callbacks
    open fun onSwipeRight() {}

    open fun onSwipeLeft() {}

    open fun onSwipeUp() {}

    open fun onSwipeDown() {}

    // Click callbacks (without view - for screen-level gestures)
    open fun onLongClick() {}

    open fun onDoubleClick() {}

    // Click callbacks (with view - for view-level gestures)
    open fun onLongClick(view: View) {}

    open fun onClick(view: View) {}

    init {
        gestureDetector = GestureDetector(context, GestureListener())
    }
}
