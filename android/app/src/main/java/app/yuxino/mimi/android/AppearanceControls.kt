package app.yuxino.mimi.android

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Same native controls in settings and the overlay; never starts a provider or capture. */
internal class AppearanceControls(
    context: Context,
    private val requestImmersive: (Boolean) -> Unit,
) : LinearLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private var observing: (() -> Unit)? = null
    private var syncing = false
    private val previewUpdate = Runnable { if (isShown) refreshPreview() }
    private val subtitleListener = object : app.yuxino.mimi.android.provider.SubtitleBus.Listener {
        override fun onSubtitleChanged() { main.removeCallbacks(previewUpdate); main.post(previewUpdate) }
    }
    private val refreshers = mutableListOf<() -> Unit>()
    private val preview = SubtitlePreviewView(context).apply { id = R.id.subtitle_preview }
    private val colorIndices = listOf(1, 0, 2, 3, 4)

    init {
        orientation = VERTICAL
        addView(preview, LayoutParams(-1, -2))
        picker(R.id.display_mode, R.string.settings_display_mode, CaptionDisplayMode.entries.map { context.getString(it.label) },
            { (if (SettingsStore.originalTextOnly(context)) CaptionDisplayMode.ORIGINAL else SettingsStore.displayMode(context)).ordinal },
            { SettingsStore.setDisplayMode(context, CaptionDisplayMode.entries[it]) }).also { spinner ->
            refreshers.add { spinner.isEnabled = !SettingsStore.originalTextOnly(context) }
        }
        slider(R.id.overlay_width, R.id.overlay_width_value, R.string.settings_width, 55, 100,
            { SettingsStore.overlayWidthPercent(context) }, { SettingsStore.setOverlayWidthPercent(context, it) })
        slider(R.id.font_size, R.id.font_size_value, R.string.settings_font, 12, 24,
            { SettingsStore.fontSize(context) }, { SettingsStore.setFontSize(context, it) }, R.string.settings_font_value)
        picker(R.id.translation_color, R.string.settings_color, listOf(
            R.string.settings_color_white, R.string.settings_color_teal, R.string.settings_color_yellow,
            R.string.settings_color_green, R.string.settings_color_pink,
        ).map(context::getString),
            { colorIndices.indexOf(SettingsStore.translationColorIndex(context)).coerceAtLeast(0) },
            { SettingsStore.setTranslationColorIndex(context, colorIndices[it]) })
        val background = slider(R.id.overlay_bg_alpha, R.id.overlay_bg_alpha_value, R.string.settings_background, 0, 90,
            { SettingsStore.overlayBgAlpha(context) }, { SettingsStore.setOverlayBgAlpha(context, it) })
        val opacity = slider(R.id.overlay_opacity, R.id.overlay_opacity_value, R.string.settings_opacity, 20, 100,
            { SettingsStore.overlayOpacity(context) }, { SettingsStore.setOverlayOpacity(context, it) })
        val modeRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        modeRow.addView(helpHeading(R.string.settings_immersive, R.string.settings_immersive_help, "immersive-help").apply { id = R.id.immersive_heading }, LayoutParams(0, -2, 1f))
        val immersive = MaterialSwitch(context).apply {
            id = R.id.immersive_subtitles
            minimumHeight = dp(48)
            contentDescription = context.getString(R.string.settings_immersive)
            trackTintList = ContextCompat.getColorStateList(context, R.color.mimi_switch_track)
            thumbTintList = ContextCompat.getColorStateList(context, R.color.mimi_switch_thumb)
            setOnCheckedChangeListener { _, enabled ->
                if (!syncing) { sync(); save { requestImmersive(enabled) } }
            }
        }
        modeRow.addView(immersive, LayoutParams(-2, -2))
        addView(modeRow, LayoutParams(-1, -2).apply { topMargin = dp(20) })
        refreshers.add {
            immersive.isChecked = SettingsStore.immersiveSubtitles(context)
            background.isEnabled = !immersive.isChecked
            opacity.isEnabled = !immersive.isChecked
        }
        val more = LinearLayout(context).apply { id = R.id.appearance_more_panel; orientation = VERTICAL; visibility = GONE }
        val toggle = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            id = R.id.appearance_more_toggle
            isAllCaps = false; isSingleLine = false; minimumHeight = dp(48); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setText(R.string.settings_more)
            setTextColor(ContextCompat.getColor(context, R.color.mimi_text))
            setOnClickListener {
                more.visibility = if (more.visibility == GONE) VISIBLE else GONE
                setText(if (more.visibility == VISIBLE) R.string.settings_more_collapse else R.string.settings_more)
            }
        }
        addView(toggle, LayoutParams(-1, -2).apply { topMargin = dp(16) })
        addView(more, LayoutParams(-1, -2))
        slider(R.id.history_lines, R.id.history_lines_value, R.string.settings_history, 0, 6,
            { SettingsStore.historyLines(context) }, { SettingsStore.setHistoryLines(context, it) },
            R.string.settings_history_value, more)
        more.addView(MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            id = R.id.reset_position; setText(R.string.settings_reset); isAllCaps = false; isSingleLine = false
            minimumHeight = dp(48); setTextColor(ContextCompat.getColor(context, R.color.mimi_text))
            setOnClickListener { save {
                SettingsStore.clearOverlayPosition(context)
                Toast.makeText(context, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
            } }
        }, LayoutParams(-2, -2).apply { gravity = Gravity.END; topMargin = dp(12) })
        sync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observing = SettingsStore.observeAppearance(context) { main.post { if (isAttachedToWindow) sync() } }
        app.yuxino.mimi.android.provider.SubtitleBus.addListener(subtitleListener)
        sync()
    }
    override fun onDetachedFromWindow() {
        observing?.invoke(); observing = null
        app.yuxino.mimi.android.provider.SubtitleBus.removeListener(subtitleListener)
        main.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
    fun sync() {
        syncing = true
        try {
            refreshers.forEach { it() }
            refreshPreview()
        } finally { syncing = false }
    }
    private fun refreshPreview() {
        preview.configure(SettingsStore.fontSize(context), SettingsStore.translationColor(context),
            SettingsStore.overlayOpacity(context), SettingsStore.overlayBgAlpha(context), SettingsStore.targetLang(context),
            SettingsStore.immersiveSubtitles(context), SettingsStore.originalTextOnly(context),
            SettingsStore.displayMode(context), SettingsStore.overlayWidthPercent(context), live = app.yuxino.mimi.android.capture.MimiService.isRunning)
        if (app.yuxino.mimi.android.capture.MimiService.isRunning) {
            val bus = app.yuxino.mimi.android.provider.SubtitleBus
            preview.showLive(bus.displaySource, bus.displayTranslation, bus.liveHidden, context.getString(R.string.capture_waiting))
        }
    }
    private fun save(action: () -> Unit) {
        try { action() }
        catch (_: RuntimeException) { Toast.makeText(context, R.string.service_save_failed, Toast.LENGTH_LONG).show() }
        sync()
    }
    private fun slider(id: Int, valueId: Int, title: Int, low: Int, high: Int, read: () -> Int, write: (Int) -> Unit,
        format: Int = R.string.settings_percent_value, parent: LinearLayout = this): SeekBar {
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val label = if (id == R.id.history_lines) helpHeading(title, R.string.settings_history_help, "history-help").apply { this.id = R.id.history_heading } else label(title)
        heading.addView(label, LayoutParams(0, -2, 1f))
        val value = TextView(context).apply { this.id = valueId; textSize = 16f; setTextColor(color(R.color.mimi_muted)) }
        heading.addView(value, LayoutParams(-2, -2).apply { marginStart = dp(12) })
        parent.addView(heading, LayoutParams(-1, -2).apply { topMargin = dp(20) })
        val seek = SeekBar(context).apply {
            this.id = id; min = low; max = high; tag = if (id == R.id.font_size) "overlay-font-slider" else null
            contentDescription = context.getString(title)
            progressTintList = ContextCompat.getColorStateList(context, R.color.mimi_text)
            thumbTintList = progressTintList
            progressBackgroundTintList = ContextCompat.getColorStateList(context, R.color.mimi_border)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !syncing) save { write(progress) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        parent.addView(seek, LayoutParams(-1, dp(48)))
        refreshers.add {
            seek.progress = read()
            value.text = if (id == R.id.history_lines && seek.progress == 0) context.getString(R.string.settings_history_off)
                else context.getString(format, seek.progress)
        }
        return seek
    }
    private fun picker(id: Int, title: Int, items: List<String>, read: () -> Int, write: (Int) -> Unit): Spinner {
        addView(if (id == R.id.display_mode) helpHeading(title, R.string.settings_display_mode_help, "display-mode-help")
            else label(title), LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(8) })
        val spinner = Spinner(context).apply {
            this.id = id; contentDescription = context.getString(title)
            background = ContextCompat.getDrawable(context, R.drawable.settings_field)
            adapter = ArrayAdapter(context, R.layout.mimi_spinner_item, items).apply {
                setDropDownViewResource(R.layout.mimi_spinner_dropdown)
            }
            setPopupBackgroundResource(R.color.mimi_bg)
            setSelection(read())
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, itemId: Long) {
                    if (!syncing && position != read() && isEnabled) save { write(position) }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        addView(spinner, LayoutParams(-1, -2))
        refreshers.add { if (spinner.selectedItemPosition != read()) spinner.setSelection(read()) }
        return spinner
    }
    private fun helpHeading(title: Int, help: Int, tag: String): View =
        (context as? androidx.appcompat.app.AppCompatActivity)?.let { activity ->
            HelpUi.heading(activity, activity.getString(title), activity.getString(help), 16f, tag)
        } ?: label(title)
    private fun label(title: Int) = TextView(context).apply {
        setText(title); textSize = 16f; setTypeface(typeface, Typeface.NORMAL); setTextColor(color(R.color.mimi_text))
    }
    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
