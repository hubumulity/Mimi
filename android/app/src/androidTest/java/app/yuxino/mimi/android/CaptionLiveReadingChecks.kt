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
    fun run(arguments: Bundle?) {
        check(!MimiService.isRunning)
        val mode = SettingsStore.displayMode(context)
        val immersed = SettingsStore.immersiveSubtitles(context)
        val originalSource = SettingsStore.sourceLang(context)
        val originalTarget = SettingsStore.targetLang(context)
        val seconds = arguments?.getString("phase_seconds")?.toIntOrNull()
        check(seconds == null || seconds in 30..300)
        val phases = if (seconds == null) listOf(CaptionDisplayMode.BILINGUAL to 90, CaptionDisplayMode.TRANSLATION to 60,
            CaptionDisplayMode.ORIGINAL to 60, CaptionDisplayMode.BILINGUAL to 60)
            else listOf(CaptionDisplayMode.BILINGUAL to seconds, CaptionDisplayMode.TRANSLATION to seconds,
                CaptionDisplayMode.ORIGINAL to seconds)
        val label = arguments?.getString("result_label") ?: "default"
        check(label.matches(Regex("[a-z0-9-]{1,40}")))
        var provider = SettingsStore.provider(context)
        var models = emptyList<String>()
        var source = originalSource
        var target = originalTarget
        var temporary: TemporaryProviderAcceptance? = null
        val results = JSONArray()
        var failure: Throwable? = null
        val drawFailure = AtomicReference<String?>()
        val draws = AtomicInteger()
        val captionDraws = AtomicInteger()
        val handoffDraws = AtomicInteger()
        val observedMode = AtomicReference<CaptionDisplayMode?>()
        var lastSize: Pair<Int, Int>? = null
        val changes = AtomicInteger()
        var observer: ViewTreeObserver? = null
        val listener = ViewTreeObserver.OnDrawListener {
            draws.incrementAndGet()
            val current = root()
            if (current != null) {
                val panel = current.findViewWithTag<CaptionPanelView>("expanded-subtitles")
                if (panel.isShown && panel.followingLive) {
                    if (listOf(panel.source, panel.translation).any { it.isShown && it.text.isNotBlank() }) {
                        captionDraws.incrementAndGet()
                    }
                    val snapshot = SubtitleBus.displaySnapshot()
                    if (observedMode.get()?.translationVisible == true && !snapshot.hidden &&
                        snapshot.retainedConfirmed?.pair?.translation?.isNotBlank() == true) {
                        if (snapshot.translation.isBlank()) handoffDraws.incrementAndGet()
                        if (!panel.translation.isShown) drawFailure.set("confirmed_translation_replaced_by_placeholder")
                    }
                    if (observedMode.get() == CaptionDisplayMode.ORIGINAL && !snapshot.hidden &&
                        (snapshot.source.isNotBlank() || snapshot.retainedConfirmed?.pair?.source?.isNotBlank() == true)) {
                        if (snapshot.currentSource.text.isBlank()) handoffDraws.incrementAndGet()
                        if (!panel.source.isShown) drawFailure.set("retained_original_replaced_by_placeholder")
                    }
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
            if (arguments?.getString("temporary_provider") == "true") {
                temporary = TemporaryProviderAcceptance(context)
                temporary.apply()
            }
            check(SettingsStore.isConfigured(context))
            arguments?.getString("test_target")?.let { requested ->
                check(requested in listOf("ja", "ko", "th"))
                val selected = SettingsStore.configuration(context).provider
                check(selected.supportsPair("en", requested, SettingsStore.textTranslationProvider(context)))
                SettingsStore.setSourceLang(context, "en"); SettingsStore.setTargetLang(context, requested)
            }
            provider = SettingsStore.provider(context)
            source = SettingsStore.sourceLang(context); target = SettingsStore.targetLang(context)
            models = runtimeModelNames(SettingsStore.runtimeConfiguration(context))
            test.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status("CAPTION_LIVE_READY provider=$provider models=${models.joinToString(",")} source=$source target=$target; start native playback capture with fresh consent, then resume the same video; recording/history preferences retained.")
            val deadline = SystemClock.elapsedRealtime() + 180000
            while ((!MimiService.isRunning || root() == null) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            check(MimiService.isRunning && root() != null) { "native_start_timeout" }
            check(MimiService.activeModelNames == models) { "active_model_mismatch" }
            // Starting capture and sending a media key do not prove that the
            // browser resumed playback. Do not score a paused-input interval.
            val audioDeadline = SystemClock.elapsedRealtime() + 30000
            while (MimiService.isRunning && MimiService.captureObservation?.state?.name != "AUDIO" &&
                SystemClock.elapsedRealtime() < audioDeadline) SystemClock.sleep(100)
            check(MimiService.isRunning && MimiService.captureObservation?.state?.name == "AUDIO") { "playback_audio_start_timeout" }
            ui { observer = root()!!.viewTreeObserver; observer!!.addOnDrawListener(listener) }
            for ((selected, seconds) in phases) {
                observedMode.set(null)
                ui { SettingsStore.setImmersiveSubtitles(context, false); SettingsStore.setDisplayMode(context, selected) }
                SystemClock.sleep(600)
                ui { lastSize = null; changes.set(0) }
                observedMode.set(selected)
                val beforeDraws = draws.get()
                val beforeCaptionDraws = captionDraws.get()
                val beforeHandoffs = handoffDraws.get()
                var sourceUpdates = 0; var translationUpdates = 0
                var sourceHash = 0; var translationHash = 0
                var maximumSource = 0; var maximumTranslation = 0
                var audioSamples = 0
                var maximumSourceGapMs = 0L
                var firstSourceMs: Long? = null; var firstTranslationMs: Long? = null
                var confirmedUpdates = 0
                var lastConfirmedAt: Long? = SubtitleBus.displaySnapshot().createdAt
                var firstConfirmedMs: Long? = null
                var maximumConfirmedGapMs = 0L
                val start = SystemClock.elapsedRealtime()
                var lastConfirmedUpdateAt = start
                var lastSourceAt = start
                val feedbackEvents = JSONArray()
                var lastFeedback: SubtitleBus.RuntimeFeedback? = null
                status("CAPTION_LIVE_PHASE mode=${selected.id} seconds=$seconds")
                var progress = start + 30000
                while (SystemClock.elapsedRealtime() - start < seconds * 1000) {
                    check(MimiService.isRunning) { "capture_stopped" }
                    drawFailure.get()?.let { error(it) }
                    val raw = SubtitleBus.displaySnapshot()
                    val state = raw.forReading(selected)
                    val a = state.source.hashCode(); val b = state.translation.hashCode()
                    val now = SystemClock.elapsedRealtime()
                    if (raw.confirmed && raw.createdAt != null && raw.createdAt != lastConfirmedAt) {
                        confirmedUpdates++
                        if (firstConfirmedMs == null) firstConfirmedMs = now - start
                        maximumConfirmedGapMs = maxOf(maximumConfirmedGapMs, now - lastConfirmedUpdateAt)
                        lastConfirmedUpdateAt = now
                        lastConfirmedAt = raw.createdAt
                    }
                    if (state.source.isNotEmpty() && a != sourceHash) {
                        sourceUpdates++
                        if (firstSourceMs == null) firstSourceMs = now - start
                        maximumSourceGapMs = maxOf(maximumSourceGapMs, now - lastSourceAt)
                        lastSourceAt = now
                    }
                    if (state.translation.isNotEmpty() && b != translationHash) {
                        translationUpdates++
                        if (firstTranslationMs == null) firstTranslationMs = now - start
                    }
                    if (MimiService.captureObservation?.state?.name == "AUDIO") audioSamples++
                    if (state.feedback != lastFeedback && feedbackEvents.length() < 100) {
                        feedbackEvents.put(JSONObject().put("elapsedMs", now - start)
                            .put("connectionStatus", state.feedback.connectionStatus)
                            .put("recoveryReason", state.feedback.recoveryReason ?: JSONObject.NULL)
                            .put("retryScheduled", state.feedback.retryScheduled)
                            .put("translationTimedOut", state.feedback.translationTimedOut))
                        lastFeedback = state.feedback
                    }
                    sourceHash = a; translationHash = b
                    maximumSource = maxOf(maximumSource, state.source.length)
                    maximumTranslation = maxOf(maximumTranslation, state.translation.length)
                    if (SystemClock.elapsedRealtime() >= progress) {
                        status("CAPTION_LIVE_PROGRESS mode=${selected.id} sourceUpdates=$sourceUpdates translationUpdates=$translationUpdates confirmedUpdates=$confirmedUpdates nativeDraws=${draws.get() - beforeDraws} heightChanges=${changes.get()} capture=${MimiService.captureObservation?.state?.name}")
                        progress += 30000
                    }
                    SystemClock.sleep(50)
                }
                results.put(JSONObject().put("mode", selected.id).put("seconds", seconds).put("sourceUpdates", sourceUpdates)
                    .put("translationUpdates", translationUpdates).put("nativeDraws", draws.get() - beforeDraws)
                    .put("maximumSourceChars", maximumSource).put("maximumTranslationChars", maximumTranslation).put("heightChanges", changes.get())
                    .put("readingHandoffDraws", handoffDraws.get() - beforeHandoffs)
                    .put("captionDraws", captionDraws.get() - beforeCaptionDraws)
                    .put("audioSamples", audioSamples).put("firstSourceMs", firstSourceMs ?: JSONObject.NULL)
                    .put("firstTranslationMs", firstTranslationMs ?: JSONObject.NULL).put("maximumSourceGapMs", maximumSourceGapMs)
                    .put("confirmedUpdates", confirmedUpdates).put("firstConfirmedMs", firstConfirmedMs ?: JSONObject.NULL)
                    .put("maximumConfirmedGapMs", maximumConfirmedGapMs)
                    .put("connectionEvents", feedbackEvents))
                check(sourceUpdates >= 3 && translationUpdates >= 3) { "insufficient_live_updates" }
                check(audioSamples > 0) { "no_playback_audio" }
                check(captionDraws.get() - beforeCaptionDraws >= 3) { "no_visible_native_captions" }
                check(changes.get() == 0) { "content_changed_window_height" }
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
            temporary?.close()
            SettingsStore.setSourceLang(context, originalSource); SettingsStore.setTargetLang(context, originalTarget)
            check(SettingsStore.flushPendingWritesForTests(context))
            File(context.getExternalFilesDir(null), "caption-live-reading-$label.json").writeText(JSONObject()
                .put("provider", provider).put("models", JSONArray(models)).put("source", source).put("target", target).put("phases", results)
                .put("passed", failure == null).put("failure", failure?.message ?: JSONObject.NULL).toString(2))
        }
        test.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (failure == null) "Caption live reading passed: ${phases.sumOf { it.second }} seconds across all three display modes; no native tail reset, glyph clipping or content-driven height changes.\n"
                else "Caption live reading failed: ${failure!!.stackTraceToString()}\n")
        })
    }
}
