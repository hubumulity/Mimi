package app.yuxino.mimi.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import app.yuxino.mimi.android.capture.MimiService

class SettingsActivity : AppCompatActivity() {
    private val immersiveHelp = ImmersiveModeHelp(this)
    private lateinit var appearance: AppearanceControls
    private var lastSessionState: Pair<Boolean, Boolean>? = null
    private val sessionListener: () -> Unit = { runOnUiThread {
        if (!isFinishing && lastSessionState != (MimiService.isRunning to MimiService.isStopping)) refreshServices()
    } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applySystemBarInsets()
        InterfaceLanguage.bind(this, findViewById(R.id.interface_language))
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        appearance = AppearanceControls(this) { enabled ->
            if (enabled) immersiveHelp.requestEnable(onConfirmed = {
                try { SettingsStore.setImmersiveSubtitles(this, true) }
                catch (_: RuntimeException) { saveFailed() }
                appearance.sync()
            }, onCancelled = { appearance.sync() })
            else {
                immersiveHelp.dismiss()
                SettingsStore.setImmersiveSubtitles(this, false)
            }
        }
        findViewById<LinearLayout>(R.id.appearance_panel).addView(appearance)
        val tabs = findViewById<RadioGroup>(R.id.settings_tabs)
        val stacked = resources.configuration.fontScale >= 1.5f
        tabs.orientation = if (stacked) RadioGroup.VERTICAL else RadioGroup.HORIZONTAL
        for (index in 0 until tabs.childCount) tabs.getChildAt(index).layoutParams = RadioGroup.LayoutParams(
            if (stacked) -1 else 0, -2, if (stacked) 0f else 1f,
        )
        fun showTab() {
            for ((tab, panel) in listOf(R.id.tab_service to R.id.service_panel,
                R.id.tab_appearance to R.id.appearance_panel, R.id.tab_general to R.id.general_panel)) {
                findViewById<View>(panel).visibility = if (tabs.checkedRadioButtonId == tab) View.VISIBLE else View.GONE
            }
            findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 0)
        }
        tabs.setOnCheckedChangeListener { _, _ -> showTab() }
        val initial = when (intent.getStringExtra("settings_section")) {
            "appearance" -> R.id.tab_appearance
            "general" -> R.id.tab_general
            else -> R.id.tab_service
        }
        tabs.check(savedInstanceState?.getInt("settings_tab", initial) ?: initial)
        showTab()
        if (savedInstanceState?.getBoolean("settings_more") == true) findViewById<View>(R.id.appearance_more_toggle).performClick()
        findViewById<View>(R.id.copy_capture_diagnostics).setOnClickListener { copyDiagnostics() }
    }

    override fun onStart() { super.onStart(); MimiService.addStateListener(sessionListener); refreshServices() }
    override fun onResume() { super.onResume(); refreshServices(); appearance.sync() }
    override fun onStop() { MimiService.removeStateListener(sessionListener); super.onStop() }
    override fun onDestroy() { immersiveHelp.dismiss(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("settings_tab", findViewById<RadioGroup>(R.id.settings_tabs).checkedRadioButtonId)
        outState.putBoolean("settings_more", findViewById<View>(R.id.appearance_more_panel).visibility == View.VISIBLE)
        super.onSaveInstanceState(outState)
    }
    private fun refreshServices() {
        lastSessionState = MimiService.isRunning to MimiService.isStopping
        ServiceSettingsUi.renderList(this, findViewById(R.id.service_panel))
    }
    private fun saveFailed() = Toast.makeText(this, R.string.service_save_failed, Toast.LENGTH_LONG).show()
    private fun copyDiagnostics() {
        val observation = MimiService.captureObservation
        val report = "mimi Android capture diagnostics v1\n" +
            "androidApi=${android.os.Build.VERSION.SDK_INT}\n" +
            "source=android_playback_capture\nusage=media,game,unknown\nmicrophone=false\n" +
            "running=${MimiService.isRunning}\nobservation=${observation?.state?.name ?: "STOPPED"}\n" +
            "pcmAgeMs=${observation?.pcmAgeMs ?: "unknown"}\nsoundAgeMs=${observation?.soundAgeMs ?: "unknown"}\n" +
            "captureError=${MimiService.lastCaptureError ?: "none"}"
        val copied = try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("mimi capture diagnostics", report)); true
        } catch (_: RuntimeException) { false }
        Toast.makeText(this, if (copied) R.string.capture_diagnostics_copied else R.string.capture_diagnostics_copy_failed,
            if (copied) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }
}
