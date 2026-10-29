package com.cmhr.box.core

/**
 * Business failures. The app maps each type to localized text; only [ServerRejected] carries
 * text meant for display (the Worker's own message).
 */
sealed class CmhrException(message: String? = null) : Exception(message) {
    class SubscriptionServiceNotConfigured : CmhrException()
    class NotActivated : CmhrException()
    class ServerRejected(message: String) : CmhrException(message)
    class SubscriptionUrlMissing : CmhrException()
    class NoNodes : CmhrException()

    /** The fetched subscription is expired or used up; the current one was kept. */
    class SubscriptionUnusable(val reason: InvalidReason) : CmhrException()

    class AccessDenied(val issue: AppAccessIssue) : CmhrException()
}

enum class InvalidReason {
    EXPIRED,
    EXHAUSTED,
}
