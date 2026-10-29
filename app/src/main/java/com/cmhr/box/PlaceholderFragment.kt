package com.cmhr.box

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.LayoutHelper

/** Stage 1 placeholder; replaced by the real home screen in stage 4. */
class PlaceholderFragment : BaseFragment() {
    private var textView: TextView? = null

    override fun createView(context: Context): View {
        actionBar.setTitle(context.getString(R.string.cmhr_app_name))
        val frame = FrameLayout(context)
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        val text = TextView(context).apply {
            text = context.getString(R.string.cmhr_app_name)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            gravity = Gravity.CENTER
        }
        frame.addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        textView = text
        fragmentView = frame
        return frame
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> = arrayListOf(
        ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
        ThemeDescription(textView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
        ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
        ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
    )
}
