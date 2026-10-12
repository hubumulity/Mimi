package app.yuxino.mimi.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.yuxino.mimi.android.capture.LatestCaptionTextView

/** Actual compact immersion text, also rendered unchanged inside the preview stage. */
internal class CompactCaptionView(context: Context) : LinearLayout(context) {
    val status = TextView(context).apply { tag = "overlay-status"; visibility = View.GONE }
    val source = LatestCaptionTextView(context).apply { maxLines = 2 }
    val translation = LatestCaptionTextView(context).apply { maxLines = 3; fixedAnchorAtTop = true }
    val timestamp = captionTimestampView(context)
    private var displayMode = CaptionDisplayMode.BILINGUAL
    private var reserveBilingualLanes = true
    private var sourceLineHeight = 0
    private var translationLineHeight = 0

    init {
        tag = "compact-subtitle"
        orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(3), dp(9), dp(3), dp(10))
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
        addView(status); addView(source); addView(translation); addView(timestamp)
    }

    fun applyStyle(style: CaptionFrameStyle, immersive: Boolean) {
        source.setFixedLaneHeight(null); translation.setFixedLaneHeight(null)
        displayMode = style.mode
        CaptionTypography.apply(source, translation, style.fontSize, immersive, style.mode == CaptionDisplayMode.ORIGINAL)
        CaptionTypography.applyContrast(status, immersive)
        status.textSize = style.fontSize.toFloat(); status.setTextColor(style.color)
        source.setTextColor(if (style.mode == CaptionDisplayMode.ORIGINAL) style.color else 0xFFE7E7E7.toInt())
        translation.setTextColor(style.color)
        listOf(status, source, translation, timestamp).forEach { it.gravity = style.alignment.gravity }
        CaptionTypography.applyContrast(timestamp, immersive)
        CaptionSurface.apply(this, style, immersive)
        sourceLineHeight = CaptionTypography.minimumLineHeight(source)
        translationLineHeight = CaptionTypography.minimumLineHeight(translation)
    }

    fun showContent(content: CaptionFrameContent, mode: CaptionDisplayMode) {
        displayMode = mode
        reserveBilingualLanes = content.reserveBilingualLanes
        val fixedLanes = reserveBilingualLanes && mode == CaptionDisplayMode.BILINGUAL
        content.applyRows(source, translation, status, mode, fixedLanes)
        content.applyTimestamp(timestamp, fixedLanes)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fixed = reserveBilingualLanes && displayMode == CaptionDisplayMode.BILINGUAL
        source.setFixedLaneHeight(if (fixed) sourceLineHeight * 2 else null, 2)
        translation.setFixedLaneHeight(if (fixed) translationLineHeight * 3 else null, 3)
        if (!fixed) { source.maxLines = 2; translation.maxLines = 3 }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
