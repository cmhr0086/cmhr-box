package com.cmhr.box.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.tgui.recyclerview.LinearLayoutManager
import org.telegram.tgui.recyclerview.RecyclerView
import org.telegram.ui.ActionBar.DrawerLayoutContainer
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.DividerCell
import org.telegram.ui.Cells.DrawerProfileCell
import org.telegram.ui.Cells.EmptyCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

/**
 * Telegram drawer row metrics (DrawerActionCell) with an unread dot. DrawerActionCell itself only
 * offers a red "!" error badge and keeps its colors private, so it cannot follow theme switches.
 */
private class DrawerItemCell(context: Context) : FrameLayout(context) {
    val icon = ImageView(context)
    val text = TextView(context)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var dot = false

    init {
        setWillNotDraw(false)
        icon.scaleType = ImageView.ScaleType.CENTER
        addView(icon, LayoutHelper.createFrame(24, 24f, Gravity.START or Gravity.TOP, 19f, 12f, 0f, 0f))
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        text.typeface = AndroidUtilities.bold()
        text.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        addView(text, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.START or Gravity.TOP, 72f, 0f, 40f, 0f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48f), MeasureSpec.EXACTLY))
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!dot) return
        dotPaint.color = Theme.getColor(Theme.key_chats_unreadCounter)
        canvas.drawCircle(width - AndroidUtilities.dp(28f).toFloat(), height / 2f, AndroidUtilities.dp(4.5f).toFloat(), dotPaint)
    }
}

/** Side menu: tgui's DrawerProfileCell header (with its day/night button) over our rows. */
class CmhrDrawer(context: Context) : FrameLayout(context) {

    data class Item(val id: Int, val icon: Int, val text: CharSequence, val dot: Boolean = false)

    private val list = RecyclerListView(context)
    private val adapter = Adapter(context)
    private var items: List<Item?> = emptyList()
    private var title: String = ""
    private var subtitle: CharSequence = ""
    private var avatar: Drawable? = null
    var onItemClick: (Int) -> Unit = {}

    init {
        list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        list.setAllowItemsInteractionDuringAnimation(false)
        list.adapter = adapter
        list.setOnItemClickListener(RecyclerListView.OnItemClickListener { _, position ->
            items.getOrNull(position - 2)?.let { onItemClick(it.id) }
        })
        addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        applyColors()
    }

    /** Same width rule as Telegram's side menu. */
    fun attachTo(container: DrawerLayoutContainer) {
        container.setDrawerLayout(this, list)
        val params = layoutParams as LayoutParams
        val screen: Point = AndroidUtilities.getRealScreenSize()
        params.width = if (AndroidUtilities.isTablet()) AndroidUtilities.dp(320f)
        else minOf(AndroidUtilities.dp(320f), minOf(screen.x, screen.y) - AndroidUtilities.dp(56f))
        params.height = LayoutHelper.MATCH_PARENT
        layoutParams = params
    }

    /** [items] null entries are dividers. */
    fun setContent(title: String, subtitle: CharSequence, avatar: Drawable?, items: List<Item?>) {
        this.title = title
        this.subtitle = subtitle
        this.avatar = avatar
        this.items = items
        adapter.notifyDataSetChanged()
    }

    fun applyColors() {
        setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground))
        list.setBackgroundColor(Theme.getColor(Theme.key_chats_menuBackground))
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter(private val context: Context) : RecyclerListView.SelectionAdapter() {
        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean = holder.itemViewType == TYPE_ACTION

        override fun getItemCount(): Int = items.size + 2

        override fun getItemViewType(position: Int): Int = when {
            position == 0 -> TYPE_PROFILE
            position == 1 -> TYPE_SPACE
            items.getOrNull(position - 2) == null -> TYPE_DIVIDER
            else -> TYPE_ACTION
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = when (viewType) {
                TYPE_PROFILE -> DrawerProfileCell(context)
                TYPE_DIVIDER -> DividerCell(context)
                TYPE_ACTION -> DrawerItemCell(context)
                else -> EmptyCell(context, AndroidUtilities.dp(8f))
            }
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val view = holder.itemView) {
                is DrawerProfileCell -> {
                    view.setUser(1, title, "", subtitle, avatar, null)
                    view.setArrowVisible(false)
                    view.applyBackground(true)
                }
                is DrawerItemCell -> {
                    val item = items[position - 2] ?: return
                    view.text.text = item.text
                    view.text.setTextColor(Theme.getColor(Theme.key_chats_menuItemText))
                    view.icon.setImageResource(item.icon)
                    view.icon.colorFilter = shadeFilter(Theme.getColor(Theme.key_chats_menuItemIcon))
                    view.dot = item.dot
                    view.background = Theme.getSelectorDrawable(false)
                    view.invalidate()
                }
            }
        }
    }

    companion object {
        private const val TYPE_PROFILE = 0
        private const val TYPE_SPACE = 1
        private const val TYPE_DIVIDER = 2
        private const val TYPE_ACTION = 3
    }
}
