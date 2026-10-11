package app.yuxino.mimi.android.capture

import android.content.Context
import android.annotation.SuppressLint
import android.view.ViewTreeObserver
import android.widget.TextView

/** A bounded compact viewport following the latest lines without discarding text. */
// Shared by the preview and system overlay, which has no AppCompat Activity/theme.
@SuppressLint("AppCompatCustomView")
internal class LatestCaptionTextView(context: Context) : TextView(context) {
    var followsLatest = true
    private val followLatest = ViewTreeObserver.OnPreDrawListener {
        // Reconcile in this frame. Cancelling the whole window draw here can
        // starve painting while streaming text keeps requesting new layouts.
        followTail()
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(followLatest)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(followLatest)
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        followTail()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val textLayout = layout ?: return
        if (!followsLatest || textLayout.lineCount == 0 || android.view.View.MeasureSpec.getMode(heightMeasureSpec) == android.view.View.MeasureSpec.EXACTLY) return
        // TextView's maxLines measures the first lines. A tail can contain a
        // taller fallback font, so size the viewport for the actual last lines.
        val first = (textLayout.lineCount - maxLines).coerceAtLeast(0)
        val tailHeight = textLayout.getLineBottom(textLayout.lineCount - 1) - textLayout.getLineTop(first) +
            compoundPaddingTop + compoundPaddingBottom
        setMeasuredDimension(measuredWidth, resolveSize(tailHeight, heightMeasureSpec))
    }

    override fun onPreDraw(): Boolean {
        // TextView brings the first line into view after setText/measurement.
        // Finish that native reconciliation before following our newest line,
        // so it cannot reset the viewport after our observer already ran.
        super.onPreDraw()
        followTail()
        return true
    }

    private fun followTail(): Boolean {
        val textLayout = layout ?: return false
        val visibleHeight = height - compoundPaddingTop - compoundPaddingBottom
        if (visibleHeight <= 0) return false
        // Align a whole line, rather than cutting through the preceding line's
        // glyphs because the first line includes extra font padding.
        val firstVisibleLine = if (followsLatest) (textLayout.lineCount - maxLines).coerceAtLeast(0) else 0
        val tail = textLayout.getLineTop(firstVisibleLine)
        if (scrollY == tail) return false
        scrollTo(0, tail)
        return true
    }
}
