package app.luma

import android.app.Application
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.navigation.fragment.NavHostFragment
import app.luma.data.Prefs
import app.luma.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldContinuityTest {
    @Before
    fun resetPreferences() {
        // The production singleton otherwise retains a previous Robolectric
        // application's SharedPreferences object between sandboxed tests.
        ReflectionHelpers.setStaticField(Prefs::class.java, "instance", null)
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("app.luma", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun autoRotateOffFollowsDisplayNaturalOrientationInsteadOfPortrait() {
        Prefs.getInstance(RuntimeEnvironment.getApplication()).autoRotateEnabled = false
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR, controller.get().requestedOrientation)
        }
    }

    @Test
    fun autoRotateOnStillRespectsAndroidRotationPreference() {
        Prefs.getInstance(RuntimeEnvironment.getApplication()).autoRotateEnabled = true
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, controller.get().requestedOrientation)
        }
    }

    @Test
    fun homePageSurvivesRecreation() {
        val prefs = Prefs.getInstance(RuntimeEnvironment.getApplication())
        prefs.homePages = 3
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            homeFragment(controller.get())
                .requireView()
                .findViewWithTag<LinearLayout>("pageIndicator")
                .getChildAt(1)
                .performClick()

            controller.recreate()

            val savedState = Bundle()
            homeFragment(controller.get()).onSaveInstanceState(savedState)
            assertEquals(1, savedState.getInt("currentPage"))
        }
    }

    @Test
    fun rowSwipesChangePagesWhenContentFits() {
        val prefs = Prefs.getInstance(RuntimeEnvironment.getApplication())
        prefs.homePages = 2
        prefs.setAppsPerPage(1, 4)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val home = homeFragment(controller.get())
            val view = home.requireView()
            layout(view, 411, 797)
            val rows = view.findViewById<LinearLayout>(R.id.homeAppsLayout)
            val row = rows.getChildAt(2)
            val bounds = Rect()
            row.getDrawingRect(bounds)
            (view as ViewGroup).offsetDescendantRectToMyCoords(row, bounds)
            swipe(view, bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterY() - 200f)
            assertEquals(1, savedPage(home))
        }
    }

    @Test
    fun overflowingRowsScrollAndHiddenPagesRemainReachableFromSideMargin() {
        val prefs = Prefs.getInstance(RuntimeEnvironment.getApplication())
        prefs.homePages = 2
        prefs.setAppsPerPage(1, 6)
        prefs.pageIndicatorPosition = Prefs.PageIndicatorPosition.Hidden
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val home = homeFragment(controller.get())
            val view = home.requireView()
            layout(view, 700, 300)
            val scroll = view.findViewById<ScrollView>(R.id.homeAppsScroll)
            val status = view.findViewById<View>(R.id.statusBar)
            assertEquals(status.height, scroll.top)
            assertTrue(scroll.canScrollVertically(1))
            swipe(view, 350f, 250f, 70f)
            assertTrue(scroll.scrollY > 0)
            assertEquals(0, savedPage(home))
            swipe(view, 8f, 250f, 70f)
            assertEquals(1, savedPage(home))
        }
    }

    @Test
    fun switchingPagesStopsThePreviousPagesFling() {
        val prefs = Prefs.getInstance(RuntimeEnvironment.getApplication())
        prefs.homePages = 2
        prefs.setAppsPerPage(1, 6)
        prefs.setAppsPerPage(2, 6)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val view = homeFragment(controller.get()).requireView()
            layout(view, 700, 300)
            val scroll = view.findViewById<ScrollView>(R.id.homeAppsScroll)
            scroll.fling(5000)
            view.findViewWithTag<LinearLayout>("pageIndicator").getChildAt(1).performClick()
            repeat(20) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(20))
                scroll.computeScroll()
            }
            assertEquals(0, scroll.scrollY)
        }
    }

    @Test
    @Config(qualifiers = "w700dp-h300dp-mdpi")
    fun overflowingHomeScrollPositionSurvivesRecreation() {
        val prefs = Prefs.getInstance(RuntimeEnvironment.getApplication())
        prefs.setAppsPerPage(1, 6)
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val view = homeFragment(controller.get()).requireView()
            layout(view, 700, 300)
            val scroll = view.findViewById<ScrollView>(R.id.homeAppsScroll)
            scroll.scrollTo(0, 60)
            val expectedScroll = scroll.scrollY
            assertTrue(expectedScroll > 0)

            controller.recreate()

            val restoredView = homeFragment(controller.get()).requireView()
            layout(restoredView, 700, 300)
            assertEquals(expectedScroll, restoredView.findViewById<ScrollView>(R.id.homeAppsScroll).scrollY)
        }
    }

    private fun savedPage(home: HomeFragment): Int {
        val state = Bundle()
        home.onSaveInstanceState(state)
        return state.getInt("currentPage")
    }

    private fun layout(
        view: View,
        width: Int,
        height: Int,
    ) {
        // A second traversal applies the viewport's measured status-bar margin.
        repeat(2) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
    }

    private fun swipe(
        view: View,
        x: Float,
        startY: Float,
        endY: Float,
    ) {
        val start = SystemClock.uptimeMillis()
        for ((action, elapsed, y) in listOf(
            Triple(MotionEvent.ACTION_DOWN, 0L, startY),
            Triple(MotionEvent.ACTION_MOVE, 40L, (startY + endY) / 2f),
            Triple(MotionEvent.ACTION_MOVE, 80L, endY),
            Triple(MotionEvent.ACTION_UP, 120L, endY),
        )) {
            val event = MotionEvent.obtain(start, start + elapsed, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun homeFragment(activity: MainActivity): HomeFragment {
        val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHost.childFragmentManager.primaryNavigationFragment as HomeFragment
    }
}
