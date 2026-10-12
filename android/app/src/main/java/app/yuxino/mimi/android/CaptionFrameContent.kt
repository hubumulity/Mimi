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
    val timestampsEnabled: Boolean = false,
    val reserveBilingualLanes: Boolean = true,
) {
    fun applyRows(source: TextView, translation: TextView, statusView: TextView, mode: CaptionDisplayMode,
                  reserveEmptyLanes: Boolean = false) {
        source.setCaptionText(original); translation.setCaptionText(translated); statusView.setCaptionText(status)
        fun visibility(enabled: Boolean, text: String): Int = when {
            !enabled -> View.GONE
            !hidden && text.isNotBlank() -> View.VISIBLE
            reserveEmptyLanes -> View.INVISIBLE
            else -> View.GONE
        }
        source.visibility = visibility(mode.sourceVisible, original)
        translation.visibility = visibility(mode.translationVisible, translated)
        statusView.visibility = if (status.isBlank()) View.GONE else View.VISIBLE
    }

    fun applyTimestamp(view: TextView, reserveEmpty: Boolean) {
        view.setCaptionText(timestamp)
        view.visibility = when {
            timestamp.isNotBlank() && !hidden -> View.VISIBLE
            reserveEmpty && (timestampsEnabled || timestamp.isNotBlank()) -> View.INVISIBLE
            else -> View.GONE
        }
    }

    companion object {
        fun live(context: Context, mode: CaptionDisplayMode, historyLimit: Int,
                 captureState: CaptureHealth.State?, running: Boolean, stopping: Boolean,
                 sourceLanguage: String = SettingsStore.sourceLang(context),
                 targetLanguage: String = SettingsStore.targetLang(context)): CaptionFrameContent {
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
                if (showTime) captionTime(currentTime) else "", showTime,
                captionReservesBilingualLanes(sourceLanguage, targetLanguage, SubtitleBus.detectedSourceLanguage))
        }
    }
}

/** Original reading follows recognition independently of translation. Translated reading
 * keeps the shared complete pair while waiting, never mixing sentence owners. */
internal fun SubtitleBus.DisplaySnapshot.forReading(mode: CaptionDisplayMode): SubtitleBus.DisplaySnapshot {
    if (hidden) return this
    if (mode == CaptionDisplayMode.ORIGINAL) {
        val current = copy(source = currentSource.text,
            confirmed = currentSource.confirmed, createdAt = currentSource.createdAt)
        if (currentSource.text.isNotBlank()) return current
        // ASR may publish an empty opening draft between utterances. Keep the
        // shared completed original until actual new words arrive, just as the
        // translated modes keep their completed pair while MT is pending.
        val retained = retainedConfirmed?.takeIf { it.pair.source.isNotBlank() }
            ?: return if (source.isNotBlank()) this else current
        return copy(source = retained.pair.source, confirmed = true, createdAt = retained.createdAt)
    }
    if (!mode.translationVisible || translation.isNotBlank()) return this
    val retained = retainedConfirmed?.takeIf { it.pair.translation.isNotBlank() } ?: return this
    return copy(source = retained.pair.source, translation = retained.pair.translation,
        confirmed = true, createdAt = retained.createdAt)
}

/** Unchanged runtime/health publications must not reset a TextView's native viewport. */
internal fun TextView.setCaptionText(value: String) {
    if (text.toString() != value) text = value
}

/** Route intent only; identical words never imply a shared sentence owner. */
internal fun captionReservesBilingualLanes(source: String, target: String, detected: String?): Boolean {
    if (target == "original") return false
    if (target == "zh_en") return true
    val knownSource = detected ?: source.takeUnless { it == "auto" }
    return knownSource == null || knownSource != target
}
