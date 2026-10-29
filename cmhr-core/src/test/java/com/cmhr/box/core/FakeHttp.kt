package com.cmhr.box.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler

/**
 * In-process HTTPS for tests, ported from 1.x PrivateServiceRequestsTest. The stream handler
 * factory can be installed once per JVM, so every test class shares this object.
 */
object FakeHttp {
    class Response(val code: Int = 200, val body: String, val headers: Map<String, String> = emptyMap())

    val requests = mutableListOf<FakeConnection>()

    /** Keyed by "host/path". */
    val routes = mutableMapOf<String, Response>()

    init {
        URL.setURLStreamHandlerFactory { protocol ->
            if (protocol != "https") null else object : URLStreamHandler() {
                override fun openConnection(url: URL): HttpURLConnection {
                    check(url.host.endsWith(".example.invalid")) { "External network access forbidden in tests" }
                    return FakeConnection(url).also { requests.add(it) }
                }
            }
        }
    }

    fun reset() {
        requests.clear()
        routes.clear()
    }

    class FakeConnection(url: URL) : HttpURLConnection(url) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        private val response: Response
            get() = routes["${url.host}${url.path}"] ?: error("Unexpected test URL ${url.host}${url.path}")

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = response.code
        override fun getOutputStream() = body
        override fun getErrorStream() = getInputStream()
        override fun getInputStream() = response.body.byteInputStream()
        override fun getHeaderField(name: String): String? =
            response.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}
