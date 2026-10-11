package app.yuxino.mimi.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.SeekBar
import android.widget.TextView
import android.util.TypedValue
import app.yuxino.mimi.android.capture.MimiService
import app.yuxino.mimi.android.provider.SubtitleBus
import java.io.File
import kotlin.math.abs

/** Comparable native Before/After fixtures; no capture, credentials or provider calls. */
internal class OverlayInteractionChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private fun root() = WindowInspector.getGlobalWindowViews().first { it.tag == "mimi-overlay" }
    private fun caption() = root().findViewWithTag<TextView>("expanded-translation")
    private fun captionPixels(sp: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), caption().resources.displayMetrics,
    )
    private fun tapVisible(view: View) {
        val origin = IntArray(2)
        onUi {
            val visible = android.graphics.Rect()
            check(view.getGlobalVisibleRect(visible) && visible.width() == view.width && visible.height() == view.height) {
                "Floating appearance action is clipped"
            }
            view.getLocationOnScreen(origin)
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                origin[0] + view.width / 2f, origin[1] + view.height / 2f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(test.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        idle()
    }
    private fun onUi(action: () -> Unit) {
        val work = FutureTask { action() }
        Handler(Looper.getMainLooper()).post(work)
        work.get(10, TimeUnit.SECONDS)
        idle()
    }
    private fun idle() {
        val completed = CountDownLatch(1)
        test.waitForIdle { completed.countDown() }
        check(completed.await(10, TimeUnit.SECONDS)) { "Native UI did not become idle" }
    }
    private fun waitFor(predicate: () -> Boolean) {
        repeat(60) {
            var ready = false
            onUi { ready = predicate() }
            if (ready) return
            SystemClock.sleep(100)
        }
        error("Overlay interaction did not settle")
    }
    private fun capture(name: String) {
        test.sendStatus(0, Bundle().apply { putString("stream", "OVERLAY_CHECK $name\n") })
        idle()
        SystemClock.sleep(350) // Wait for WindowManager/compositor bounds after overlay resizing.
        val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
        File(checkNotNull(context.getExternalFilesDir("ui-preview")), "interaction-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun drag(seek: SeekBar, value: Int) {
        onUi {
            val x = seek.paddingLeft + (seek.width - seek.paddingLeft - seek.paddingRight) *
                (value - seek.min).toFloat() / (seek.max - seek.min)
            val time = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, seek.height / 2f, 0)
                seek.dispatchTouchEvent(event)
                event.recycle()
            }
        }
    }

    private fun dragCompact(view: View) {
        val origin = IntArray(2)
        var x = 0f
        var y = 0f
        var distance = 0f
        onUi {
            view.getLocationOnScreen(origin)
            x = origin[0] + view.width / 2f
            y = origin[1] + view.height / 2f
            distance = 64 * view.resources.displayMetrics.density
        }
        val down = SystemClock.uptimeMillis()
        for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0f,
            MotionEvent.ACTION_MOVE to distance / 2, MotionEvent.ACTION_MOVE to distance,
            MotionEvent.ACTION_UP to distance)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y - offset, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(test.uiAutomation.injectInputEvent(event, true)) { "Compact drag injection failed" } }
            finally { event.recycle() }
            SystemClock.sleep(30)
        }
        idle()
    }
    private fun dragHorizontally(view: View, distanceDp: Int) {
        val origin = IntArray(2)
        var x = 0f
        var y = 0f
        var distance = 0f
        onUi {
            view.getLocationOnScreen(origin)
            x = origin[0] + view.width / 2f
            y = origin[1] + view.height / 2f
            distance = distanceDp * view.resources.displayMetrics.density
        }
        val down = SystemClock.uptimeMillis()
        for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0f,
            MotionEvent.ACTION_MOVE to distance / 2, MotionEvent.ACTION_MOVE to distance,
            MotionEvent.ACTION_UP to distance)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x + offset, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(test.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
            SystemClock.sleep(30)
        }
        idle()
    }

    /** Run only on a blank idle device; uses the existing synthetic overlay. */
    private fun immersivePlacementChecks(home: MainActivity) {
        val before = root()
        onUi {
            SettingsStore.setFontSize(context, 12)
            SettingsStore.setOverlayYOffset(context, 48)
            check(context.getSharedPreferences("first_run", 0).edit().putBoolean("immersive_seen", true).commit())
            root().findViewWithTag<View>("collapse-overlay").performClick()
        }
        waitFor { root().findViewWithTag<View>("compact-subtitle").isShown }
        dragCompact(root().findViewWithTag<View>("compact-subtitle"))
        waitFor { SettingsStore.overlayYOffset(context) != 48 }
        val savedOffset = SettingsStore.overlayYOffset(context)
        fun bottom(): Int {
            var value = 0
            onUi { val origin = IntArray(2); root().getLocationOnScreen(origin); value = origin[1] + root().height }
            return value
        }
        val anchor = bottom()
        capture("after-positioned-compact")
        onUi { home.findViewById<View>(R.id.immersive_subtitles).performClick() }
        waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "exit-immersive" } }
        onUi {
            check(home.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.immersive_subtitles).isChecked)
            home.findViewById<View>(R.id.immersive_subtitles).performClick()
        }
        waitFor { WindowInspector.getGlobalWindowViews().none { it.tag == "exit-immersive" } }
        check(!root().findViewWithTag<View>("expanded-subtitles").isShown)
        repeat(3) { iteration ->
            onUi { root().findViewWithTag<View>("compact-subtitle").performClick() }
            waitFor { root().findViewWithTag<View>("expanded-subtitles").isShown }
            onUi { root().findViewWithTag<View>("enter-immersive").performClick() }
            waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "exit-immersive" } }
            check(root() === before) { "Mode toggle replaced the caption window" }
            check(abs(bottom() - anchor) <= 2) { "Immersive changed the compact bottom anchor" }
            onUi {
                val text = (root().findViewWithTag<View>("compact-subtitle") as android.view.ViewGroup).getChildAt(2) as TextView
                check(text.gravity and android.view.Gravity.HORIZONTAL_GRAVITY_MASK == android.view.Gravity.CENTER_HORIZONTAL && text.typeface.weight <= 500) {
                    "Immersive text did not use the centered, lighter presentation: gravity=${text.gravity}, weight=${text.typeface.weight}"
                }
                check(abs(text.textSize - android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    SettingsStore.fontSize(context).toFloat(), text.resources.displayMetrics)) < 1) {
                    "Immersive text did not respect the chosen size"
                }
            }
            if (iteration == 0) capture("after-positioned-immersive")
            onUi { WindowInspector.getGlobalWindowViews().first { it.tag == "exit-immersive" }.performClick() }
            waitFor { root().findViewWithTag<View>("expanded-subtitles").isShown }
            if (iteration == 0) capture("after-positioned-expanded-restored")
            onUi { root().findViewWithTag<View>("collapse-overlay").performClick() }
            waitFor { root().findViewWithTag<View>("compact-subtitle").isShown }
            check(abs(bottom() - anchor) <= 2)
            check(SettingsStore.overlayYOffset(context) == savedOffset)
            onUi {
                val text = (root().findViewWithTag<View>("compact-subtitle") as android.view.ViewGroup).getChildAt(2) as TextView
                check(text.gravity and android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK == android.view.Gravity.START && text.typeface.weight >= 700 && text.shadowRadius == 0f) {
                    "Exiting immersive mode retained its typography"
                }
            }
            if (iteration == 0) capture("after-positioned-compact-restored")
        }
        onUi {
            SettingsStore.setImmersiveSubtitles(context, true)
            SettingsStore.setFontSize(context, 24)
            SubtitleBus.onFinalPair("Synthetic source with several words.", "合成字幕用于内容高度变化检查。", "en")
        }
        waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "exit-immersive" } }
        check(abs(bottom() - anchor) <= 2) { "Font/content reflow changed the bottom anchor" }
        onUi { SettingsStore.setImmersiveSubtitles(context, false) }
        waitFor { WindowInspector.getGlobalWindowViews().none { it.tag == "exit-immersive" } }
        check(!root().findViewWithTag<View>("expanded-subtitles").isShown)
        check(SettingsStore.overlayYOffset(context) == savedOffset)
        onUi { SettingsStore.setFontSize(context, 12) }
        context.stopService(Intent(context, MimiService::class.java))
        waitFor { WindowInspector.getGlobalWindowViews().none { it.tag == "mimi-overlay" } }
        context.startService(Intent(context, MimiService::class.java).setAction(MimiService.ACTION_UI_PREVIEW).putExtra("preview_over_own_ui", true))
        waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "mimi-overlay" } }
        waitFor { root().findViewWithTag<View>("compact-subtitle").isShown }
        check(SettingsStore.overlayYOffset(context) == savedOffset)
        check(abs(bottom() - anchor) <= 2) { "Reopened overlay lost the saved drag position" }
        capture("after-positioned-reopened")
    }

    private fun liveDisplayControls() {
        val sameWindow = root()
        val history = SubtitleBus.historySnapshot()
        onUi { SubtitleBus.onSourceDraft("Synthetic original text.", "en"); SubtitleBus.onTranslationDraft("合成译文。") }
        for (mode in CaptionDisplayMode.entries) {
            onUi { SettingsStore.setDisplayMode(context, mode); SettingsStore.setTranslationColorIndex(context, 2) }
            waitFor {
                root().findViewWithTag<View>("expanded-source").isShown == mode.sourceVisible &&
                    root().findViewWithTag<View>("expanded-translation").isShown == mode.translationVisible
            }
            check(root() === sameWindow && SubtitleBus.historySnapshot() == history)
            if (mode == CaptionDisplayMode.ORIGINAL) onUi {
                check(root().findViewWithTag<TextView>("expanded-source").currentTextColor == SettingsStore.translationColor(context))
            }
            capture("mode-${mode.id}")
        }
        onUi { SettingsStore.setDisplayMode(context, CaptionDisplayMode.BILINGUAL); SettingsStore.setTranslationColorIndex(context, 1) }
        val oldWidth = root().width
        onUi { SettingsStore.setOverlayWidthPercent(context, 65) }
        waitFor { root().width < oldWidth }
        capture("width-65")
        onUi { SettingsStore.setOverlayWidthPercent(context, 55) }
        waitFor { root().width < oldWidth * 0.7f }
        val narrowWidth = root().width
        dragHorizontally(root().findViewWithTag("overlay-resize"), 24)
        waitFor { SettingsStore.overlayWidthPercent(context) > 55 && root().width > narrowWidth }
        val resizedWidth = root().width
        capture("direct-resize")
        dragHorizontally(root().findViewWithTag("overlay-drag"), -20)
        waitFor { SettingsStore.overlayXOffset(context, true) < 0 }
        check(root().width == resizedWidth && SettingsStore.overlayXOffset(context, false) == 0)
        val oldY = SettingsStore.expandedYOffset(context)
        dragCompact(root().findViewWithTag("overlay-drag"))
        waitFor { SettingsStore.expandedYOffset(context) > oldY }
        check(SettingsStore.overlayYOffset(context) == 48) { "Expanded drag changed compact anchor" }
        val savedY = SettingsStore.expandedYOffset(context)
        onUi { SettingsStore.setFontSize(context, 20) }
        waitFor { SettingsStore.fontSize(context) == 20 && abs(caption().textSize - captionPixels(23)) < 1 }
        check(SettingsStore.expandedYOffset(context) == savedY)
        onUi { SettingsStore.setOverlayWidthPercent(context, 92) }
        waitFor { root().width == oldWidth }
        capture("expanded-dragged")
    }

    private fun ownSurfaceChecks(home: MainActivity) {
        context.stopService(Intent(context, MimiService::class.java))
        waitFor { WindowInspector.getGlobalWindowViews().none { it.tag == "mimi-overlay" } }
        context.startService(Intent(context, MimiService::class.java).setAction(MimiService.ACTION_UI_PREVIEW))
        waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "mimi-overlay" } && !root().isShown }
        capture("home-uncovered")
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor { root().isShown }
        val existing = root()
        capture("outside-mimi")
        onUi { root().findViewWithTag<View>("compact-subtitle").performClick() }
        waitFor { root().findViewWithTag<View>("expanded-subtitles").isShown }
        onUi { root().findViewWithTag<View>("overlay-font").performClick() }
        waitFor { WindowInspector.getGlobalWindowViews().any { it.findViewWithTag<SeekBar>("overlay-font-slider") != null } }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor { home.hasWindowFocus() }
        val settings = home
        waitFor { !root().isShown }
        waitFor { WindowInspector.getGlobalWindowViews().none { it.findViewWithTag<SeekBar>("overlay-font-slider") != null } }
        onUi { openCaptionStyles(settings) }
        capture("settings-uncovered")
        onUi { settings.finish(); home.finish() }
        context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor { root().isShown }
        check(root() === existing) { "Own-page visibility replaced subtitle views" }
    }

    private fun providerMarkChecks(home: MainActivity) {
        val provider = app.yuxino.mimi.android.provider.ServiceProvider.DASHSCOPE
        val saved = SettingsStore.configuration(context, provider)
        val translation = SettingsStore.translationConfiguration(context)
        val deepL = SettingsStore.translationConfiguration(context, app.yuxino.mimi.android.provider.TextTranslationProvider.DEEPL).let {
            if (it.apiKey == "synthetic-ui-fixture-never-sent") it.copy(apiKey = "") else it
        }
        val model = SettingsStore.qwenMtModel(context)
        val active = SettingsStore.provider(context)
        val synthetic = app.yuxino.mimi.android.provider.ServiceConfiguration(provider,
            mapOf("apiKey" to "synthetic-ui-fixture-never-sent"), saved.endpoint, saved.model)
        try {
            for (independent in listOf(false, true)) {
                val translator = if (independent) app.yuxino.mimi.android.provider.TranslationConfiguration(
                    provider = app.yuxino.mimi.android.provider.TextTranslationProvider.DEEPL,
                    apiKey = "synthetic-ui-fixture-never-sent",
                ) else app.yuxino.mimi.android.provider.TranslationConfiguration(
                    provider = app.yuxino.mimi.android.provider.TextTranslationProvider.BUILTIN,
                )
                check(SettingsStore.saveConfiguration(context, synthetic, translator, true, model))
                check(SettingsStore.activateProvider(context, provider))
                context.stopService(Intent(context, MimiService::class.java))
                waitFor { WindowInspector.getGlobalWindowViews().none { it.tag == "mimi-overlay" } }
                context.startService(Intent(context, MimiService::class.java).setAction(MimiService.ACTION_UI_PREVIEW)
                    .putExtra("preview_over_own_ui", true))
                waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "mimi-overlay" } }
                val page = test.startActivitySync(Intent(context, ServiceSettingsActivity::class.java)
                    .putExtra("provider", provider.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ServiceSettingsActivity
                onUi { page.finish() }
                waitFor { home.hasWindowFocus() }
                onUi { SubtitleBus.onSourceDraft("Synthetic provider badge check.", "en"); SubtitleBus.onTranslationDraft("合成译文。") }
                waitFor { home.findViewById<View>(R.id.translation_provider_icon).isShown == independent }
                onUi { root().findViewWithTag<View>("compact-subtitle").performClick() }
                waitFor { root().findViewWithTag<View>("expanded-subtitles").isShown }
                onUi {
                    val marks = root().findViewWithTag<android.widget.LinearLayout>("overlay-providers")
                    check(marks.childCount == if (independent) 3 else 2)
                }
                capture(if (independent) "two-provider-marks" else "one-provider-mark")
            }
        } finally {
            check(SettingsStore.saveConfiguration(context, saved, deepL, false, model))
            check(SettingsStore.saveConfiguration(context, saved, translation, true, model))
            SettingsStore.setProvider(context, active)
            check(SettingsStore.flushPendingWritesForTests(context))
        }
    }

    fun run(arguments: Bundle?) {
        val reviewSeconds = arguments?.getString("review_hold_seconds")?.toLongOrNull()?.takeIf { it in 1..180 }
        val baseline = arguments?.getString("baseline") == "true"
        val prefix = if (baseline) "before" else "after"
        val source = SettingsStore.sourceLang(context)
        val target = SettingsStore.targetLang(context)
        val historyLines = SettingsStore.historyLines(context)
        val position = SettingsStore.overlayYOffset(context)
        val font = SettingsStore.fontSize(context)
        val displayMode = SettingsStore.displayMode(context)
        val width = SettingsStore.overlayWidthPercent(context)
        val expandedY = SettingsStore.expandedYOffset(context)
        val compactX = SettingsStore.overlayXOffset(context, false)
        val expandedX = SettingsStore.overlayXOffset(context, true)
        val immersive = SettingsStore.immersiveSubtitles(context)
        val color = SettingsStore.translationColorIndex(context)
        val background = SettingsStore.overlayBgAlpha(context)
        val opacity = SettingsStore.overlayOpacity(context)
        val prefs = context.getSharedPreferences("first_run", 0)
        val hadSeen = prefs.contains("seen")
        val seen = prefs.getBoolean("seen", false)
        val hadImmersiveSeen = prefs.contains("immersive_seen")
        val immersiveSeen = prefs.getBoolean("immersive_seen", false)
        var home: MainActivity? = null
        var settings: MainActivity? = null
        var failure: Throwable? = null
        try {
            check(!MimiService.isRunning && !SettingsStore.isConfigured(context)) { "Use a blank idle emulator" }
            SettingsStore.setDisplayMode(context, CaptionDisplayMode.BILINGUAL)
            SettingsStore.setOverlayWidthPercent(context, 92)
            SettingsStore.setFontSize(context, 16)
            SettingsStore.setImmersiveSubtitles(context, false)
            SettingsStore.setTranslationColorIndex(context, 1)
            SettingsStore.setOverlayBgAlpha(context, 65)
            SettingsStore.setOverlayOpacity(context, 100)
            check(prefs.edit().putBoolean("seen", true).commit())
            home = test.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            context.startService(Intent(context, MimiService::class.java).setAction(MimiService.ACTION_UI_PREVIEW).putExtra("preview_over_own_ui", true))
            waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "mimi-overlay" } }
            onUi { SubtitleBus.clear() }
            waitFor { root().isShown == baseline }
            capture("$prefix-empty")
            onUi {
                SubtitleBus.setHistoryLimit(2)
                SubtitleBus.onFinalPair("もう少し歩いてみましょう。", "再往前走一会儿吧。", "ja")
                SubtitleBus.onSourceDraft("もう少し歩いてみましょう。", "ja")
                SubtitleBus.onTranslationDraft("再往前走一会儿吧。")
            }
            waitFor { root().isShown }
            if (reviewSeconds != null) {
                if (arguments?.getString("review_immersive") == "true") {
                    onUi {
                        check(prefs.edit().putBoolean("immersive_seen", true).commit())
                        SettingsStore.setImmersiveSubtitles(context, true)
                    }
                    waitFor { WindowInspector.getGlobalWindowViews().any { it.tag == "exit-immersive" } }
                    context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                test.sendStatus(0, Bundle().apply { putString("stream", "READY_FOR_MANUAL_OVERLAY_REVIEW: synthetic subtitles; no capture, credentials or provider.\n") })
                SystemClock.sleep(reviewSeconds * 1000)
            } else {
                capture("$prefix-compact")
                onUi { root().findViewWithTag<View>("compact-subtitle").performClick() }
                waitFor { root().findViewWithTag<View>("expanded-subtitles").isShown }
                val history = SubtitleBus.historySnapshot()
                check(history.isNotEmpty())
                capture("$prefix-expanded")
                onUi { root().findViewWithTag<View>("overlay-font").performClick() }
                if (baseline) {
                    check(SettingsStore.fontSize(context) == 18)
                } else {
                    waitFor { WindowInspector.getGlobalWindowViews().any { it.findViewWithTag<SeekBar>("overlay-font-slider") != null } }
                    capture("$prefix-font-control")
                    onUi { root().findViewWithTag<View>("overlay-font").performClick() }
                    check(WindowInspector.getGlobalWindowViews().count { it.findViewWithTag<SeekBar>("overlay-font-slider") != null } == 1)
                    val slider = WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewWithTag<SeekBar>("overlay-font-slider") }
                    drag(slider, 18)
                    waitFor { SettingsStore.fontSize(context) == 18 && abs(caption().textSize - captionPixels(21)) < 1 }
                    val dialog = WindowInspector.getGlobalWindowViews().first { it.findViewWithTag<SeekBar>("overlay-font-slider") != null }
                    val preview = dialog.findViewById<View>(R.id.subtitle_preview)
                    val previewPosition = IntArray(2)
                    val palette = dialog.findViewById<android.view.ViewGroup>(R.id.translation_color)
                    onUi {
                        preview.getLocationOnScreen(previewPosition)
                        palette.requestRectangleOnScreen(android.graphics.Rect(0, 0, palette.width, palette.height), true)
                    }
                    capture("$prefix-style-preview")
                    onUi {
                        check(preview.getGlobalVisibleRect(android.graphics.Rect())) { "Floating style controls hid their preview" }
                        fun captionRows(view: View): List<TextView> =
                            (if (view is app.yuxino.mimi.android.capture.LatestCaptionTextView) listOf(view) else emptyList()) +
                                if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { captionRows(view.getChildAt(it)) } else emptyList()
                        val rows = captionRows(preview).filter { it.isShown }
                        val rowBounds = rows.map {
                            val visible = android.graphics.Rect()
                            val location = IntArray(2).also(it::getLocationOnScreen)
                            val firstLine = (it.lineCount - it.maxLines).coerceAtLeast(0)
                            val top = location[1] + it.totalPaddingTop + it.layout.getLineTop(firstLine) - it.scrollY
                            val bottom = location[1] + it.totalPaddingTop + it.layout.getLineBottom(it.lineCount - 1) - it.scrollY
                            val shown = it.getGlobalVisibleRect(visible)
                            val windowOrigin = IntArray(2).also(it.rootView::getLocationOnScreen)
                            visible.offset(windowOrigin[0], windowOrigin[1])
                            Triple(shown && visible.top <= top && visible.bottom >= bottom, visible.toShortString(), "$top..$bottom; lines=${it.lineCount}; scroll=${it.scrollY}; padding=${it.totalPaddingTop}")
                        }
                        check(rows.size == 2 && rowBounds.all { it.first }) { "Floating preview clipped its caption rows: $rowBounds" }
                        check(IntArray(2).also(preview::getLocationOnScreen).contentEquals(previewPosition)) { "Floating style controls moved their preview" }
                        val touchSize = (48 * palette.resources.displayMetrics.density).toInt()
                        for (index in 0 until palette.childCount) {
                            val choice = palette.getChildAt(index)
                            check(choice.width >= touchSize && choice.height >= touchSize && choice.contentDescription.isNotBlank())
                        }
                    }
                    tapVisible(palette.findViewWithTag("caption-color-3"))
                    waitFor { SettingsStore.translationColorIndex(context) == 3 && caption().currentTextColor == SettingsStore.translationColor(context) }
                    capture("$prefix-style-colors")
                    tapVisible(palette.findViewWithTag("caption-color-1"))
                    waitFor { caption().currentTextColor == android.graphics.Color.WHITE }
                    onUi { check(root().findViewWithTag<View>("overlay-font").contentDescription == context.getString(R.string.caption_style_controls)) }
                    onUi { WindowInspector.getGlobalWindowViews().first { it.findViewWithTag<SeekBar>("overlay-font-slider") != null }.findViewById<View>(android.R.id.button1).performClick() }
                }
                settings = checkNotNull(home)
                onUi { openCaptionStyles(checkNotNull(settings)) }
                val seek = settings.findViewById<SeekBar>(R.id.font_size)
                drag(seek, 22)
                waitFor { SettingsStore.fontSize(context) == 22 }
                if (!baseline) waitFor { abs(caption().textSize - captionPixels(25)) < 1 }
                onUi {
                    check(abs(caption().textSize - captionPixels(if (baseline) 21 else 25)) < 1) { "Unexpected native caption size" }
                    check(SubtitleBus.historySnapshot() == history)
                }
                capture("$prefix-settings-size")
                onUi { settings.findViewById<View>(R.id.tab_caption).performClick() }
                if (!baseline) {
                    liveDisplayControls()
                    immersivePlacementChecks(checkNotNull(home))
                    providerMarkChecks(checkNotNull(home))
                    ownSurfaceChecks(checkNotNull(home))
                    // Confirmed pairs intentionally remain readable. Test the genuinely empty state.
                    onUi {
                        root().findViewWithTag<View>("collapse-overlay").performClick()
                        SubtitleBus.clear(); SubtitleBus.hideLive()
                    }
                    waitFor { !root().isShown }
                }
            }
            check(!MimiService.isRunning)
        } catch (error: Throwable) { failure = error }
        finally {
            context.stopService(Intent(context, MimiService::class.java))
            onUi { settings?.finish(); home?.finish() }
            SettingsStore.setSourceLang(context, source)
            SettingsStore.setTargetLang(context, target)
            SettingsStore.setHistoryLines(context, historyLines)
            SettingsStore.setOverlayPosition(context, false, compactX, position)
            SettingsStore.setOverlayPosition(context, true, expandedX, expandedY)
            SettingsStore.setDisplayMode(context, displayMode)
            SettingsStore.setOverlayWidthPercent(context, width)
            SettingsStore.setFontSize(context, font)
            SettingsStore.setImmersiveSubtitles(context, immersive)
            SettingsStore.setTranslationColorIndex(context, color)
            SettingsStore.setOverlayBgAlpha(context, background)
            SettingsStore.setOverlayOpacity(context, opacity)
            val restore = prefs.edit()
            if (hadSeen) restore.putBoolean("seen", seen) else restore.remove("seen")
            if (hadImmersiveSeen) restore.putBoolean("immersive_seen", immersiveSeen) else restore.remove("immersive_seen")
            check(restore.commit())
            check(SettingsStore.flushPendingWritesForTests(context))
        }
        test.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (failure == null && reviewSeconds != null) "Manual overlay review fixture closed; preferences restored; no capture or provider.\n"
                else if (failure == null) "Overlay $prefix checks passed: empty window, font action, live settings, history and silence; synthetic captions, no capture or provider.\n"
            else "Overlay $prefix failed: ${failure.javaClass.simpleName}: ${failure.message}; ${failure.stackTrace.filter { it.fileName == "OverlayInteractionChecks.kt" }.take(3)}\n")
        })
    }
}
