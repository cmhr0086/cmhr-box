package com.cmhr.box.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.tgui.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView

/** What the connect area shows; [accentKey] tints the button, [busy] spins the ring. */
data class HeroState(
    val title: CharSequence,
    val subtitle: CharSequence,
    val accentKey: Int,
    val busy: Boolean,
    val filled: Boolean,
    val enabled: Boolean = true,
)

/** Round connect button with the paper plane: filled when active, outlined when idle. */
class ConnectButton(context: Context) : View(context) {
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val plane = PlaneDrawable(context, 0, fill = 0.5f)
    var accent = 0
    var filled = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        plane.setBounds(0, 0, w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) / 2f
        circle.color = accent
        circle.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        circle.strokeWidth = AndroidUtilities.dp(2f).toFloat()
        canvas.drawCircle(width / 2f, height / 2f, radius - circle.strokeWidth, circle)
        plane.color = if (filled) Theme.getColor(Theme.key_featuredStickers_buttonText) else accent
        plane.draw(canvas)
    }
}

/** Static rounded progress bar (tgui's LineProgressView fades out at 100% and keeps redrawing). */
private class ProgressLine(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    var fraction = 0f
    var trackColor = 0
    var fillColor = 0

    override fun onDraw(canvas: Canvas) {
        val radius = height / 2f
        paint.color = trackColor
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (fraction <= 0f) return
        paint.color = fillColor
        // Keep a visible dot for tiny values instead of a sliver narrower than its own radius.
        rect.right = maxOf(width * fraction.coerceAtMost(1f), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
}

/** Title and value on one line, a thin progress line under them. */
@SuppressLint("ViewConstructor")
class UsageCell(context: Context) : FrameLayout(context) {
    private val title = TextView(context)
    private val value = TextView(context)
    private val line = ProgressLine(context)
    private var divider = false

    init {
        setWillNotDraw(false)
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        title.isSingleLine = true
        addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 21f, 14f, 21f, 0f))
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        value.isSingleLine = true
        addView(value, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.TOP, 21f, 15f, 21f, 0f))
        addView(line, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 4f, Gravity.START or Gravity.TOP, 21f, 46f, 21f, 0f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (line.visibility == VISIBLE) 66f else 50f
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(height), MeasureSpec.EXACTLY))
    }

    /** [fraction] null removes the line and the row shrinks to a plain value row. */
    fun bind(titleText: CharSequence, valueText: CharSequence, fraction: Float?, warn: Boolean, divider: Boolean) {
        this.divider = divider
        title.text = titleText
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        value.text = valueText
        value.setTextColor(Theme.getColor(if (warn) Theme.key_text_RedBold else Theme.key_windowBackgroundWhiteValueText))
        line.visibility = if (fraction == null) GONE else VISIBLE
        requestLayout()
        line.trackColor = Theme.getColor(Theme.key_player_progressBackground)
        line.fillColor = Theme.getColor(if (warn) Theme.key_text_RedBold else Theme.key_featuredStickers_addButton)
        line.fraction = fraction ?: 0f
        line.invalidate()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (divider) {
            canvas.drawLine(AndroidUtilities.dp(21f).toFloat(), height - 1f, width.toFloat(), height - 1f, Theme.dividerPaint)
        }
    }
}

/** Node row: name, protocol below, latency on the right, check mark when selected. */
@SuppressLint("ViewConstructor")
class NodeCell(context: Context) : FrameLayout(context) {
    private val name = TextView(context)
    private val detail = TextView(context)
    private val delay = TextView(context)
    private val check = ImageView(context)
    private var divider = false

    init {
        setWillNotDraw(false)
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        name.isSingleLine = true
        name.ellipsize = TextUtils.TruncateAt.END
        addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 21f, 10f, 120f, 0f))
        detail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        detail.isSingleLine = true
        addView(detail, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 21f, 35f, 120f, 0f))
        delay.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        delay.isSingleLine = true
        addView(delay, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 56f, 0f))
        check.setImageResource(R.drawable.msg_check_s)
        check.scaleType = ImageView.ScaleType.CENTER
        addView(check, LayoutHelper.createFrame(24, 24f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 21f, 0f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62f), MeasureSpec.EXACTLY))
    }

    fun bind(nameText: CharSequence, detailText: CharSequence, delayText: CharSequence, delayKey: Int, selected: Boolean, divider: Boolean) {
        this.divider = divider
        name.text = nameText
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        detail.text = detailText
        detail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
        delay.text = delayText
        delay.setTextColor(Theme.getColor(delayKey))
        check.visibility = if (selected) VISIBLE else INVISIBLE
        check.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (divider) {
            canvas.drawLine(AndroidUtilities.dp(21f).toFloat(), height - 1f, width.toFloat(), height - 1f, Theme.dividerPaint)
        }
    }
}

/** Installed app row: icon, label, package name, check mark when selected. */
@SuppressLint("ViewConstructor")
class AppCell(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context)
    private val name = TextView(context)
    private val pkg = TextView(context)
    private val check = ImageView(context)
    private var divider = false

    init {
        setWillNotDraw(false)
        addView(icon, LayoutHelper.createFrame(36, 36f, Gravity.START or Gravity.CENTER_VERTICAL, 21f, 0f, 0f, 0f))
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        name.isSingleLine = true
        name.ellipsize = TextUtils.TruncateAt.END
        addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 72f, 9f, 56f, 0f))
        pkg.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        pkg.isSingleLine = true
        pkg.ellipsize = TextUtils.TruncateAt.MIDDLE
        addView(pkg, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 72f, 33f, 56f, 0f))
        check.setImageResource(R.drawable.msg_check_s)
        check.scaleType = ImageView.ScaleType.CENTER
        addView(check, LayoutHelper.createFrame(24, 24f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 21f, 0f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(58f), MeasureSpec.EXACTLY))
    }

    fun bind(label: CharSequence, packageName: CharSequence, iconDrawable: android.graphics.drawable.Drawable?, selected: Boolean, divider: Boolean) {
        this.divider = divider
        icon.setImageDrawable(iconDrawable)
        name.text = label
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        pkg.text = packageName
        pkg.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
        check.visibility = if (selected) VISIBLE else INVISIBLE
        check.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (divider) {
            canvas.drawLine(AndroidUtilities.dp(72f).toFloat(), height - 1f, width.toFloat(), height - 1f, Theme.dividerPaint)
        }
    }
}

/** Settings-style action row: icon, text, and on the right either a value or a spinner. */
@SuppressLint("ViewConstructor")
class ActionCell(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context)
    private val text = TextView(context)
    private val value = TextView(context)
    private val spinner = RadialProgressView(context)
    private var divider = false

    init {
        setWillNotDraw(false)
        icon.scaleType = ImageView.ScaleType.CENTER
        addView(icon, LayoutHelper.createFrame(24, 24f, Gravity.START or Gravity.CENTER_VERTICAL, 21f, 0f, 0f, 0f))
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        text.isSingleLine = true
        addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 72f, 0f, 0f, 0f))
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        value.isSingleLine = true
        addView(value, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 21f, 0f))
        spinner.setSize(AndroidUtilities.dp(20f))
        addView(spinner, LayoutHelper.createFrame(28, 28f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 17f, 0f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50f), MeasureSpec.EXACTLY))
    }

    fun bind(iconRes: Int, label: CharSequence, valueText: CharSequence?, loading: Boolean, textKey: Int, divider: Boolean) {
        this.divider = divider
        icon.setImageResource(iconRes)
        icon.setColorFilter(Theme.getColor(textKey))
        text.text = label
        text.setTextColor(Theme.getColor(textKey))
        value.text = valueText
        value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText))
        value.visibility = if (loading || valueText.isNullOrEmpty()) GONE else VISIBLE
        spinner.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton))
        spinner.visibility = if (loading) VISIBLE else GONE
        isEnabled = !loading
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (divider) {
            canvas.drawLine(AndroidUtilities.dp(72f).toFloat(), height - 1f, width.toFloat(), height - 1f, Theme.dividerPaint)
        }
    }
}

/** About header: the paper plane on an accent circle, app name and version. */
@SuppressLint("ViewConstructor")
class AboutHeaderCell(context: Context) : LinearLayout(context) {
    private val badge = ConnectButton(context)
    private val name = TextView(context)
    private val version = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, AndroidUtilities.dp(28f), 0, AndroidUtilities.dp(20f))
        badge.isClickable = false
        badge.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(badge, LayoutHelper.createLinear(88, 88))
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
        name.typeface = AndroidUtilities.bold()
        addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 14f, 0f, 0f))
        version.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        addView(version, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 2f, 0f, 0f))
    }

    fun bind(appName: CharSequence, versionText: CharSequence) {
        badge.accent = Theme.getColor(Theme.key_featuredStickers_addButton)
        badge.filled = true
        badge.invalidate()
        name.text = appName
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        version.text = versionText
        version.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2))
    }
}

/** Telegram's pinned-message bar (chat top panel): accent line, title, one line of text, close. */
class PinnedBar(context: Context) : FrameLayout(context) {
    private val line = View(context)
    private val title = TextView(context)
    private val message = TextView(context)
    val close = ImageView(context)

    init {
        addView(line, LayoutHelper.createFrame(2, 32f, Gravity.START or Gravity.CENTER_VERTICAL, 8f, 0f, 0f, 0f))
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        title.typeface = AndroidUtilities.bold()
        title.isSingleLine = true
        title.ellipsize = TextUtils.TruncateAt.END
        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 18f, 7f, 48f, 0f))
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        message.isSingleLine = true
        message.ellipsize = TextUtils.TruncateAt.END
        addView(message, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.TOP, 18f, 25.3f, 48f, 0f))
        close.setImageResource(R.drawable.msg_voiceclose)
        // Telegram's chat close glyph is smaller than a menu icon.
        close.scaleType = ImageView.ScaleType.FIT_CENTER
        close.setPadding(AndroidUtilities.dp(15f), AndroidUtilities.dp(15f), AndroidUtilities.dp(15f), AndroidUtilities.dp(15f))
        addView(close, LayoutHelper.createFrame(48, 48, Gravity.END or Gravity.TOP))
    }

    fun bind(titleText: CharSequence, messageText: CharSequence) {
        title.text = titleText
        message.text = messageText
        applyColors()
    }

    fun applyColors() {
        background = Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_chat_topPanelBackground), Theme.getColor(Theme.key_listSelector))
        line.background = Theme.createRoundRectDrawable(AndroidUtilities.dp(1f), Theme.getColor(Theme.key_chat_topPanelLine))
        title.setTextColor(Theme.getColor(Theme.key_chat_topPanelTitle))
        message.setTextColor(Theme.getColor(Theme.key_chat_topPanelMessage))
        close.setColorFilter(Theme.getColor(Theme.key_chat_topPanelClose))
        close.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP)
    }
}
