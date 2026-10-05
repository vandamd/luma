package app.luma.listener

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SwipeTouchListenerTest {
    @Test
    fun destroyingHomeCancelsPressedStateAndPendingLongPress() {
        val view = View(RuntimeEnvironment.getApplication())
        var longPresses = 0
        val listener =
            object : SwipeTouchListener(view.context, view) {
                override fun onLongClick(view: View) {
                    longPresses++
                }
            }
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0)
        listener.onTouch(view, down)
        down.recycle()
        assertTrue(view.isPressed)

        listener.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertFalse(view.isPressed)
        assertEquals(0, longPresses)
    }
}
