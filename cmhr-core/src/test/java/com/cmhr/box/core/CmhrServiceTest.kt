package com.cmhr.box.core

import android.content.Context
import android.content.Intent
import com.cmhr.box.engine.api.EngineState
import com.cmhr.box.engine.api.Node
import com.cmhr.box.engine.api.PerAppMode
import com.cmhr.box.engine.api.PerAppProxy
import com.cmhr.box.engine.api.RoutingMode
import com.cmhr.box.engine.api.ProxyEngine
import com.cmhr.box.engine.api.SubscriptionReplacement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CmhrServiceTest {
    private val host = "service.example.invalid"
    private val now = 1_800_000_000L

    private class MemoryStore : CmhrStore {
        override var inviteCode: String? = null
        override var subscriptionUserinfo: String? = null
        override var subscriptionUpdatedAt: Long = 0L
        override var expiryReference: String? = null
        override var seenAnnouncement: String? = null
        override var cachedAnnouncement: String? = null
    }

    /** Body lines are node names; selection maps by name like upstream ProfileReplacement. */
    private class FakeEngine : ProxyEngine {
        override val state = MutableStateFlow<EngineState>(EngineState.Stopped)
        var nodes = listOf<Node>()
        var selected: String? = null
        var generation = 0
        var stops = 0

        override suspend fun replaceManagedSubscription(rawContent: String): SubscriptionReplacement {
            val names = rawContent.lines().filter { it.isNotBlank() }
            require(names.isNotEmpty()) { "no nodes" }
            generation++
            val selectedName = nodes.firstOrNull { it.id == selected }?.name
            nodes = names.map { Node("$generation-$it", it, "vless", null) }
            val match = nodes.firstOrNull { it.name == selectedName }?.id
            selected = match ?: nodes.first().id
            return SubscriptionReplacement(nodes, selectionLost = selectedName != null && match == null)
        }

        override suspend fun clearManagedSubscription() { nodes = emptyList(); selected = null }
        override suspend fun nodes() = nodes
        override suspend fun selectedNodeId() = selected?.takeIf { id -> nodes.any { it.id == id } }
        override suspend fun select(nodeId: String) { selected = nodeId }
        override fun prepareIntent(context: Context): Intent? = null
        override suspend fun start() { state.value = EngineState.Running(selected) }
        override suspend fun stop() { stops++; state.value = EngineState.Stopped }
        override suspend fun testDelay(nodeIds: List<String>) = emptyMap<String, Long>()
        override suspend fun routingMode() = RoutingMode.BYPASS_MAINLAND
        override suspend fun setRoutingMode(mode: RoutingMode) {}
        override suspend fun perAppProxy() = PerAppProxy(PerAppMode.OFF, emptySet())
        override suspend fun setPerAppProxy(value: PerAppProxy) {}
        override suspend fun ipv6Enabled() = false
        override suspend fun setIpv6Enabled(enabled: Boolean) {}
    }

    private lateinit var store: MemoryStore
    private lateinit var engine: FakeEngine

    private fun service(subscription: Boolean = true, control: Boolean = true, announcement: Boolean = false) = CmhrService(
        CmhrConfig(
            subscriptionEndpoint = if (subscription) "https://$host/subscription" else "",
            announcementEndpoint = if (announcement) "https://$host/announcement" else "",
            appControlEndpoint = if (control) "https://$host/control" else "",
            versionCode = 100,
            defaultUpdateUrl = "https://fallback.example.invalid",
        ),
        CmhrApi(100), store, engine, nowSeconds = { now },
    )

    private fun serveSubscription(name: String, body: String, userinfo: String? = "upload=1; download=1; total=100; expire=0") {
        FakeHttp.routes["$host/subscription"] = FakeHttp.Response(
            body = """{"subscriptionUrl":"https://config.example.invalid/$name"}""")
        FakeHttp.routes["config.example.invalid/$name"] = FakeHttp.Response(
            body = body, headers = userinfo?.let { mapOf("subscription-userinfo" to it) } ?: emptyMap())
    }

    private fun serveControl(json: String) {
        FakeHttp.routes["$host/control"] = FakeHttp.Response(body = json)
    }

    @Before fun reset() {
        FakeHttp.reset()
        store = MemoryStore()
        engine = FakeEngine()
        serveControl("""{"serviceEnabled":true,"minVersionCode":100}""")
    }

    @Test fun activationStoresInviteAndUsageButNeverTheSubscriptionUrl() = runBlocking {
        serveSubscription("a", "HK\nJP")
        val status = service().activate("  code-1  ")
        assertEquals(SubscriptionStatus.Ready(2, needsSelection = false), status)
        assertEquals("code-1", store.inviteCode)
        assertEquals("upload=1; download=1; total=100; expire=0", store.subscriptionUserinfo)
        assertEquals(now, store.subscriptionUpdatedAt)
        assertEquals(listOf("HK", "JP"), engine.nodes.map { it.name })
        assertFalse(listOf(store.inviteCode, store.subscriptionUserinfo, store.expiryReference)
            .any { it?.contains("config.example.invalid") == true })
    }

    @Test fun everyRefreshPostsTheInviteAgainAndUsesTheLatestUrl() = runBlocking {
        store.inviteCode = "code-1"
        serveSubscription("old", "HK")
        service().refresh()
        serveSubscription("new", "HK\nSG")
        service().refresh()
        val posts = FakeHttp.requests.filter { it.url.path == "/subscription" }
        assertEquals(2, posts.size)
        assertTrue(posts.all { JSONObject(it.body.toString("UTF-8")).getString("inviteCode") == "code-1" })
        val fetched = FakeHttp.requests.filter { it.url.host == "config.example.invalid" }.map { it.url.path }
        assertEquals(listOf("/old", "/new"), fetched)
        assertEquals(listOf("HK", "SG"), engine.nodes.map { it.name })
    }

    @Test fun failedActivationKeepsTheCurrentSubscription() = runBlocking {
        store.inviteCode = "old-code"
        serveSubscription("a", "HK")
        service().refresh()
        val before = engine.nodes

        serveSubscription("b", "")
        assertTrue(runCatching { service().activate("new-code") }.exceptionOrNull() is CmhrException.NoNodes)
        serveSubscription("c", "JP", userinfo = "upload=0; download=0; total=0; expire=${now - 1}")
        val unusable = runCatching { service().activate("new-code") }.exceptionOrNull()
        assertEquals(InvalidReason.EXPIRED, (unusable as CmhrException.SubscriptionUnusable).reason)

        assertEquals("old-code", store.inviteCode)
        assertEquals(before, engine.nodes)
    }

    @Test fun expiredOrExhaustedRefreshRemovesTheManagedSubscription() = runBlocking {
        for ((userinfo, reason) in listOf(
            "upload=0; download=0; total=0; expire=$now" to InvalidReason.EXPIRED,
            "upload=60; download=40; total=100; expire=0" to InvalidReason.EXHAUSTED,
        )) {
            store.inviteCode = "code-1"
            serveSubscription("a", "HK")
            service().refresh()
            engine.start()
            serveSubscription("b", "HK", userinfo = userinfo)
            assertEquals(SubscriptionStatus.Removed(reason), service().refresh())
            assertTrue(engine.nodes.isEmpty())
            assertEquals(EngineState.Stopped, engine.state.value)
            assertNull(store.inviteCode)
            assertNull(store.subscriptionUserinfo)
        }
    }

    @Test fun refreshKeepsTheConnectionWhenTheSelectedNodeSurvives() = runBlocking {
        store.inviteCode = "code-1"
        serveSubscription("a", "HK\nJP")
        service().refresh()
        engine.selected = engine.nodes.first { it.name == "JP" }.id
        engine.start()

        serveSubscription("b", "JP\nSG")
        service().refresh()
        assertEquals(0, engine.stops)
        assertEquals("JP", engine.nodes.first { it.id == engine.selected }.name)

        serveSubscription("c", "SG")
        service().refresh()
        assertEquals(1, engine.stops)
    }

    @Test fun appControlBlocksBeforeAnySubscriptionRequest() = runBlocking {
        store.inviteCode = "code-1"
        serveControl("""{"serviceEnabled":false,"disabledMessage":"maintenance"}""")
        val denied = runCatching { service().refresh() }.exceptionOrNull() as CmhrException.AccessDenied
        assertEquals(AppAccessIssue.ServiceDisabled("maintenance"), denied.issue)

        serveControl("""{"serviceEnabled":true,"minVersionCode":101,"updateUrl":"https://dl.example.invalid/app.apk"}""")
        val update = runCatching { service().activate("x") }.exceptionOrNull() as CmhrException.AccessDenied
        assertEquals(AppAccessIssue.UpdateRequired("", "https://dl.example.invalid/app.apk"), update.issue)
        assertTrue(FakeHttp.requests.none { it.url.path == "/subscription" })
    }

    @Test fun eachEndpointCanBeTurnedOffOnItsOwn() = runBlocking {
        store.inviteCode = "code-1"
        serveSubscription("a", "HK")
        assertTrue(runCatching { service(subscription = false).refresh() }.exceptionOrNull()
            is CmhrException.SubscriptionServiceNotConfigured)
        assertTrue(FakeHttp.requests.isEmpty())

        serveControl("""{"serviceEnabled":false}""")
        assertEquals(SubscriptionStatus.Ready(1, needsSelection = false), service(control = false).refresh())
        assertTrue(FakeHttp.requests.none { it.url.path == "/control" })
    }

    @Test fun announcementStaysUnreadUntilAcknowledgedAndAnyEditMakesItNew() {
        val service = service()
        val first = Announcement("Notice", "Body", "2026-10-28 12:00:00")
        assertTrue(service.isUnread(first))
        service.markRead(first)
        assertFalse(service.isUnread(first))
        assertTrue(service.isUnread(first.copy(content = "Body, edited")))
        assertTrue(service.isUnread(first.copy(updatedAt = "2026-10-29 08:00:00")))
    }

    @Test fun announcementIsCachedKeptOnFailureAndClearedWhenWithdrawn() = runBlocking {
        val service = service(announcement = true)
        assertNull(service.cachedAnnouncement())

        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(code = 503, body = "")
        assertEquals(AnnouncementResult.Failed, service.announcement())

        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(
            body = """{"enabled":true,"title":"Notice","content":"Body","updatedAt":"2026-10-29 10:00:00"}""")
        val shown = AnnouncementResult.Shown(Announcement("Notice", "Body", "2026-10-29 10:00:00"))
        assertEquals(shown, service.announcement())
        assertEquals(shown.item, service.cachedAnnouncement())

        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(code = 503, body = "")
        assertEquals(shown, service.announcement())

        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(body = """{"enabled":false}""")
        assertEquals(AnnouncementResult.None, service.announcement())
        assertNull(service.cachedAnnouncement())
    }

    @Test fun refreshIsDueOnlyForActivatedSubscriptionsPastTheInterval() {
        val interval = CmhrService.AUTO_REFRESH_INTERVAL_SECONDS
        assertFalse(service().refreshDue())
        store.inviteCode = "code-1"
        store.subscriptionUpdatedAt = now - interval + 60
        assertFalse(service().refreshDue())
        store.subscriptionUpdatedAt = now - interval
        assertTrue(service().refreshDue())
        assertFalse(service(subscription = false).refreshDue())
    }

    @Test fun connectionCheckRefreshesOnceWhenNoNodesAreStored() = runBlocking {
        assertTrue(runCatching { service().ensureReadyForConnection() }.exceptionOrNull() is CmhrException.NotActivated)

        store.inviteCode = "code-1"
        store.subscriptionUserinfo = "upload=0; download=0; total=0; expire=0"
        serveSubscription("a", "HK")
        assertEquals(SubscriptionStatus.Ready(1, needsSelection = false), service().ensureReadyForConnection())
        assertEquals(1, FakeHttp.requests.count { it.url.path == "/subscription" })

        store.subscriptionUserinfo = "upload=0; download=0; total=0; expire=${now - 10}"
        assertEquals(SubscriptionStatus.Removed(InvalidReason.EXPIRED), service().ensureReadyForConnection())
    }
}
