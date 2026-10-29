package com.cmhr.box.core

import java.net.URL

/**
 * Why the app may not be used right now. Blank messages mean "use the app's default text";
 * a blank updateUrl means there is no link to offer.
 */
sealed interface AppAccessIssue {
    data class ServiceDisabled(val message: String) : AppAccessIssue
    data class UpdateRequired(val message: String, val updateUrl: String) : AppAccessIssue
}

object AppAccess {
    fun evaluate(control: AppControl?, versionCode: Int, defaultUpdateUrl: String): AppAccessIssue? {
        if (control == null) return null
        if (!control.serviceEnabled) {
            return AppAccessIssue.ServiceDisabled(control.disabledMessage)
        }
        if (control.minVersionCode > versionCode) {
            return AppAccessIssue.UpdateRequired(
                control.updateMessage,
                resolveUpdateUrl(control.updateUrl, defaultUpdateUrl),
            )
        }
        return null
    }

    /** Only an https URL with a host is accepted; anything else falls back to [default] (may be blank). */
    fun resolveUpdateUrl(value: String, default: String): String {
        val candidate = value.trim()
        val valid = runCatching {
            val url = URL(candidate)
            url.protocol.equals("https", ignoreCase = true) && url.host.isNotBlank()
        }.getOrDefault(false)
        return if (valid) candidate else default
    }
}
