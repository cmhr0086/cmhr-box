package com.cmhr.box.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class Announcement(
    val title: String,
    val content: String,
    val updatedAt: String,
)

data class AppControl(
    val serviceEnabled: Boolean,
    val minVersionCode: Int,
    val updateUrl: String,
    val updateMessage: String,
    val disabledMessage: String,
)

/** Subscription body plus the raw `subscription-userinfo` header (null when absent). */
data class SubscriptionPayload(
    val body: String,
    val userinfo: String?,
)

/**
 * Worker protocol, ported from 1.x PrivateSubscriptionManager. A blank endpoint disables that
 * call: optional services return null without opening a connection.
 */
class CmhrApi(private val versionCode: Int) {

    /** POST {inviteCode, versionCode} → subscriptionUrl. Non-2xx throws with the server message. */
    suspend fun requestSubscriptionUrl(endpoint: String, inviteCode: String): String? {
        if (endpoint.isBlank()) return null
        return withContext(Dispatchers.IO) {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                useCaches = false
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val body = JSONObject()
                    .put("inviteCode", inviteCode)
                    .put("versionCode", versionCode)
                    .toString()
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(body)
                }
                val responseText = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText().orEmpty()
                }
                val json = runCatching { JSONObject(responseText.takeIf { it.isNotBlank() } ?: "{}") }
                    .getOrDefault(JSONObject())
                if (connection.responseCode !in 200..299) {
                    throw CmhrException.ServerRejected(
                        json.optString("message").takeIf { it.isNotBlank() }
                            ?: "HTTP ${connection.responseCode}"
                    )
                }
                json.optString("subscriptionUrl")
                    .takeIf { it.isNotBlank() }
                    ?: throw CmhrException.SubscriptionUrlMissing()
            } finally {
                connection.disconnect()
            }
        }
    }

    /** GET the subscription itself, so the usage header stays readable. */
    suspend fun fetchSubscription(url: String): SubscriptionPayload = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            useCaches = false
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw CmhrException.ServerRejected("HTTP $code")
            SubscriptionPayload(
                body = connection.inputStream.bufferedReader().readText(),
                userinfo = connection.getHeaderField("subscription-userinfo"),
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchAnnouncement(endpoint: String): Announcement? {
        if (endpoint.isBlank()) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode !in 200..299) return@withContext null
                    val responseText = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText.takeIf { it.isNotBlank() } ?: "{}")
                    if (!json.optBoolean("enabled", false)) return@withContext null
                    val content = json.optString("content").trim()
                    if (content.isBlank()) return@withContext null
                    Announcement(
                        title = json.optString("title").trim(),
                        content = content,
                        updatedAt = json.optString("updatedAt").trim(),
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }.getOrNull()
    }

    suspend fun fetchAppControl(endpoint: String): AppControl? {
        if (endpoint.isBlank()) return null
        return runCatching {
            withContext(Dispatchers.IO) {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    if (connection.responseCode !in 200..299) return@withContext null
                    val json = JSONObject(connection.inputStream.bufferedReader().readText())
                    AppControl(
                        serviceEnabled = json.optBoolean("serviceEnabled", true),
                        minVersionCode = json.optInt("minVersionCode", 0).coerceAtLeast(0),
                        updateUrl = json.optString("updateUrl").trim(),
                        updateMessage = json.optString("updateMessage").trim(),
                        disabledMessage = json.optString("disabledMessage").trim(),
                    )
                } finally {
                    connection.disconnect()
                }
            }
        }.getOrNull()
    }
}
