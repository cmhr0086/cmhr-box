package com.cmhr.box.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.cmhr.box.AppGraph
import com.cmhr.box.R
import com.cmhr.box.core.Announcement
import com.cmhr.box.core.NodeDisplay
import com.cmhr.box.engine.api.Node
import com.cmhr.box.engine.api.RoutingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView

private fun sheetTitle(context: Context, text: CharSequence) = TextView(context).apply {
    this.text = text
    typeface = AndroidUtilities.bold()
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
    setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
    isSingleLine = true
}

private fun sheetButton(context: Context, text: CharSequence, onClick: () -> Unit) = TextView(context).apply {
    this.text = text
    gravity = Gravity.CENTER
    typeface = AndroidUtilities.bold()
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
    setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
    background = Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8f)
    setOnClickListener { onClick() }
}

/** Announcement in a custom-view sheet; "Got it" marks it read. */
fun BaseFragment.showAnnouncementSheet(item: Announcement, onRead: () -> Unit) {
    val context = parentActivity ?: return
    val sheet = BottomSheet(context, false)
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), AndroidUtilities.dp(20f), AndroidUtilities.dp(16f))
    }
    layout.addView(sheetTitle(context, item.title.ifBlank { context.getString(R.string.cmhr_drawer_announcement) }))
    if (item.updatedAt.isNotBlank()) {
        layout.addView(TextView(context).apply {
            text = item.updatedAt
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
        }, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 2f, 0f, 0f))
    }
    val body = TextView(context).apply {
        text = item.content.trim()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        setLineSpacing(AndroidUtilities.dp(3f).toFloat(), 1f)
        setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        setTextIsSelectable(true)
    }
    // Long announcements scroll inside the sheet instead of pushing the button off screen.
    val scroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val max = (AndroidUtilities.displaySize.y * 0.55f).toInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
        }
    }
    scroll.addView(body)
    layout.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 14f, 0f, 18f))
    layout.addView(sheetButton(context, context.getString(R.string.cmhr_got_it)) {
        onRead()
        sheet.dismiss()
    }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))
    sheet.setCustomView(layout)
    showDialog(sheet)
}

/** Node picker sheet: tap to select, "Test" measures every node in place. */
fun BaseFragment.showNodesSheet(scope: CoroutineScope, onChanged: () -> Unit) {
    val context = parentActivity ?: return
    val engine = AppGraph.engine
    val sheet = BottomSheet(context, false)
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    val header = FrameLayout(context)
    header.addView(sheetTitle(context, context.getString(R.string.cmhr_nodes_title)),
        LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 20f, 0f, 0f, 0f))
    val test = TextView(context).apply {
        text = context.getString(R.string.cmhr_test_delay)
        typeface = AndroidUtilities.bold()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton))
        gravity = Gravity.CENTER
        setPadding(AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(12f), 0)
        background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP)
    }
    header.addView(test, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f))
    val spinner = RadialProgressView(context).apply {
        setSize(AndroidUtilities.dp(20f))
        setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton))
        visibility = View.GONE
    }
    header.addView(spinner, LayoutHelper.createFrame(36, 36f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 20f, 0f))
    root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56))

    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val scroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val max = (AndroidUtilities.displaySize.y * 0.6f).toInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
        }
    }
    scroll.addView(list)
    root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0f, 0f, 8f))

    fun render(nodes: List<Node>, selected: String?) {
        list.removeAllViews()
        if (nodes.isEmpty()) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.cmhr_nodes_empty)
                setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
                gravity = Gravity.CENTER
            }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 80))
            return
        }
        val names = nodes.map { it.name }
        nodes.forEachIndexed { index, node ->
            val cell = NodeCell(context)
            cell.bind(
                NodeDisplay.name(node.name, node.name, names),
                node.protocol.uppercase(),
                context.delayText(node.delayMs),
                delayColorKey(node.delayMs),
                selected = node.id == selected,
                divider = index < nodes.lastIndex,
            )
            cell.background = Theme.getSelectorDrawable(false)
            cell.setOnClickListener {
                scope.launch {
                    engine.select(node.id)
                    onChanged()
                    sheet.dismiss()
                }
            }
            list.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 62))
        }
    }

    suspend fun reload() = render(engine.nodes(), engine.selectedNodeId())

    test.setOnClickListener {
        test.visibility = View.GONE
        spinner.visibility = View.VISIBLE
        scope.launch {
            engine.testDelay(engine.nodes().map { it.id })
            reload()
            spinner.visibility = View.GONE
            test.visibility = View.VISIBLE
            onChanged()
        }
    }

    scope.launch { reload() }
    sheet.setCustomView(root)
    showDialog(sheet)
}

/** Routing mode picker: one row per mode with its description, the current one checked. */
fun BaseFragment.showRoutingSheet(current: RoutingMode, onPick: (RoutingMode) -> Unit) {
    val context = parentActivity ?: return
    val sheet = BottomSheet(context, false)
    val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    root.addView(sheetTitle(context, context.getString(R.string.cmhr_routing)),
        LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 20f, 16f, 20f, 8f))
    RoutingMode.entries.forEachIndexed { index, mode ->
        val cell = NodeCell(context)
        cell.bind(
            context.getString(mode.title()),
            context.getString(mode.description()),
            "", Theme.key_windowBackgroundWhiteGrayText2,
            selected = mode == current,
            divider = index < RoutingMode.entries.lastIndex,
        )
        cell.background = Theme.getSelectorDrawable(false)
        cell.setOnClickListener {
            if (mode != current) onPick(mode)
            sheet.dismiss()
        }
        root.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 62))
    }
    root.addView(TextView(context).apply {
        text = context.getString(R.string.cmhr_routing_hint)
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setTextColor(Theme.getColor(Theme.key_dialogTextGray3))
    }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20f, 8f, 20f, 16f))
    sheet.setCustomView(root)
    showDialog(sheet)
}
