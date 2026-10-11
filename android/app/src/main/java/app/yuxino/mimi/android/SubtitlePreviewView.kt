package app.yuxino.mimi.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Clearly marked sample content; never starts capture or opens a provider session. */
class SubtitlePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val exampleLabel = TextView(context)
    private val previewLabel = TextView(context)
    private var livePreview = false
    private var widthPercent = 92
    private var displayMode = CaptionDisplayMode.BILINGUAL
    private val status = TextView(context).apply { textSize = 16f; setTextColor(Color.LTGRAY); visibility = GONE }
    private val source = app.yuxino.mimi.android.capture.LatestCaptionTextView(context).apply { maxLines = 1 }
    private val translation = app.yuxino.mimi.android.capture.LatestCaptionTextView(context).apply { maxLines = 1 }
    private val subtitle = LinearLayout(context)

    init {
        orientation = VERTICAL
        minimumHeight = dp(152)
        setPadding(dp(18), dp(16), dp(18), dp(18))
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.mimi_preview_bg))
            cornerRadius = dp(14).toFloat()
        }
        val labelRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(previewLabel.apply {
            setText(R.string.preview_label)
            setTextColor(Color.rgb(184, 184, 184))
            textSize = 11f
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        labelRow.addView(exampleLabel.apply {
            setText(R.string.preview_example)
            setTextColor(Color.rgb(145, 145, 145))
            textSize = 11f
        })
        addView(labelRow)
        subtitle.orientation = VERTICAL
        subtitle.setPadding(dp(12), dp(9), dp(12), dp(10))
        source.setTextColor(Color.rgb(203, 203, 203))
        source.textSize = 14f
        source.setLineSpacing(dp(3).toFloat(), 1f)
        translation.setTextColor(Color.WHITE)
        translation.textSize = 19f
        translation.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        translation.setLineSpacing(dp(4).toFloat(), 1f)
        // Reserve one readable line at the largest supported size for each
        // language. Width, font and live-text changes must not move the sliders
        // under the user's finger. Full captions remain in the reading panel.
        source.minimumHeight = maximumLineHeight(source, 24f)
        translation.minimumHeight = maximumLineHeight(translation, 27f)
        subtitle.minimumHeight = source.minimumHeight + translation.minimumHeight + dp(27)
        subtitle.gravity = Gravity.CENTER_VERTICAL
        subtitle.addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        subtitle.addView(source, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        subtitle.addView(translation, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        addView(subtitle, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        configure(16, Color.WHITE, 100, 65, "zh", false)
    }

    fun configure(fontSize: Int, color: Int, opacity: Int, backgroundAlpha: Int, targetLang: String, immersive: Boolean, originalOnly: Boolean = false, mode: CaptionDisplayMode = CaptionDisplayMode.BILINGUAL, widthPercent: Int = 92, live: Boolean = false) {
        livePreview = live
        if (!live) {
            previewLabel.setText(R.string.preview_label)
            exampleLabel.setText(R.string.preview_example)
            status.visibility = GONE
        }
        val effective = if (originalOnly) CaptionDisplayMode.ORIGINAL else mode
        displayMode = effective
        source.visibility = if (effective.sourceVisible) VISIBLE else GONE
        translation.visibility = if (effective.translationVisible) VISIBLE else GONE
        this.widthPercent = widthPercent.coerceIn(55, 100)
        updateWidth()
        if (!live) {
            source.setText(when (targetLang) {
                "en" -> R.string.preview_zh
                "th" -> R.string.preview_en
                else -> R.string.preview_source
            })
            translation.setText(when (targetLang) {
                "en" -> R.string.preview_en
                "ja" -> R.string.preview_ja
                "th" -> R.string.preview_th
                else -> R.string.preview_zh
            })
        }
        CaptionTypography.apply(source, translation, fontSize, immersive, effective == CaptionDisplayMode.ORIGINAL)
        CaptionTypography.applyContrast(status, immersive)
        status.setTextColor(if (immersive) Color.WHITE else Color.LTGRAY)
        translation.setTextColor(color)
        source.setTextColor(if (effective == CaptionDisplayMode.ORIGINAL) color else Color.rgb(203, 203, 203))
        subtitle.alpha = if (immersive) 0.8f else opacity.coerceIn(20, 100) / 100f
        subtitle.background = GradientDrawable().apply {
            setColor(Color.argb(if (immersive) 0 else backgroundAlpha.coerceIn(0, 90) * 255 / 100, 16, 16, 16))
            cornerRadius = dp(12).toFloat()
        }
    }

    fun showLive(original: String, translated: String, hidden: Boolean, waiting: String) {
        livePreview = true
        previewLabel.setText(R.string.home_title)
        exampleLabel.text = ""
        source.text = if (hidden) "" else original
        translation.text = if (hidden) "" else translated
        source.visibility = if (displayMode.sourceVisible && source.text.isNotEmpty()) VISIBLE else GONE
        translation.visibility = if (displayMode.translationVisible && translation.text.isNotEmpty()) VISIBLE else GONE
        status.text = waiting
        status.visibility = if ((source.visibility != VISIBLE || source.text.isNullOrBlank()) &&
            (translation.visibility != VISIBLE || translation.text.isNullOrBlank())) VISIBLE else GONE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateWidth()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val compact = MeasureSpec.getSize(widthMeasureSpec) < dp(320)
        // A narrow preview needs one sample/live label, not two wrapped headings.
        previewLabel.visibility = if (!compact || livePreview) VISIBLE else GONE
        exampleLabel.visibility = if (livePreview) GONE else VISIBLE
        minimumHeight = dp(if (compact) 132 else 152)
        setPadding(dp(18), dp(if (compact) 8 else 16), dp(18), dp(if (compact) 8 else 18))
        subtitle.setPadding(dp(12), dp(if (compact) 6 else 9), dp(12), dp(if (compact) 6 else 10))
        (subtitle.layoutParams as LayoutParams).topMargin = dp(if (compact) 8 else 14)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    private fun updateWidth() {
        if (width <= 0) return
        val desired = ((width - paddingLeft - paddingRight).coerceAtLeast(1) * widthPercent / 100f).toInt().coerceAtLeast(1)
        val params = subtitle.layoutParams as? LayoutParams ?: return
        if (params.width != desired) subtitle.layoutParams = params.apply { width = desired; gravity = Gravity.CENTER_HORIZONTAL }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun maximumLineHeight(view: TextView, sizeSp: Float): Int {
        val paint = TextPaint(view.paint).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
        }
        // Include the supported scripts' fallback metrics, including Thai marks.
        val glyphs = "Agé中文あ한กำลังฟัง"
        return StaticLayout.Builder.obtain(glyphs, 0, glyphs.length, paint, 4096)
            .setIncludePad(view.includeFontPadding).setUseLineSpacingFromFallbacks(true).build().height
    }
}
