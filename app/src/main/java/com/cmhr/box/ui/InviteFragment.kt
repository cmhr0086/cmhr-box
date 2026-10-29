package com.cmhr.box.ui

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cmhr.box.AppGraph
import com.cmhr.box.R
import com.cmhr.box.core.CmhrException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.RadialProgressView
import org.telegram.tgui.R as TR

/** Activation in the style of Telegram's login steps: headline, one field, one button. */
class InviteFragment(args: Bundle?) : BaseFragment(args) {

    private var scope: CoroutineScope? = null
    private val replace get() = arguments?.getBoolean(ARG_REPLACE) == true

    private var duck: RLottieImageView? = null
    private var headline: TextView? = null
    private var description: TextView? = null
    private var field: EditTextBoldCursor? = null
    private var error: TextView? = null
    private var button: TextView? = null
    private var progress: RadialProgressView? = null
    private var submitting = false

    override fun onFragmentCreate(): Boolean {
        scope = MainScope()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        scope?.cancel()
        scope = null
        super.onFragmentDestroy()
    }

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(TR.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(context.getString(R.string.cmhr_invite_title))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) finishFragment()
            }
        })

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(32f), AndroidUtilities.dp(24f), AndroidUtilities.dp(32f), AndroidUtilities.dp(24f))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // Telegram's passcode duck, as on its own passcode setup screen.
        duck = RLottieImageView(context).apply {
            setAnimation(TR.raw.utyan_passcode, 120, 120)
            setAutoRepeat(false)
            setOnClickListener { if (!isPlaying) { setProgress(0f); playAnimation() } }
        }
        column.addView(duck, LayoutHelper.createLinear(120, 120, 0f, 0f, 0f, 16f))
        headline = TextView(context).apply {
            text = context.getString(if (replace) R.string.cmhr_change_invite else R.string.cmhr_invite_headline)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
        }
        column.addView(headline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        description = TextView(context).apply {
            text = context.getString(if (replace) R.string.cmhr_invite_replace_description else R.string.cmhr_invite_description)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            gravity = Gravity.CENTER
            setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1f)
        }
        column.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f))

        field = EditTextBoldCursor(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            hint = context.getString(R.string.cmhr_invite_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setCursorSize(AndroidUtilities.dp(20f))
            setCursorWidth(1.5f)
            gravity = Gravity.CENTER
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
            background = null
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) { submit(); true } else false
            }
        }
        column.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0f, 32f, 0f, 0f))

        error = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        column.addView(error, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f))

        val buttonFrame = FrameLayout(context)
        button = TextView(context).apply {
            text = context.getString(R.string.cmhr_invite_submit)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
            setOnClickListener { submit() }
        }
        buttonFrame.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f))
        progress = RadialProgressView(context).apply {
            setSize(AndroidUtilities.dp(22f))
            visibility = View.INVISIBLE
        }
        buttonFrame.addView(progress, LayoutHelper.createFrame(32, 32, Gravity.CENTER))
        column.addView(buttonFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0f, 28f, 0f, 0f))

        val scroll = ScrollView(context)
        scroll.addView(column)
        fragmentView = scroll
        applyColors()
        return scroll
    }

    override fun onResume() {
        super.onResume()
        duck?.playAnimation()
        field?.let { AndroidUtilities.showKeyboard(it.apply { requestFocus() }) }
    }

    private fun submit() {
        val code = field?.text?.toString()?.trim().orEmpty()
        if (code.isEmpty() || submitting) return
        submitting = true
        setBusy(true)
        error?.visibility = View.GONE
        scope?.launch {
            val context = parentActivity
            runCatching { AppGraph.cmhr.activate(code) }
                .onSuccess { status ->
                    context?.toast(context.getString(R.string.cmhr_invite_activated, status.nodeCount))
                    field?.let { AndroidUtilities.hideKeyboard(it) }
                    finishFragment()
                }
                .onFailure { e ->
                    submitting = false
                    setBusy(false)
                    if (e is CmhrException.AccessDenied) showAccessDialog(e.issue)
                    else showError(context?.describeError(e).orEmpty())
                }
        }
    }

    private fun showError(text: String) {
        error?.text = text
        error?.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        field?.let { AndroidUtilities.shakeViewSpring(it, 3.5f) }
    }

    private fun setBusy(busy: Boolean) {
        button?.text = if (busy) "" else parentActivity?.getString(R.string.cmhr_invite_submit)
        progress?.visibility = if (busy) View.VISIBLE else View.INVISIBLE
        field?.isEnabled = !busy
    }

    private fun applyColors() {
        fragmentView?.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        headline?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        description?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6))
        error?.setTextColor(Theme.getColor(Theme.key_text_RedRegular))
        field?.apply {
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
            setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular),
            )
        }
        button?.apply {
            setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            background = Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8f),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed),
            )
        }
        progress?.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> {
        val recolor = object : ThemeDescription.ThemeDescriptionDelegate {
            override fun didSetColor() = applyColors()
        }
        return arrayListOf(
            ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_windowBackgroundWhite),
            ThemeDescription(null, 0, null, null, null, recolor, Theme.key_windowBackgroundWhiteBlackText),
        )
    }

    companion object {
        private const val ARG_REPLACE = "replace"

        fun create(replace: Boolean) = InviteFragment(Bundle().apply { putBoolean(ARG_REPLACE, replace) })
    }
}
