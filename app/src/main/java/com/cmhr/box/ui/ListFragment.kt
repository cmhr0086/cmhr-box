package com.cmhr.box.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.telegram.messenger.NotificationCenter
import org.telegram.tgui.R
import org.telegram.tgui.recyclerview.LinearLayoutManager
import org.telegram.tgui.recyclerview.RecyclerView
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeDescription
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextDetailSettingsCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

/** One list entry; subclasses rebuild the whole list from their state in [fillRows]. */
sealed class Row {
    open val id: Int get() = 0

    data class Header(val text: CharSequence) : Row()
    data object Shadow : Row()
    data class Info(val text: CharSequence) : Row()
    data class Text(
        override val id: Int,
        val text: CharSequence,
        val value: CharSequence? = null,
        val icon: Int = 0,
        val textKey: Int = Theme.key_windowBackgroundWhiteBlackText,
        val clickable: Boolean = true,
    ) : Row()

    data class Check(override val id: Int, val text: CharSequence, val checked: Boolean, val icon: Int = 0) : Row()
    data class Detail(val title: CharSequence, val value: CharSequence) : Row()

    /** Rows drawn by a subclass cell; [type] must be >= [ListFragment.CUSTOM_TYPE]. */
    data class Custom(val type: Int, override val id: Int, val payload: Any?) : Row()
}

/**
 * tgui settings-style screen: an ActionBar over a RecyclerListView of upstream cells.
 * Data is loaded in onFragmentCreate (createView may run more than once); views only render
 * [rows]. Colors follow day/night switches through [getThemeDescriptions].
 */
abstract class ListFragment : BaseFragment(), NotificationCenter.NotificationCenterDelegate {

    protected var scope: CoroutineScope? = null
    protected var listView: RecyclerListView? = null
    private var adapter: Adapter? = null
    protected val rows = ArrayList<Row>()

    protected abstract fun title(context: Context): CharSequence
    protected abstract fun fillRows(rows: MutableList<Row>)
    protected open fun onRowClick(row: Row, view: View) {}
    protected open val showBackButton: Boolean = true
    protected open fun createMenu(actionBar: ActionBar) {}
    protected open fun onMenuClick(id: Int) {}
    protected open fun onBackButtonClick() = finishFragment()

    protected open fun createCustomCell(context: Context, type: Int): View =
        error("No cell for type $type")

    protected open fun bindCustomCell(view: View, row: Row.Custom, divider: Boolean) {}

    override fun onFragmentCreate(): Boolean {
        scope = MainScope()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme)
        scope?.cancel()
        scope = null
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        // Check states and cell backgrounds are read from the active theme on bind.
        if (id == NotificationCenter.didSetNewTheme) listView?.post { updateRows() }
    }

    override fun createView(context: Context): View {
        if (showBackButton) actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setTitle(title(context))
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) onBackButtonClick() else onMenuClick(id)
            }
        })
        createMenu(actionBar)

        val frame = FrameLayout(context)
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        fragmentView = frame

        val list = RecyclerListView(context)
        list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        list.isVerticalScrollBarEnabled = false
        val listAdapter = Adapter(context)
        list.adapter = listAdapter
        list.setOnItemClickListener(RecyclerListView.OnItemClickListener { view, position ->
            rows.getOrNull(position)?.let { onRowClick(it, view) }
        })
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        listView = list
        adapter = listAdapter

        updateRows()
        return frame
    }

    /** Rebuilds [rows] from state; safe to call before the view exists. */
    protected fun updateRows() {
        rows.clear()
        fillRows(rows)
        adapter?.notifyDataSetChanged()
    }

    override fun getThemeDescriptions(): ArrayList<ThemeDescription> {
        // Cells read their colors on bind, so a rebind after every color step is enough.
        val rebind = object : ThemeDescription.ThemeDescriptionDelegate {
            override fun didSetColor() {
                adapter?.notifyDataSetChanged()
            }
        }
        return arrayListOf(
            ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
            ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
            ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle),
            ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
            ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
            ThemeDescription(null, 0, null, null, null, rebind, Theme.key_windowBackgroundWhite),
            ThemeDescription(null, 0, null, null, null, rebind, Theme.key_windowBackgroundWhiteBlackText),
        )
    }

    private inner class Adapter(private val context: Context) : RecyclerListView.SelectionAdapter() {

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean =
            when (val row = rows.getOrNull(holder.adapterPosition)) {
                is Row.Text -> row.clickable
                is Row.Check -> true
                is Row.Custom -> row.id != 0
                else -> false
            }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int = when (val row = rows[position]) {
            is Row.Header -> TYPE_HEADER
            Row.Shadow -> TYPE_SHADOW
            is Row.Info -> TYPE_INFO
            is Row.Text -> TYPE_TEXT
            is Row.Check -> if (row.icon != 0) TYPE_CHECK_ICON else TYPE_CHECK
            is Row.Detail -> TYPE_DETAIL
            is Row.Custom -> row.type
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = when (viewType) {
                TYPE_HEADER -> HeaderCell(context)
                TYPE_SHADOW -> ShadowSectionCell(context)
                TYPE_INFO -> TextInfoPrivacyCell(context)
                TYPE_TEXT -> TextCell(context)
                TYPE_CHECK -> TextCheckCell(context)
                TYPE_CHECK_ICON -> TextCell(context)
                TYPE_DETAIL -> TextDetailSettingsCell(context).apply { setMultilineDetail(true) }
                else -> createCustomCell(context, viewType)
            }
            view.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            )
            return RecyclerListView.Holder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            val next = rows.getOrNull(position + 1)
            val divider = next != null && next !is Row.Shadow && next !is Row.Info && next !is Row.Header
            val view = holder.itemView
            if (row !is Row.Shadow && row !is Row.Info) {
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            }
            when (row) {
                // Upstream cells read their colors once at construction; refresh on every bind
                // so a day/night switch never leaves stale text colors behind.
                is Row.Header -> (view as HeaderCell).apply {
                    setText(row.text)
                    setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader))
                }
                Row.Shadow -> view.background = shadow(position)
                is Row.Info -> (view as TextInfoPrivacyCell).apply {
                    setText(row.text)
                    setTextColorByKey(Theme.key_windowBackgroundWhiteGrayText4)
                    background = shadow(position)
                }
                is Row.Text -> (view as TextCell).apply {
                    when {
                        row.value != null && row.icon != 0 -> setTextAndValueAndIcon(row.text, row.value, row.icon, divider)
                        row.value != null -> setTextAndValue(row.text, row.value, divider)
                        row.icon != 0 -> setTextAndIcon(row.text, row.icon, divider)
                        else -> setText(row.text, divider)
                    }
                    val iconKey = if (row.textKey == Theme.key_windowBackgroundWhiteBlackText) {
                        Theme.key_windowBackgroundWhiteGrayIcon
                    } else row.textKey
                    setColors(if (row.icon != 0) iconKey else -1, row.textKey)
                }
                is Row.Check -> if (view is TextCell) {
                    // TextCell aligns a switch row with the icon rows around it.
                    view.setTextAndCheckAndIcon(row.text, row.checked, row.icon, divider)
                    view.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText)
                } else (view as TextCheckCell).apply {
                    setColors(
                        Theme.key_windowBackgroundWhiteBlackText,
                        Theme.key_switchTrack, Theme.key_switchTrackChecked,
                        Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite,
                    )
                    setTextAndCheck(row.text, row.checked, divider)
                }
                is Row.Detail -> (view as TextDetailSettingsCell).setTextAndValue(row.title, row.value, divider)
                is Row.Custom -> bindCustomCell(view, row, divider)
            }
        }

        private fun shadow(position: Int) = Theme.getThemedDrawableByKey(
            context,
            if (position + 1 >= rows.size) R.drawable.greydivider_bottom else R.drawable.greydivider,
            Theme.key_windowBackgroundGrayShadow,
        )
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SHADOW = 1
        private const val TYPE_INFO = 2
        private const val TYPE_TEXT = 3
        private const val TYPE_CHECK = 4
        private const val TYPE_DETAIL = 5
        private const val TYPE_CHECK_ICON = 6
        const val CUSTOM_TYPE = 100
    }
}
