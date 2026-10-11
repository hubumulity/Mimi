package app.yuxino.mimi.android

import android.graphics.Rect
import android.view.View
import kotlin.math.abs

/** Compare rendered native bounds, including asymmetric neighboring actions. */
internal fun checkOverlayChromeAlignment(panel: View) {
    fun bounds(view: View): Rect {
        val origin = IntArray(2).also(view::getLocationInWindow)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }
    val frame = bounds(panel)
    for (tag in listOf("overlay-drag-mark", "overlay-route", "overlay-providers")) {
        val view = checkNotNull(panel.findViewWithTag<View>(tag))
        check(view.isShown && view.width > 0)
        check(abs(bounds(view).exactCenterX() - frame.exactCenterX()) <= 1f) {
            "Overlay $tag is not centered in the caption window"
        }
    }
    val providers = bounds(panel.findViewWithTag("overlay-providers"))
    for (tag in listOf("enter-immersive", "overlay-resize")) {
        check(!Rect.intersects(providers, bounds(panel.findViewWithTag(tag)))) {
            "Provider label overlaps $tag"
        }
    }
    check(!Rect.intersects(bounds(panel.findViewWithTag("overlay-route")),
        bounds(panel.findViewWithTag("overlay-font")))) { "Language route overlaps settings" }
}
