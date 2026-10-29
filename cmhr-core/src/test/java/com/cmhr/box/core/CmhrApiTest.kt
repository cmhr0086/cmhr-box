package com.cmhr.box.core

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Ported from 1.x PrivateServiceRequestsTest: real request/response code, never production. */
class CmhrApiTest {
    private val api = CmhrApi(versionCode = 100)
    private val host = "service.example.invalid"

    @Before fun reset() {
        FakeHttp.reset()
        FakeHttp.routes["$host/subscription"] = FakeHttp.Response(
            body = """{"subscriptionUrl":"https://config.example.invalid/self-hosted"}""")
        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(
            body = """{"enabled":true,"title":"Notice","content":"Content","updatedAt":"today"}""")
        FakeHttp.routes["$host/control"] = FakeHttp.Response(
            body = """{"serviceEnabled":false,"minVersionCode":9999,"updateUrl":"https://update.example.invalid","updateMessage":"Upgrade","disabledMessage":"Disabled"}""")
    }

    @Test fun allEightConfigurationsOnlyContactEnabledServices() = runBlocking {
        for (mask in 0..7) {
            FakeHttp.requests.clear()
            fun endpoint(bit: Int, path: String) =
                if (mask and bit != 0) "https://$host/$path" else ""
            val subscription = api.requestSubscriptionUrl(endpoint(1, "subscription"), "test-invite")
            val announcement = api.fetchAnnouncement(endpoint(2, "announcement"))
            val control = api.fetchAppControl(endpoint(4, "control"))
            assertEquals(mask and 1 != 0, subscription != null)
            assertEquals(mask and 2 != 0, announcement != null)
            assertEquals(mask and 4 != 0, control != null)
            assertEquals(Integer.bitCount(mask), FakeHttp.requests.size)
            assertTrue(FakeHttp.requests.all { it.disconnected })
            FakeHttp.requests.firstOrNull { it.url.path == "/subscription" }?.let {
                assertEquals("POST", it.requestMethod)
                val json = JSONObject(it.body.toString("UTF-8"))
                assertEquals("test-invite", json.getString("inviteCode"))
                assertEquals(100, json.getInt("versionCode"))
                assertEquals("https://config.example.invalid/self-hosted", subscription)
            }
            FakeHttp.requests.filter { it.url.path != "/subscription" }.forEach { assertEquals("GET", it.requestMethod) }
            announcement?.let { assertEquals("Content", it.content) }
            control?.let {
                assertFalse(it.serviceEnabled)
                assertEquals(9999, it.minVersionCode)
                assertEquals("Disabled", it.disabledMessage)
                assertEquals("https://update.example.invalid", it.updateUrl)
            }
        }
    }

    @Test fun repeatedLookupUsesTheLatestServerMappingForTheSameInvite() = runBlocking {
        val endpoint = "https://$host/subscription"
        FakeHttp.routes["$host/subscription"] = FakeHttp.Response(body = """{"subscriptionUrl":"https://config.example.invalid/old"}""")
        assertEquals("https://config.example.invalid/old", api.requestSubscriptionUrl(endpoint, "same-code"))
        FakeHttp.routes["$host/subscription"] = FakeHttp.Response(body = """{"subscriptionUrl":"https://config.example.invalid/new"}""")
        assertEquals("https://config.example.invalid/new", api.requestSubscriptionUrl(endpoint, "same-code"))
        assertEquals(2, FakeHttp.requests.size)
        assertTrue(FakeHttp.requests.all { JSONObject(it.body.toString("UTF-8")).getString("inviteCode") == "same-code" })
    }

    @Test fun serviceErrorAndOptionalServiceFailuresKeepExistingSemantics() = runBlocking {
        for (path in listOf("subscription", "announcement", "control")) {
            FakeHttp.routes["$host/$path"] = FakeHttp.Response(403, """{"message":"Invalid invitation"}""")
        }
        val error = runCatching { api.requestSubscriptionUrl("https://$host/subscription", "bad") }.exceptionOrNull()
        assertTrue(error is CmhrException.ServerRejected)
        assertEquals("Invalid invitation", error?.message)
        assertNull(api.fetchAnnouncement("https://$host/announcement"))
        assertNull(api.fetchAppControl("https://$host/control"))
        assertTrue(FakeHttp.requests.all { it.disconnected })
    }

    @Test fun missingSubscriptionUrlIsAnError() = runBlocking {
        FakeHttp.routes["$host/subscription"] = FakeHttp.Response(body = "{}")
        val error = runCatching { api.requestSubscriptionUrl("https://$host/subscription", "code") }.exceptionOrNull()
        assertTrue(error is CmhrException.SubscriptionUrlMissing)
    }

    @Test fun disabledAnnouncementsAndMalformedOptionalResponsesAreIgnored() = runBlocking {
        FakeHttp.routes["$host/announcement"] = FakeHttp.Response(body = """{"enabled":false,"content":"Hidden"}""")
        assertNull(api.fetchAnnouncement("https://$host/announcement"))
        FakeHttp.routes["$host/control"] = FakeHttp.Response(body = "not json")
        assertNull(api.fetchAppControl("https://$host/control"))
    }

    @Test fun subscriptionFetchReturnsBodyAndUsageHeader() = runBlocking {
        FakeHttp.routes["config.example.invalid/sub"] = FakeHttp.Response(
            body = "dmxlc3M6Ly8=",
            headers = mapOf("Subscription-Userinfo" to "upload=1; download=2; total=10; expire=0"),
        )
        val payload = api.fetchSubscription("https://config.example.invalid/sub")
        assertEquals("dmxlc3M6Ly8=", payload.body)
        assertEquals("upload=1; download=2; total=10; expire=0", payload.userinfo)
        assertEquals("GET", FakeHttp.requests.single().requestMethod)
        assertTrue(FakeHttp.requests.single().disconnected)
    }
}
