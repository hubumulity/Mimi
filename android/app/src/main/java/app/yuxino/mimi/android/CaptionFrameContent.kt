package app.yuxino.mimi.android

import android.content.Context
import android.view.View
import android.widget.TextView
import app.yuxino.mimi.android.capture.CaptureHealth
import app.yuxino.mimi.android.provider.SubtitleBus

/** Native presentation of the bounded shared subtitle state; never retains a transcript. */
internal data class CaptionFrameContent(
    val original: String,
    val translated: String,
    val hidden: Boolean = false,
    val status: String = "",
    val history: String = "",
    val historyLimit: Int = 0,
    val confirmed: Boolean = false,
    val timestamp: String = "",
) {
    fun applyRows(source: TextView, translation: TextView, statusView: TextView, mode: CaptionDisplayMode) {
        source.setCaptionText(original); translation.setCaptionText(translated); statusView.setCaptionText(status)
        source.visibility = if (!hidden && mode.sourceVisible && original.isNotBlank()) View.VISIBLE else View.GONE
        translation.visibility = if (!hidden && mode.translationVisible && translated.isNotBlank()) View.VISIBLE else View.GONE
        statusView.visibility = if (status.isBlank()) View.GONE else View.VISIBLE
    }

    companion object {
        fun live(context: Context, mode: CaptionDisplayMode, historyLimit: Int,
                 captureState: CaptureHealth.State?, running: Boolean, stopping: Boolean): CaptionFrameContent {
            val state = SubtitleBus.displaySnapshot().forReading(mode)
            val visible = !state.hidden && (mode.sourceVisible && state.source.isNotBlank() ||
                mode.translationVisible && state.translation.isNotBlank())
            val localized = InterfaceLanguage.context(context)
            val runtime = runtimeFeedbackResource(state.feedback)?.let(localized::getString) ?: state.status
            val status = when {
                captureState == CaptureHealth.State.NO_PCM -> localized.getString(R.string.capture_no_sound_hint)
                runtime.isNotBlank() -> runtime
                running && !stopping && !visible -> localized.getString(R.string.overlay_listening_empty)
                else -> ""
            }
            val showTime = SettingsStore.subtitleTimestamps(context)
            val currentTime = state.createdAt
            val history = if (historyLimit > 0) state.history.takeLast(historyLimit)
                .filterNot { currentTime != null && it.createdAt == currentTime }.joinToString("\n\n") { timed ->
                val pair = timed.pair
                listOfNotNull(captionTime(timed.createdAt).takeIf { showTime && it.isNotBlank() }, pair.source.takeIf { mode.sourceVisible && it.isNotBlank() },
                    pair.translation.takeIf { mode.translationVisible && it.isNotBlank() }).joinToString("\n")
            } else ""
            return CaptionFrameContent(state.source, state.translation, state.hidden, status, history, historyLimit, state.confirmed,
                if (showTime) captionTime(currentTime) else "")
        }
    }
}

/** Keep the shared confirmed pair readable while the next translation is pending.
 * Replace the entire pair, never attach an old translation to a new original. */
internal fun SubtitleBus.DisplaySnapshot.forReading(mode: CaptionDisplayMode): SubtitleBus.DisplaySnapshot {
    if (hidden || !mode.translationVisible || translation.isNotBlank()) return this
    val retained = retainedConfirmed?.takeIf { it.pair.translation.isNotBlank() } ?: return this
    return copy(source = retained.pair.source, translation = retained.pair.translation,
        confirmed = true, createdAt = retained.createdAt)
}

/** Unchanged runtime/health publications must not reset a TextView's native viewport. */
internal fun TextView.setCaptionText(value: String) {
    if (text.toString() != value) text = value
}
