package app.luma

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.luma.helper.applySafeWindowInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Window/layout regression checks, not a substitute for a physical foldable test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeLayoutTest {
    @Test
    fun coverAndInnerWindowsFillTheirBounds() {
        for ((name, width, height) in listOf(Triple("cover", 411, 797), Triple("inner", 841, 701), Triple("inner-portrait", 701, 841))) {
            val root = createHome()
            dispatchInsets(root, top = if (name == "cover") 32 else 0, bottom = 24)
            layout(root, width, height)
            val home = root.getChildAt(0)
            assertEquals(width, home.width)
            assertEquals(height - root.paddingTop - 24, home.height)
            assertFalse(home.findViewById<ScrollView>(R.id.homeAppsScroll).canScrollVertically(1))
            screenshot(root, name)
        }
    }

    @Test
    fun longRowsRemainReachableInShortRotatedWindowAndAfterResize() {
        val root = createHome(longLabels = true)
        dispatchInsets(root, top = 0, bottom = 24)
        layout(root, 797, 360)
        val scroll = root.findViewById<ScrollView>(R.id.homeAppsScroll)
        val status = root.findViewById<View>(R.id.statusBar)
        assertTrue(scroll.top >= status.bottom)
        assertTrue(scroll.canScrollVertically(1))
        assertTrue(scroll.getChildAt(0).top >= 0)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        assertFalse(scroll.canScrollVertically(1))
        assertTrue(scroll.canScrollVertically(-1))
        assertTrue(scroll.getChildAt(0).bottom - scroll.scrollY <= scroll.height)
        screenshot(root, "short-window-last-rows")

        layout(root, 841, 1000)
        assertFalse(scroll.canScrollVertically(1))
        assertFalse(scroll.canScrollVertically(-1))
        assertEquals(0, scroll.scrollY)
    }

    @Test
    fun sixRowsRemainReachableAtLargeTextScale() {
        val root = createHome(fontScale = 2f)
        dispatchInsets(root, top = 32, bottom = 48)
        layout(root, 411, 797)
        val scroll = root.findViewById<ScrollView>(R.id.homeAppsScroll)
        assertTrue(scroll.canScrollVertically(1))
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        assertFalse(scroll.canScrollVertically(1))
        assertTrue(scroll.getChildAt(0).bottom - scroll.scrollY <= scroll.height)
        screenshot(root, "large-text-last-rows")
    }

    private fun createHome(
        longLabels: Boolean = false,
        fontScale: Float = 1f,
    ): FrameLayout {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val root =
            FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                applySafeWindowInsets()
            }
        val home = LayoutInflater.from(context).inflate(R.layout.fragment_home, root, false)
        root.addView(home, ViewGroup.LayoutParams(-1, -1))
        home.findViewById<TextView>(R.id.statusClock).text = "12:34"
        val rows = home.findViewById<LinearLayout>(R.id.homeAppsLayout)
        rows.gravity = android.view.Gravity.CENTER
        repeat(6) { index ->
            val row = LayoutInflater.from(context).inflate(R.layout.home_app_button, rows, false) as TextView
            row.text = if (longLabels) "A long application name with a second line of text $index" else "Application ${index + 1}"
            row.textSize = 41f * fontScale
            row.gravity = android.view.Gravity.CENTER
            rows.addView(row)
        }
        return root
    }

    private fun dispatchInsets(
        root: View,
        top: Int,
        bottom: Int,
    ) {
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, top, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, bottom))
                .build(),
        )
    }

    private fun layout(
        root: View,
        width: Int,
        height: Int,
    ) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
    }

    private fun screenshot(
        root: View,
        name: String,
    ) {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val file = File("build/reports/fold-layout/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
