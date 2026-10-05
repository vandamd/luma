package app.luma.helper

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach

/** Keep the background edge-to-edge while protecting every fragment's content. */
internal fun View.applySafeWindowInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        // Cutouts remain unsafe even when the Android status bar is hidden. Use
        // a union (not a sum) so overlapping bars, cutouts and IME are counted once.
        val safeInsets =
            insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
        view.setPadding(
            initialLeft + safeInsets.left,
            initialTop + safeInsets.top,
            initialRight + safeInsets.right,
            initialBottom + safeInsets.bottom,
        )
        // This root handles IME as well as system bars for both Views and Compose.
        WindowInsetsCompat.CONSUMED
    }
    doOnAttach { ViewCompat.requestApplyInsets(it) }
}
