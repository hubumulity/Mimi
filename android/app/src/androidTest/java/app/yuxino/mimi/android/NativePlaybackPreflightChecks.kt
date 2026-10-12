package app.yuxino.mimi.android

import android.app.Activity
import android.app.Instrumentation
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import app.yuxino.mimi.android.capture.MimiService

/** Offline source preparation: pause and seek the existing public Firefox video; never Play. */
internal class NativePlaybackPreflightChecks(private val test: Instrumentation) {
    fun run() {
        var permissionAdopted = false
        var failure: String? = null
        var position: Long? = null
        var state: Int? = null
        try {
            check(Build.VERSION.SDK_INT >= 29) { "preflight_api_unavailable" }
            check(!MimiService.isRunning && !MimiService.isStopping) { "preflight_capture_active" }
            test.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
            permissionAdopted = true
            val manager = test.targetContext.getSystemService(MediaSessionManager::class.java)
            val controllers = manager.getActiveSessions(null).filter { it.packageName == "org.mozilla.firefox" }
            check(controllers.size == 1) { "preflight_firefox_session_missing_or_ambiguous" }
            val controller = controllers.single()
            check(controller.playbackState?.actions?.and(PlaybackState.ACTION_SEEK_TO) == PlaybackState.ACTION_SEEK_TO) {
                "preflight_seek_unavailable"
            }
            controller.transportControls.pause()
            controller.transportControls.seekTo(180000)
            controller.transportControls.pause()
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (SystemClock.elapsedRealtime() < deadline) {
                val playback = controller.playbackState
                state = playback?.state
                position = playback?.position
                if (state == PlaybackState.STATE_PAUSED && position?.let { it in 179000L..181000L } == true) break
                SystemClock.sleep(100)
            }
            check(state == PlaybackState.STATE_PAUSED && position?.let { it in 179000L..181000L } == true) {
                "preflight_paused_position_not_verified"
            }
            test.sendStatus(0, Bundle().apply {
                putString("stream", "NATIVE_PLAYBACK_PREFLIGHT_READY package=org.mozilla.firefox state=$state positionMs=$position unixMs=${System.currentTimeMillis()}; paused only, no capture/provider/playback started.\n")
            })
        } catch (error: Throwable) {
            failure = error.message?.takeIf { it.startsWith("preflight_") } ?: "preflight_${error.javaClass.simpleName}"
        } finally {
            if (permissionAdopted) test.uiAutomation.dropShellPermissionIdentity()
        }
        test.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (failure == null) "Native playback preflight passed: Firefox paused at $position ms; no capture/provider/playback started.\n"
                else "Native playback preflight failed: $failure state=$state positionMs=$position; no Play requested.\n")
        })
    }
}
