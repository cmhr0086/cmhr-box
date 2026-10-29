package com.cmhr.box.ui

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.EditText
import com.cmhr.box.AppGraph
import com.cmhr.box.R
import com.cmhr.box.engine.api.PerAppMode
import com.cmhr.box.engine.api.PerAppProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.Theme
import org.telegram.tgui.R as TR

/**
 * Per-app proxy: a mode (off / proxy only checked apps / bypass checked apps) and the app list.
 * Changes are written once when the screen closes, so the tunnel reconnects at most once.
 */
class PerAppFragment : ListFragment() {

    private class App(val packageName: String, val label: String, val icon: Drawable?, val system: Boolean)

    private val engine get() = AppGraph.engine
    private var loaded: PerAppProxy? = null
    private var mode = PerAppMode.OFF
    private val selected = HashSet<String>()
    private var apps: List<App> = emptyList()
    private var query = ""
    private var showSystem = false

    override fun title(context: Context): CharSequence = context.getString(R.string.cmhr_per_app)

    override fun onFragmentCreate(): Boolean {
        val created = super.onFragmentCreate()
        scope?.launch {
            val current = engine.perAppProxy()
            loaded = current
            mode = current.mode
            selected.addAll(current.packages)
            updateRows()
            apps = loadApps()
            updateRows()
        }
        return created
    }

    override fun onFragmentDestroy() {
        val before = loaded
        val after = PerAppProxy(mode, selected.toSet())
        if (before != null && before != after) {
            // The fragment scope is cancelled below; the write must outlive it.
            AppGraph.applicationScope.launch { engine.setPerAppProxy(after) }
        }
        super.onFragmentDestroy()
    }

    private suspend fun loadApps(): List<App> = withContext(Dispatchers.IO) {
        val pm = parentActivity?.packageManager ?: return@withContext emptyList()
        val self = parentActivity?.packageName
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { info ->
                info.packageName != self &&
                    info.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            }
            .map { info ->
                val appInfo = info.applicationInfo
                App(
                    packageName = info.packageName,
                    label = appInfo?.loadLabel(pm)?.toString() ?: info.packageName,
                    icon = runCatching { appInfo?.loadIcon(pm) }.getOrNull(),
                    system = appInfo != null && appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    override fun createMenu(actionBar: ActionBar) {
        val context = actionBar.context
        val search = actionBar.createMenu().addItem(MENU_SEARCH, TR.drawable.ic_ab_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onTextChanged(editText: EditText) {
                    query = editText.text.toString().trim()
                    updateRows()
                }

                override fun onSearchCollapse() {
                    query = ""
                    updateRows()
                }
            })
        search.setSearchFieldHint(context.getString(R.string.cmhr_per_app_search))
    }

    override fun fillRows(rows: MutableList<Row>) {
        val context = parentActivity ?: return
        if (query.isEmpty()) {
            rows += Row.Shadow
            for (option in PerAppMode.entries) {
                val description = when (option) {
                    PerAppMode.OFF -> R.string.cmhr_per_app_off_desc
                    PerAppMode.PROXY_SELECTED -> R.string.cmhr_per_app_proxy_desc
                    PerAppMode.BYPASS_SELECTED -> R.string.cmhr_per_app_bypass_desc
                }
                rows += Row.Custom(TYPE_CHOICE, MODE_ID_BASE + option.ordinal, Choice(context.getString(option.title()), context.getString(description), option == mode))
            }
            rows += Row.Info(context.getString(R.string.cmhr_per_app_hint))
        }
        if (mode == PerAppMode.OFF) return

        rows += Row.Header(context.getString(R.string.cmhr_per_app_apps))
        if (query.isEmpty()) rows += Row.Check(ID_SHOW_SYSTEM, context.getString(R.string.cmhr_per_app_show_system), showSystem)
        apps.asSequence()
            .filter { showSystem || !it.system || it.packageName in selected }
            .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            // Checked apps first, so the current choice is visible without scrolling.
            .sortedBy { it.packageName !in selected }
            .forEachIndexed { index, app -> rows += Row.Custom(TYPE_APP, APP_ID_BASE + index, app) }
        rows += Row.Shadow
    }

    override fun createCustomCell(context: Context, type: Int): View =
        if (type == TYPE_APP) AppCell(context) else NodeCell(context)

    override fun bindCustomCell(view: View, row: Row.Custom, divider: Boolean) {
        when (val payload = row.payload) {
            is Choice -> (view as NodeCell).bind(payload.title, payload.description, "", Theme.key_windowBackgroundWhiteGrayText2, payload.selected, divider)
            is App -> (view as AppCell).bind(payload.label, payload.packageName, payload.icon, payload.packageName in selected, divider)
        }
    }

    override fun onRowClick(row: Row, view: View) {
        val payload = (row as? Row.Custom)?.payload
        when {
            row.id == ID_SHOW_SYSTEM -> showSystem = !showSystem
            payload is Choice -> mode = PerAppMode.entries[row.id - MODE_ID_BASE]
            payload is App -> if (!selected.remove(payload.packageName)) selected.add(payload.packageName)
            else -> return
        }
        updateRows()
    }

    private companion object {
        const val MENU_SEARCH = 1
        const val TYPE_CHOICE = CUSTOM_TYPE
        const val TYPE_APP = CUSTOM_TYPE + 1
        const val ID_SHOW_SYSTEM = 1
        const val MODE_ID_BASE = 10
        const val APP_ID_BASE = 100
    }
}
