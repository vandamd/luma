# Foldable window regression checks

This change targets the stable `main` launcher. The first-generation Pixel Fold
has a portrait outer display and a naturally landscape inner display. With
Auto-rotate disabled, Luma now follows the active display's natural orientation;
with it enabled, Luma continues to use Android's rotation preference. No model
name, pixel resolution, or cutout size is hard-coded in production code.

The background fills the window. Content uses the union of the current visible
system bars, display cutout, and keyboard insets. The root owns these insets for
both XML Views and Compose screens. Hidden system status bars do not remove
camera-cutout protection.

Home rows stay centered when they fit and scroll when they do not. On an
overflowing page, vertical drags over the list scroll the rows. Page indicators
and vertical swipes in the side margins still change pages, including when the
indicators are hidden. Horizontal gestures and long-press settings remain
available in empty list space. The custom status row remains outside the scroll
viewport.

## Automated checks

Run with JDK 17 or newer and the Android SDK required by this repository:

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

With the current AGP 9 / ktlint Gradle plugin combination, `ktlintCheck` can
report `NO-SOURCE` for Kotlin sources. Also run ktlint CLI 1.8.0 against
`app/src/**/*.kt`; an empty Gradle style-check task is not source validation.

- `WindowInsetsTest`: cutout protection with hidden bars, all four edges,
  overlapping inset types, repeated dispatch, keyboard show/hide, and zero-inset
  compact-phone behavior on API 31 and 35
- `FoldContinuityTest`: orientation policy, home-page/scroll preservation across
  recreation, fit/overflow gesture routing, and cancelling a previous page's fling
- `SwipeTouchListenerTest`: cancelling pressed state and delayed long presses
  when a home view is destroyed
- `HomeLayoutTest`: XML/home-row bounds in cover and inner-sized windows, short
  landscape overflow, resize back to a taller window, and enlarged text. Native
  Robolectric renderings are written to `app/build/reports/fold-layout/`

These tests simulate layout and lifecycle behavior. They do not emulate the
Pixel Fold's display hardware, OEM rotation policy, hinge transitions, or touch
input. Renderings use representative dp windows, not physical-device screenshots.
The existing Light Phone density/font defaults are unchanged; the enlarged-text
test deliberately stresses the row layout independently of those defaults.

## Device/emulator checklist

Physical first-generation Pixel Fold validation is still required. Test API 34
and 35 or newer where available, with both Auto-rotate settings, Android rotation
lock on/off, light/dark themes, and gesture/three-button navigation.

- Cold-launch closed and fully open. The inner display should have no portrait
  compatibility strip; the cover camera should not overlap any text or control
- Fold, unfold, rotate, and resize while on home page 2, in the drawer, and in a
  scrolled settings list. Return navigation and page selection should survive
- Begin renaming an app, type without saving, fold/unfold, then save. Text and
  selection should survive; the keyboard must not cover the field or save action
- Try custom, Android, and hidden status-bar modes. Check top/side cutouts and
  the navigation area, including after returning from another app
- Use six apps, long labels, the largest Android text/display settings, and a
  short landscape window. Reach the first and last row; taps and long presses
  should work, and scrolling must not launch an app or change pages
- Hide page indicators and use more than one page. Confirm side-margin swipes
  reach every page even when the rows overflow
- In a window where rows fit, confirm vertical page swipes over rows and empty
  space still work. Resize while scrolled and trigger a notification refresh;
  scrolling should clamp on resize without unrelated refreshes jumping to top
- Check the same home/status-bar/gesture behavior on Light Phone III

The debug APK uses the repository's existing `app.luma.debug` application ID and
a local debug key. It is a test build, not an official release or an upgrade to
an installed production APK. Installing it or changing the default launcher is a
separate user action. Keep the accompanying GPL-3.0 source available when sharing
a build.

## References

- [Google's first-generation Pixel Fold specification sheet](https://storage.googleapis.com/pixel-goog-prod-files/downloads/PixelFoldOnePager.pdf)
- [Landscape foldables and natural orientation](https://developer.android.com/develop/adaptive-apps/guides/foldables/trifolds-and-landscape-foldables)
- [Views edge-to-edge and insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [Display cutouts](https://developer.android.com/develop/ui/views/layout/display-cutout)
- [Issue #70](https://github.com/vandamd/luma/issues/70), with related camera-cutout report [#65](https://github.com/vandamd/luma/issues/65)
