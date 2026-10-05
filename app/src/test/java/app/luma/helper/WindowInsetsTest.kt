package app.luma.helper

import android.app.Application
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 35], application = Application::class)
class WindowInsetsTest {
    private val root = FrameLayout(RuntimeEnvironment.getApplication()).apply { applySafeWindowInsets() }

    @Test
    fun hiddenStatusBarStillRespectsCoverCameraAndNavigation() {
        dispatch(cutout = Insets.of(0, 48, 0, 0), bars = Insets.of(0, 0, 0, 24))
        assertPadding(0, 48, 0, 24)
    }

    @Test
    fun visibleStatusBarAndCutoutUseMaximumRatherThanSum() {
        dispatch(cutout = Insets.of(0, 48, 0, 0), bars = Insets.of(0, 64, 0, 48))
        assertPadding(0, 64, 0, 48)
    }

    @Test
    fun rotationMovesSafeAreaToSideAndUnfoldingClearsOldCutout() {
        dispatch(cutout = Insets.of(0, 48, 0, 0))
        dispatch(cutout = Insets.of(48, 0, 0, 0), bars = Insets.of(0, 0, 24, 0))
        assertPadding(48, 0, 24, 0)
        dispatch(bars = Insets.of(0, 0, 0, 24))
        assertPadding(0, 0, 0, 24)
    }

    @Test
    fun keyboardDoesNotAddNavigationHeightAndResetsWhenDismissed() {
        dispatch(bars = Insets.of(0, 0, 0, 24), ime = Insets.of(0, 0, 0, 320))
        assertPadding(0, 0, 0, 320)
        dispatch(bars = Insets.of(0, 0, 0, 24))
        assertPadding(0, 0, 0, 24)
    }

    @Test
    fun repeatedDispatchPreservesOriginalPaddingWithoutAccumulating() {
        val paddedRoot = FrameLayout(RuntimeEnvironment.getApplication())
        paddedRoot.setPadding(1, 2, 3, 4)
        paddedRoot.applySafeWindowInsets()
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(10, 20, 30, 40)).build()
        repeat(3) { ViewCompat.dispatchApplyWindowInsets(paddedRoot, insets) }
        assertEquals(11, paddedRoot.paddingLeft)
        assertEquals(22, paddedRoot.paddingTop)
        assertEquals(33, paddedRoot.paddingRight)
        assertEquals(44, paddedRoot.paddingBottom)
    }

    @Test
    fun lightPhoneWithoutSystemObstructionsKeepsOriginalContentBounds() {
        dispatch()
        assertPadding(0, 0, 0, 0)
    }

    private fun dispatch(
        cutout: Insets = Insets.NONE,
        bars: Insets = Insets.NONE,
        ime: Insets = Insets.NONE,
    ) {
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
                .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
                .setInsets(WindowInsetsCompat.Type.ime(), ime)
                .build()
        assertTrue(ViewCompat.dispatchApplyWindowInsets(root, insets).isConsumed)
    }

    private fun assertPadding(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        assertEquals(left, root.paddingLeft)
        assertEquals(top, root.paddingTop)
        assertEquals(right, root.paddingRight)
        assertEquals(bottom, root.paddingBottom)
    }
}
