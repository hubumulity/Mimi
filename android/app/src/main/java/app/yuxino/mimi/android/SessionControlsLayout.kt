package app.yuxino.mimi.android

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView

/** Use one footer row when the actual localized labels fit, otherwise stack them. */
class SessionControlsLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val state = getChildAt(0)
        val action = getChildAt(1)
        action.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val label = findViewById<TextView>(R.id.status)
        val stateWidth = Layout.getDesiredWidth(label.text, label.paint) + dp(48)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val fits = stateWidth + action.measuredWidth + dp(16) <= available
        orientation = if (fits) HORIZONTAL else VERTICAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        state.layoutParams = if (fits) LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) }
            else LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        action.layoutParams = LayoutParams(if (fits) -2 else -1, -2)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
