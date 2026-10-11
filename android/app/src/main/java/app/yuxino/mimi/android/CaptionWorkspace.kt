package app.yuxino.mimi.android

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** Keep the single caption preview in view while its settings scroll independently. */
class CaptionWorkspace @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    var showsPreview = true
        set(value) { if (field != value) { field = value; requestLayout() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preview = getChildAt(0)
        val controls = getChildAt(1)
        preview.visibility = if (showsPreview) View.VISIBLE else View.GONE
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val sideBySide = showsPreview && width >= dp(600) && width > height
        orientation = if (sideBySide) HORIZONTAL else VERTICAL
        if (sideBySide) {
            preview.layoutParams = LayoutParams((width * .4f).toInt().coerceAtMost(dp(400)), -1)
            controls.layoutParams = LayoutParams(0, -1, 1f)
        } else {
            preview.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((height - dp(128)).coerceAtLeast(0), MeasureSpec.AT_MOST))
            preview.layoutParams = LayoutParams(-1, if (showsPreview) preview.measuredHeight else 0)
            controls.layoutParams = LayoutParams(-1, 0, 1f)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
