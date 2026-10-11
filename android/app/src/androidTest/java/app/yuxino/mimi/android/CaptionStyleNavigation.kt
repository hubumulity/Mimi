package app.yuxino.mimi.android

import android.view.View
import android.widget.RadioGroup

/** Exercise the same caption-preview shortcut that opens the inline style controls. */
internal fun openCaptionStyles(activity: MainActivity) {
    activity.findViewById<View>(R.id.tab_caption).performClick()
    check(activity.findViewById<RadioGroup>(R.id.settings_tabs).checkedRadioButtonId == R.id.tab_caption)
    check(activity.findViewById<View>(R.id.subtitle_preview).performClick())
}
