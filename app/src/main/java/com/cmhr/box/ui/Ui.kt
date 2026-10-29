package com.cmhr.box.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import com.cmhr.box.R
import com.cmhr.box.core.AppAccessIssue
import com.cmhr.box.core.CmhrException
import com.cmhr.box.core.InvalidReason
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import java.io.IOException

fun Context.toast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

/** User-facing text for business and network failures. */
fun Context.describeError(error: Throwable): String = when (error) {
    is CmhrException.ServerRejected -> error.message.orEmpty()
    is CmhrException.SubscriptionServiceNotConfigured -> getString(R.string.cmhr_error_not_configured)
    is CmhrException.NotActivated -> getString(R.string.cmhr_error_not_activated)
    is CmhrException.SubscriptionUrlMissing -> getString(R.string.cmhr_error_url_missing)
    is CmhrException.NoNodes -> getString(R.string.cmhr_error_no_nodes)
    is CmhrException.SubscriptionUnusable -> getString(
        if (error.reason == InvalidReason.EXPIRED) R.string.cmhr_error_unusable_expired
        else R.string.cmhr_error_unusable_exhausted
    )
    is CmhrException.AccessDenied -> when (val issue = error.issue) {
        is AppAccessIssue.ServiceDisabled -> issue.message.ifBlank { getString(R.string.cmhr_service_disabled_message) }
        is AppAccessIssue.UpdateRequired -> issue.message.ifBlank { getString(R.string.cmhr_update_required_message) }
    }
    is IOException -> getString(R.string.cmhr_error_network, error.message ?: error.javaClass.simpleName)
    else -> error.message ?: error.javaClass.simpleName
}

/** Relative "x minutes ago" style text for an epoch-seconds timestamp. */
fun Context.formatUpdatedAt(epochSeconds: Long): String =
    if (epochSeconds <= 0L) getString(R.string.cmhr_never_updated)
    else if (System.currentTimeMillis() / 1000 - epochSeconds < 60) getString(R.string.cmhr_updated_just_now)
    else getString(
        R.string.cmhr_updated_at,
        DateUtils.getRelativeTimeSpanString(
            epochSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
    )

/** Binary units up to TB (tgui's formatFileSize stops at GB, so 10 TB reads "10240 GB"). */
fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return when {
        unit == 0 -> "$bytes B"
        value >= 100 -> "%.0f %s".format(value, units[unit])
        value >= 10 -> "%.1f %s".format(value, units[unit])
        else -> "%.2f %s".format(value, units[unit])
    }
}

fun delayColorKey(delayMs: Long?): Int = when {
    delayMs == null -> Theme.key_windowBackgroundWhiteGrayText2
    delayMs < 0 -> Theme.key_text_RedBold
    delayMs < 300 -> Theme.key_windowBackgroundWhiteGreenText2
    else -> Theme.key_windowBackgroundWhiteValueText
}

fun Context.delayText(delayMs: Long?): String = when {
    delayMs == null -> ""
    delayMs < 0 -> getString(R.string.cmhr_delay_timeout)
    else -> getString(R.string.cmhr_delay_ms, delayMs)
}

/** Service paused or update required. Dismissable, like 1.x; the app stays usable for reading. */
fun BaseFragment.showAccessDialog(issue: AppAccessIssue) {
    val context = parentActivity ?: return
    val builder = AlertDialog.Builder(context)
    when (issue) {
        is AppAccessIssue.ServiceDisabled -> builder
            .setTitle(context.getString(R.string.cmhr_service_disabled_title))
            .setMessage(issue.message.ifBlank { context.getString(R.string.cmhr_service_disabled_message) })
            .setPositiveButton(context.getString(R.string.cmhr_ok), null)

        is AppAccessIssue.UpdateRequired -> {
            builder.setTitle(context.getString(R.string.cmhr_update_required_title))
                .setMessage(issue.message.ifBlank { context.getString(R.string.cmhr_update_required_message) })
            if (issue.updateUrl.isBlank()) {
                builder.setPositiveButton(context.getString(R.string.cmhr_ok), null)
            } else {
                builder.setPositiveButton(context.getString(R.string.cmhr_update_now)) { _, _ ->
                    if (!context.openUrl(issue.updateUrl)) context.toast(context.getString(R.string.cmhr_no_browser))
                }
                builder.setNeutralButton(context.getString(R.string.cmhr_copy_link)) { _, _ ->
                    context.copyText(issue.updateUrl)
                    context.toast(context.getString(R.string.cmhr_link_copied))
                }
            }
        }
    }
    showDialog(builder.create())
}

fun BaseFragment.showRemovedDialog(reason: InvalidReason) {
    val context = parentActivity ?: return
    showDialog(
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cmhr_subscription_removed_title))
            .setMessage(
                context.getString(
                    if (reason == InvalidReason.EXPIRED) R.string.cmhr_removed_expired
                    else R.string.cmhr_removed_exhausted
                )
            )
            .setPositiveButton(context.getString(R.string.cmhr_ok), null)
            .create()
    )
}

fun Context.openUrl(url: String): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(packageManager) == null) return false
    startActivity(intent)
    true
}.getOrDefault(false)

fun Context.copyText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}

/** Vendor runtime permission for the installed-app list; only some ROMs define it. */
private const val GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"

/** Asks for the app list where the ROM gates it; a no-op elsewhere or once granted. */
fun Activity.requestAppListPermission(requestCode: Int) {
    val defined = runCatching { packageManager.getPermissionInfo(GET_INSTALLED_APPS, 0) }.isSuccess
    if (defined && checkSelfPermission(GET_INSTALLED_APPS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(GET_INSTALLED_APPS), requestCode)
    }
}
