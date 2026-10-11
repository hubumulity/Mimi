package app.yuxino.mimi.android

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView

/** Keep the native preview and floating caption typography in agreement. */
internal object CaptionTypography {
    fun apply(source: TextView, translation: TextView, fontSize: Int, immersive: Boolean, originalOnly: Boolean = false) {
        val size = fontSize.coerceIn(12, 24)
        source.textSize = (size - if (immersive && !originalOnly) 2 else 0).coerceAtLeast(12).toFloat()
        translation.textSize = (size + if (immersive) 0 else 3).toFloat()
        translation.typeface = Typeface.create(if (immersive) "sans-serif-medium" else "sans-serif", if (immersive) Typeface.NORMAL else Typeface.BOLD)
        for (view in listOf(source, translation)) {
            val density = view.resources.displayMetrics.density
            view.setLineSpacing(if (immersive) 2f * density else 0f, 1f)
            applyContrast(view, immersive)
        }
        (translation.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { params ->
            val gap = ((if (immersive) 4 else 6) * translation.resources.displayMetrics.density).toInt()
            if (params.topMargin != gap) translation.layoutParams = params.apply { topMargin = gap }
        }
    }

    fun applyContrast(view: TextView, immersive: Boolean) {
        view.gravity = if (immersive) Gravity.CENTER_HORIZONTAL else Gravity.START
        val density = view.resources.displayMetrics.density
        view.setShadowLayer(if (immersive) 1.25f * density else 0f, 0f, if (immersive) 0.5f * density else 0f, Color.BLACK)
    }
}
