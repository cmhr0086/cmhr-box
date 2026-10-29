package com.cmhr.box.ui

import android.content.Context
import android.view.View
import com.cmhr.box.AppGraph
import com.cmhr.box.R
import com.cmhr.box.core.CmhrException
import com.cmhr.box.core.SubscriptionExpiryProgress
import com.cmhr.box.core.SubscriptionStatus
import com.cmhr.box.core.UsageInfo
import kotlinx.coroutines.launch
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.tgui.R as TR

/** Subscription: traffic and validity, manual refresh, invite code replacement. */
class SubscriptionFragment : ListFragment() {

    private val cmhr get() = AppGraph.cmhr
    private var usage = UsageInfo()
    private var expiry = SubscriptionExpiryProgress.State(SubscriptionExpiryProgress.Kind.UNKNOWN)
    private var updatedAt = 0L
    private var refreshing = false

    override fun title(context: Context): CharSequence = context.getString(R.string.cmhr_section_subscription)

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        usage = cmhr.usage()
        expiry = cmhr.expiryProgress()
        updatedAt = cmhr.lastUpdatedAt()
        updateRows()
    }

    override fun fillRows(rows: MutableList<Row>) {
        val context = parentActivity ?: return
        if (!cmhr.isActivated) {
            rows += Row.Shadow
            rows += Row.Custom(TYPE_ACTION, ID_INVITE, Action(TR.drawable.msg_link, context.getString(R.string.cmhr_invite_submit), null, false, Theme.key_windowBackgroundWhiteBlueText4))
            rows += Row.Info(context.getString(R.string.cmhr_invite_description))
            return
        }
        rows += Row.Header(context.getString(R.string.cmhr_section_usage))
        rows += Row.Custom(TYPE_USAGE, 0, trafficPayload(context))
        rows += Row.Custom(TYPE_USAGE, 0, validityPayload(context))
        rows += Row.Shadow
        rows += Row.Custom(
            TYPE_ACTION, ID_REFRESH,
            Action(TR.drawable.msg_retry, context.getString(R.string.cmhr_refresh_subscription), context.formatUpdatedAt(updatedAt), refreshing, Theme.key_windowBackgroundWhiteBlueText4),
        )
        rows += Row.Custom(
            TYPE_ACTION, ID_INVITE,
            Action(TR.drawable.msg_link, context.getString(R.string.cmhr_change_invite), null, false, Theme.key_text_RedRegular),
        )
        rows += Row.Info(context.getString(R.string.cmhr_change_invite_confirm))
    }

    private data class Action(val icon: Int, val text: String, val value: String?, val loading: Boolean, val textKey: Int)

    override fun createCustomCell(context: Context, type: Int): View =
        if (type == TYPE_USAGE) UsageCell(context) else ActionCell(context)

    override fun bindCustomCell(view: View, row: Row.Custom, divider: Boolean) {
        when (val payload = row.payload) {
            is UsagePayload -> (view as UsageCell).bind(payload.title, payload.value, payload.fraction, payload.warn, divider)
            is Action -> (view as ActionCell).bind(payload.icon, payload.text, payload.value, payload.loading, payload.textKey, divider)
        }
    }

    override fun onRowClick(row: Row, view: View) {
        when (row.id) {
            ID_REFRESH -> refresh()
            ID_INVITE -> if (cmhr.isActivated) confirmChangeInvite() else presentFragment(InviteFragment.create(replace = false))
        }
    }

    private fun refresh() {
        if (refreshing) return
        refreshing = true
        updateRows()
        scope?.launch {
            val context = parentActivity
            runCatching { cmhr.refresh() }
                .onSuccess { status ->
                    when (status) {
                        is SubscriptionStatus.Removed -> showRemovedDialog(status.reason)
                        is SubscriptionStatus.Ready -> context?.toast(context.getString(R.string.cmhr_refresh_done))
                    }
                }
                .onFailure { error ->
                    if (error is CmhrException.AccessDenied) showAccessDialog(error.issue)
                    else context?.toast(context.describeError(error))
                }
            refreshing = false
            reload()
        }
    }

    /** Replacing the invite is destructive for the current node set: red confirm button. */
    private fun confirmChangeInvite() {
        val context = parentActivity ?: return
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cmhr_change_invite))
            .setMessage(context.getString(R.string.cmhr_change_invite_confirm))
            .setPositiveButton(context.getString(R.string.cmhr_change_invite_action)) { _, _ ->
                presentFragment(InviteFragment.create(replace = true))
            }
            .setNegativeButton(context.getString(R.string.cmhr_cancel), null)
            .create()
        showDialog(dialog)
        // Builder.makeRed only applies in Builder.show(); fragment dialogs go through showDialog.
        dialog.redPositive()
    }

    private data class UsagePayload(val title: String, val value: String, val fraction: Float?, val warn: Boolean)

    private fun trafficText(context: Context): String {
        val used = usage.used.coerceAtLeast(0L)
        return when {
            usage.total > 0L -> context.getString(R.string.cmhr_traffic_used_total, formatBytes(used), formatBytes(usage.total))
            used > 0L -> context.getString(R.string.cmhr_traffic_used_only, formatBytes(used))
            else -> context.getString(R.string.cmhr_traffic_unlimited)
        }
    }

    private fun trafficPayload(context: Context): UsagePayload {
        val title = context.getString(R.string.cmhr_traffic)
        if (usage.total <= 0L) return UsagePayload(title, trafficText(context), null, false)
        val fraction = (usage.used.coerceAtLeast(0L).toDouble() / usage.total).toFloat().coerceIn(0f, 1f)
        return UsagePayload(title, trafficText(context), fraction, warn = fraction >= 0.9f)
    }

    private fun validityPayload(context: Context): UsagePayload {
        val title = context.getString(R.string.cmhr_validity)
        return when (expiry.kind) {
            SubscriptionExpiryProgress.Kind.UNKNOWN -> UsagePayload(title, context.getString(R.string.cmhr_validity_unknown), null, false)
            SubscriptionExpiryProgress.Kind.UNLIMITED -> UsagePayload(title, context.getString(R.string.cmhr_validity_unlimited), null, false)
            SubscriptionExpiryProgress.Kind.EXPIRED -> UsagePayload(title, context.getString(R.string.cmhr_validity_expired), 1f, true)
            SubscriptionExpiryProgress.Kind.ACTIVE -> UsagePayload(
                title,
                context.resources.getQuantityString(R.plurals.cmhr_validity_days, expiry.daysRemaining, expiry.daysRemaining),
                // Elapsed share, so this bar fills up like the traffic bar above it.
                1f - expiry.percentRemaining / 100f,
                warn = expiry.daysRemaining <= 3,
            )
        }
    }

    private companion object {
        const val TYPE_USAGE = CUSTOM_TYPE
        const val TYPE_ACTION = CUSTOM_TYPE + 1
        const val ID_REFRESH = 1
        const val ID_INVITE = 2
    }
}
