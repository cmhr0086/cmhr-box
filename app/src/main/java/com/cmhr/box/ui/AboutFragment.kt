package com.cmhr.box.ui

import android.content.Context
import android.view.View
import com.cmhr.box.BuildConfig
import com.cmhr.box.R
import com.cmhr.box.engine.api.PerAppMode
import com.cmhr.box.engine.api.RoutingMode
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.tgui.R as TR

/** About: version, licenses, source. */
class AboutFragment : ListFragment() {

    override fun title(context: Context): CharSequence = context.getString(R.string.cmhr_section_about)

    override fun fillRows(rows: MutableList<Row>) {
        val context = parentActivity ?: return
        rows += Row.Custom(TYPE_HEADER, 0, null)
        rows += Row.Shadow
        rows += Row.Text(0, context.getString(R.string.cmhr_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", TR.drawable.msg_info, clickable = false)
        rows += Row.Text(ID_LICENSES, context.getString(R.string.cmhr_licenses), icon = TR.drawable.msg_copy)
        rows += Row.Text(ID_SOURCE, context.getString(R.string.cmhr_source_code), "GitHub", TR.drawable.msg_link2)
        rows += Row.Info(context.getString(R.string.cmhr_about_info))
    }

    override fun createCustomCell(context: Context, type: Int): View = AboutHeaderCell(context)

    override fun bindCustomCell(view: View, row: Row.Custom, divider: Boolean) {
        (view as AboutHeaderCell).bind(view.context.getString(R.string.cmhr_app_name), "${BuildConfig.VERSION_NAME}")
    }

    override fun onRowClick(row: Row, view: View) {
        when (row.id) {
            ID_LICENSES -> presentFragment(LicensesFragment())
            ID_SOURCE -> parentActivity?.openUrl(SOURCE_URL)
        }
    }

    private companion object {
        const val TYPE_HEADER = CUSTOM_TYPE
        const val ID_LICENSES = 1
        const val ID_SOURCE = 2
        const val SOURCE_URL = "https://github.com/cmhr0086/cmhr-box"
    }
}

fun RoutingMode.title(): Int = when (this) {
    RoutingMode.GLOBAL -> R.string.cmhr_routing_global
    RoutingMode.BYPASS_MAINLAND -> R.string.cmhr_routing_bypass
    RoutingMode.DIRECT -> R.string.cmhr_routing_direct
}

fun RoutingMode.description(): Int = when (this) {
    RoutingMode.GLOBAL -> R.string.cmhr_routing_global_desc
    RoutingMode.BYPASS_MAINLAND -> R.string.cmhr_routing_bypass_desc
    RoutingMode.DIRECT -> R.string.cmhr_routing_direct_desc
}

fun PerAppMode.title(): Int = when (this) {
    PerAppMode.OFF -> R.string.cmhr_per_app_off
    PerAppMode.PROXY_SELECTED -> R.string.cmhr_per_app_proxy
    PerAppMode.BYPASS_SELECTED -> R.string.cmhr_per_app_bypass
}

data class Choice(val title: String, val description: String, val selected: Boolean)

/** GPL notice plus the licenses of the bundled upstream projects. */
class LicensesFragment : ListFragment() {

    override fun title(context: Context): CharSequence = context.getString(R.string.cmhr_licenses)

    override fun fillRows(rows: MutableList<Row>) {
        val context = parentActivity ?: return
        rows += Row.Info(context.getString(R.string.cmhr_licenses_intro))
        listOf(
            R.string.cmhr_license_v2rayng to "https://github.com/2dust/v2rayNG",
            R.string.cmhr_license_xray to "https://github.com/XTLS/Xray-core",
            R.string.cmhr_license_libxray to "https://github.com/2dust/AndroidLibXrayLite",
            R.string.cmhr_license_hev to "https://github.com/heiher/hev-socks5-tunnel",
            R.string.cmhr_license_tgui to "https://github.com/cmhr0086/telegram-11-ui",
        ).forEachIndexed { index, (text, url) ->
            rows += Row.Custom(TYPE_LINK, index + 1, context.getString(text) to url)
        }
        rows += Row.Info(context.getString(R.string.cmhr_license_more))
    }

    override fun createCustomCell(context: Context, type: Int): View = TextCell(context)

    @Suppress("UNCHECKED_CAST")
    override fun bindCustomCell(view: View, row: Row.Custom, divider: Boolean) {
        val (text, _) = row.payload as Pair<String, String>
        (view as TextCell).apply {
            setText(text, divider)
            setColors(-1, Theme.key_windowBackgroundWhiteBlackText)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun onRowClick(row: Row, view: View) {
        val (_, url) = (row as? Row.Custom)?.payload as? Pair<String, String> ?: return
        parentActivity?.openUrl(url)
    }

    private companion object {
        const val TYPE_LINK = CUSTOM_TYPE
    }
}
