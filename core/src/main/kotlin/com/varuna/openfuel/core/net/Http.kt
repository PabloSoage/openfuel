package com.varuna.openfuel.core.net

import java.net.HttpURLConnection
import java.net.URI

class HttpResponse(val code: Int, val body: ByteArray, val contentType: String?) {
    val isSuccess: Boolean get() = code in 200..299

    fun text(): String = body.toString(Charsets.UTF_8)
}

/** The only door to the network, so tests can replace it with a fake. */
fun interface HttpClient {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

/**
 * `HttpURLConnection`, deliberately: no dependency, and on Android it already
 * negotiates and decompresses gzip on its own.
 */
class JdkHttpClient(
    private val userAgent: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpClient {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return HttpResponse(code, bytes, connection.contentType)
        } finally {
            connection.disconnect()
        }
    }
}
