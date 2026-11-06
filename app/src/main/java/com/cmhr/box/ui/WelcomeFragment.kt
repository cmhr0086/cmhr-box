package com.cmhr.box.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.cmhr.box.R
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.tgui.R as TR

/** First screen of the first run, like Telegram's intro: duck, greeting, one button. */
class WelcomeFragment : BaseFragment() {

    private var duck: RLottieImageView? = null
    private var headline: TextView? = null
    private var description: TextView? = null
    private var start: TextView? = null

    override fun createView(context: Context): View {
        actionBar.setAddToContainer(false)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(AndroidUtilities.dp(32f), AndroidUtilities.dp(24f), AndroidUtilities.dp(32f), AndroidUtilities.dp(24f))
        }
        duck = RLottieImageView(context).apply {
            setAnimation(TR.raw.utyan_newborn, 160, 160)
            setAutoRepeat(false)
            setOnClickListener { if (!isPlaying) { setProgress(0f); playAnimation() } }
        }
        column.addView(duck, LayoutHelper.createLinear(160, 160, 0f, 0f, 0f, 20f))
        headline = TextView(context).apply {
            text = context.getString(R.string.cmhr_welcome_title)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
        }
        column.addView(headline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        description = TextView(context).apply {
            text = context.getString(R.string.cmhr_welcome_description)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            gravity = Gravity.CENTER
            setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1f)
        }
        column.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f))

        val scroll = ScrollView(context).apply { isFillViewport = true }
        scroll.addView(column)
        start = TextView(context).apply {
            text = context.getString(R.string.cmhr_welcome_start)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
            setOnClickListener { presentFragment(InviteFragment.createForOnboarding(), true) }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, AndroidUtilities.statusBarHeight, 0, 0)
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(start, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 32f, 0f, 32f, 32f))
        fragmentView = root
        applyColors()
        return root
    }

    override fun onResume() {
        super.onResume()
        duck?.playAnimation()
    }

    override fun isLightStatusBar(): Boolean =
        ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite)) > 0.7

    private fun applyColors() {
        fragmentView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        headline?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        description?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6))
        start?.apply {
            setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            background = Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8f),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed),
            )
        }
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> {
        val recolor = object : ThemeDescription.ThemeDescriptionDelegate {
            override fun didSetColor() = applyColors()
        }
        return arrayListOf(
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_windowBackgroundWhite),
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_windowBackgroundWhiteBlackText),
        )
    }
}
