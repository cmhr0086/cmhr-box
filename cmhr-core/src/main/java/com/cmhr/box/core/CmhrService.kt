package com.cmhr.box.core

import com.cmhr.box.engine.api.EngineState
import com.cmhr.box.engine.api.ProxyEngine
import com.cmhr.box.engine.api.SubscriptionReplacement

/** Endpoints and app identity, supplied by the app's BuildConfig. Blank endpoint = feature off. */
data class CmhrConfig(
    val subscriptionEndpoint: String,
    val announcementEndpoint: String,
    val appControlEndpoint: String,
    val versionCode: Int,
    val defaultUpdateUrl: String,
)

/** Result of a refresh or connection check that did not throw. */
sealed interface SubscriptionStatus {
    /** Nodes are present; [needsSelection] when none is selected yet. */
    data class Ready(val nodeCount: Int, val needsSelection: Boolean) : SubscriptionStatus

    /** The managed subscription expired or ran out and was removed; the user must re-activate. */
    data class Removed(val reason: InvalidReason) : SubscriptionStatus
}

/**
 * Invite code, subscription, usage, announcement and app control, ported from 1.x
 * PrivateSubscriptionManager without the NekoBox database. Each refresh re-POSTs the invite
 * code for a fresh subscription URL; the URL itself is never stored.
 */
class CmhrService(
    private val config: CmhrConfig,
    private val api: CmhrApi,
    private val store: CmhrStore,
    private val engine: ProxyEngine,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val operations = SubscriptionOperations()

    val subscriptionEnabled: Boolean get() = config.subscriptionEndpoint.isNotBlank()

    val isActivated: Boolean get() = !store.inviteCode.isNullOrBlank()

    fun usage(): UsageInfo = UsageInfo.parse(store.subscriptionUserinfo)

    fun lastUpdatedAt(): Long = store.subscriptionUpdatedAt

    /** Days/percent left; keeps the period start across refreshes until the deadline changes. */
    fun expiryProgress(): SubscriptionExpiryProgress.State {
        val usage = usage()
        val state = SubscriptionExpiryProgress.calculate(
            usage.expire, usage.expiryKnown, nowSeconds(), store.expiryReference
        )
        state.reference?.let { store.expiryReference = it }
        return state
    }

    suspend fun announcement(): Announcement? = api.fetchAnnouncement(config.announcementEndpoint)

    /** True until the user acknowledges this exact announcement (any edit makes it new again). */
    fun isUnread(announcement: Announcement): Boolean = store.seenAnnouncement != announcementKey(announcement)

    fun markRead(announcement: Announcement) {
        store.seenAnnouncement = announcementKey(announcement)
    }

    private fun announcementKey(announcement: Announcement): String =
        "${announcement.updatedAt}:${(announcement.title + "\n" + announcement.content).hashCode()}"

    /** An activated subscription not refreshed for [intervalSeconds] should be refreshed quietly. */
    fun refreshDue(intervalSeconds: Long = AUTO_REFRESH_INTERVAL_SECONDS): Boolean =
        subscriptionEnabled && isActivated && nowSeconds() - store.subscriptionUpdatedAt >= intervalSeconds

    /** Null when access is allowed, the endpoint is off, or the control service is unreachable. */
    suspend fun checkAppAccess(): AppAccessIssue? = AppAccess.evaluate(
        api.fetchAppControl(config.appControlEndpoint),
        config.versionCode,
        config.defaultUpdateUrl,
    )

    /**
     * Activates or replaces the invite code. The current nodes are only replaced after the new
     * subscription was fetched, is usable and parsed to at least one node.
     */
    suspend fun activate(inviteCode: String): SubscriptionStatus.Ready = operations.run {
        requireSubscriptionService()
        requireAppAccess()
        val code = inviteCode.trim()
        val wasRunning = engine.state.value is EngineState.Running
        val payload = fetchSubscription(code)
        rejectUnusable(UsageInfo.parse(payload.userinfo))
        val nodes = replaceNodes(payload.body).nodes.size
        store.inviteCode = code
        saveUsage(payload.userinfo)
        // A different invite means a different node set; the user picks again.
        if (wasRunning) engine.stop()
        SubscriptionStatus.Ready(nodes, needsSelection = engine.selectedNodeId() == null)
    }

    /** Refreshes with the stored invite code. Expired or used-up subscriptions are removed. */
    suspend fun refresh(): SubscriptionStatus = operations.run {
        requireSubscriptionService()
        requireAppAccess()
        val code = store.inviteCode?.takeIf { it.isNotBlank() } ?: throw CmhrException.NotActivated()
        val payload = fetchSubscription(code)
        val usage = UsageInfo.parse(payload.userinfo)
        invalidReason(usage)?.let { return@run removeSubscription(it) }
        val replacement = replaceNodes(payload.body)
        saveUsage(payload.userinfo)
        // The running node is gone from the subscription: stop rather than keep using it.
        if (replacement.selectionLost && engine.state.value is EngineState.Running) {
            engine.stop()
        }
        SubscriptionStatus.Ready(replacement.nodes.size, needsSelection = engine.selectedNodeId() == null)
    }

    /**
     * Check before connecting: app access, activation, stored usage, nodes and selection.
     * Refreshes once when activated but no nodes are present.
     */
    suspend fun ensureReadyForConnection(): SubscriptionStatus {
        requireAppAccess()
        if (!subscriptionEnabled) throw CmhrException.SubscriptionServiceNotConfigured()
        if (!isActivated) throw CmhrException.NotActivated()
        invalidReason(usage())?.let { reason ->
            return operations.run { removeSubscription(reason) }
        }
        val nodes = engine.nodes()
        if (nodes.isEmpty()) return refresh()
        return SubscriptionStatus.Ready(nodes.size, needsSelection = engine.selectedNodeId() == null)
    }

    private suspend fun fetchSubscription(inviteCode: String): SubscriptionPayload {
        val url = api.requestSubscriptionUrl(config.subscriptionEndpoint, inviteCode)
            ?: throw CmhrException.SubscriptionServiceNotConfigured()
        return api.fetchSubscription(url)
    }

    private suspend fun replaceNodes(body: String): SubscriptionReplacement =
        try {
            engine.replaceManagedSubscription(body)
        } catch (e: IllegalArgumentException) {
            throw CmhrException.NoNodes()
        } catch (e: IllegalStateException) {
            throw CmhrException.NoNodes()
        }

    private suspend fun removeSubscription(reason: InvalidReason): SubscriptionStatus.Removed {
        if (engine.state.value is EngineState.Running) engine.stop()
        engine.clearManagedSubscription()
        store.clearSubscription()
        store.inviteCode = null
        return SubscriptionStatus.Removed(reason)
    }

    private fun saveUsage(userinfo: String?) {
        store.subscriptionUserinfo = userinfo
        store.subscriptionUpdatedAt = nowSeconds()
    }

    private fun invalidReason(usage: UsageInfo): InvalidReason? = when {
        usage.isExpired(nowSeconds()) -> InvalidReason.EXPIRED
        usage.isExhausted -> InvalidReason.EXHAUSTED
        else -> null
    }

    private fun rejectUnusable(usage: UsageInfo) {
        invalidReason(usage)?.let { throw CmhrException.SubscriptionUnusable(it) }
    }

    private fun requireSubscriptionService() {
        if (!subscriptionEnabled) throw CmhrException.SubscriptionServiceNotConfigured()
    }

    private suspend fun requireAppAccess() {
        checkAppAccess()?.let { throw CmhrException.AccessDenied(it) }
    }

    companion object {
        const val AUTO_REFRESH_INTERVAL_SECONDS = 6 * 60 * 60L
    }
}
