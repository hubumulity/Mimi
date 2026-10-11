package app.yuxino.mimi.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import app.yuxino.mimi.android.capture.MimiService
import app.yuxino.mimi.android.provider.SubtitleBus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Explicit operator-started saved-profile playback; diagnostics never contain caption text. */
internal class CaptionLiveReadingChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private fun ui(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun status(value: String) = test.sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
    private fun root(): View? = WindowInspector.getGlobalWindowViews().firstOrNull { it.tag == "mimi-overlay" }
    fun run() {
        check(!MimiService.isRunning && SettingsStore.isConfigured(context))
        val mode = SettingsStore.displayMode(context)
        val immersed = SettingsStore.immersiveSubtitles(context)
        val phases = listOf(CaptionDisplayMode.BILINGUAL to 90, CaptionDisplayMode.TRANSLATION to 60,
            CaptionDisplayMode.ORIGINAL to 60, CaptionDisplayMode.BILINGUAL to 60)
        val results = JSONArray()
        var failure: Throwable? = null
        val drawFailure = AtomicReference<String?>()
        val draws = AtomicInteger()
        var lastSize: Pair<Int, Int>? = null
        val changes = AtomicInteger()
        var observer: ViewTreeObserver? = null
        val listener = ViewTreeObserver.OnDrawListener {
            draws.incrementAndGet()
            val current = root()
            if (current != null) {
                val panel = current.findViewWithTag<CaptionPanelView>("expanded-subtitles")
                if (panel.isShown && panel.followingLive) {
                    for (row in listOf(panel.source, panel.translation).filter { it.isShown }) {
                        val layout = row.layout ?: continue
                        val first = (layout.lineCount - row.maxLines).coerceAtLeast(0)
                        val tail = layout.getLineTop(first)
                        if (row.scrollY != tail) drawFailure.set("native_tail_reset")
                        val origin = row.top - panel.transcriptScroll.scrollY + row.compoundPaddingTop - row.scrollY
                        if (origin + layout.getLineTop(first) < 0 ||
                            origin + layout.getLineBottom(layout.lineCount - 1) > panel.transcriptScroll.height) drawFailure.set("native_glyph_clipping")
                    }
                    val size = current.width to current.height
                    if (lastSize?.first == size.first && lastSize?.second != size.second) changes.incrementAndGet()
                    lastSize = size
                }
            }
        }
        try {
            test.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status("CAPTION_LIVE_READY; start native playback capture with fresh consent, then resume the same video; recording/history preferences retained.")
            val deadline = SystemClock.elapsedRealtime() + 180000
            while ((!MimiService.isRunning || root() == null) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            check(MimiService.isRunning && root() != null) { "native_start_timeout" }
            ui { observer = root()!!.viewTreeObserver; observer!!.addOnDrawListener(listener) }
            for ((selected, seconds) in phases) {
                ui { SettingsStore.setImmersiveSubtitles(context, false); SettingsStore.setDisplayMode(context, selected) }
                SystemClock.sleep(600)
                ui { lastSize = null; changes.set(0) }
                val beforeDraws = draws.get()
                var sourceUpdates = 0; var translationUpdates = 0
                var sourceHash = 0; var translationHash = 0
                var maximumSource = 0; var maximumTranslation = 0
                val start = SystemClock.elapsedRealtime()
                status("CAPTION_LIVE_PHASE mode=${selected.id} seconds=$seconds")
                var progress = start + 30000
                while (SystemClock.elapsedRealtime() - start < seconds * 1000) {
                    check(MimiService.isRunning) { "capture_stopped" }
                    drawFailure.get()?.let { error(it) }
                    val state = SubtitleBus.displaySnapshot()
                    val a = state.source.hashCode(); val b = state.translation.hashCode()
                    if (state.source.isNotEmpty() && a != sourceHash) sourceUpdates++
                    if (state.translation.isNotEmpty() && b != translationHash) translationUpdates++
                    sourceHash = a; translationHash = b
                    maximumSource = maxOf(maximumSource, state.source.length)
                    maximumTranslation = maxOf(maximumTranslation, state.translation.length)
                    if (SystemClock.elapsedRealtime() >= progress) {
                        status("CAPTION_LIVE_PROGRESS mode=${selected.id} sourceUpdates=$sourceUpdates translationUpdates=$translationUpdates nativeDraws=${draws.get() - beforeDraws} heightChanges=${changes.get()} capture=${MimiService.captureObservation?.state?.name}")
                        progress += 30000
                    }
                    SystemClock.sleep(50)
                }
                check(sourceUpdates >= 3 && translationUpdates >= 3) { "insufficient_live_updates" }
                check(changes.get() == 0) { "content_changed_window_height" }
                results.put(JSONObject().put("mode", selected.id).put("seconds", seconds).put("sourceUpdates", sourceUpdates)
                    .put("translationUpdates", translationUpdates).put("nativeDraws", draws.get() - beforeDraws)
                    .put("maximumSourceChars", maximumSource).put("maximumTranslationChars", maximumTranslation).put("heightChanges", changes.get()))
                status("CAPTION_LIVE_PHASE_PASSED mode=${selected.id}")
            }
        } catch (error: Throwable) { failure = error }
        finally {
            ui {
                observer?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
                SettingsStore.setDisplayMode(context, mode); SettingsStore.setImmersiveSubtitles(context, immersed)
            }
            context.startService(Intent(context, MimiService::class.java).setAction(MimiService.ACTION_STOP))
            repeat(100) { if (MimiService.isRunning || MimiService.isStopping) SystemClock.sleep(100) }
            File(context.getExternalFilesDir(null), "caption-live-reading.json").writeText(JSONObject()
                .put("phases", results).put("passed", failure == null).put("failure", failure?.message ?: JSONObject.NULL).toString(2))
        }
        test.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (failure == null) "Caption live reading passed: 270 seconds across all three display modes; no native tail reset, glyph clipping or content-driven height changes.\n"
                else "Caption live reading failed: ${failure!!.stackTraceToString()}\n")
        })
    }
}
