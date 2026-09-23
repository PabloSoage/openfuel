package com.varuna.openfuel.core.net

import com.varuna.openfuel.core.parse.GeoportalParser
import java.net.URI

/**
 * Second step of the logo cascade: the brand's own site. PNG only, because
 * Android's BitmapFactory does not reliably decode .ico and cannot decode SVG.
 */
class FaviconFetcher(private val http: HttpClient) {

    fun pngFor(host: String): ByteArray? {
        val base = "https://$host/"
        fetchPng(base + "apple-touch-icon.png")?.let { return it }
        val html = runCatching { http.get(base, mapOf("Accept" to "text/html")) }.getOrNull()
            ?.takeIf { it.isSuccess }?.text() ?: return null
        for (href in iconLinks(html)) {
            val absolute = runCatching { URI(base).resolve(href).toString() }.getOrNull() ?: continue
            fetchPng(absolute)?.let { return it }
        }
        return null
    }

    private fun fetchPng(url: String): ByteArray? = runCatching {
        http.get(url, mapOf("Accept" to "image/png"))
    }.getOrNull()?.takeIf { it.isSuccess && GeoportalParser.isPng(it.body) }?.body

    companion object {
        private val LINK = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val REL = Regex("rel\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        private val HREF = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        /** `href`s of apple-touch-icon / icon links pointing at a .png, apple-touch-icon first. */
        fun iconLinks(html: String): List<String> {
            val found = LINK.findAll(html).mapNotNull { m ->
                val tag = m.value
                val rel = REL.find(tag)?.groupValues?.get(1)?.lowercase() ?: return@mapNotNull null
                val href = HREF.find(tag)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!href.substringBefore('?').lowercase().endsWith(".png")) return@mapNotNull null
                when {
                    "apple-touch-icon" in rel -> 0 to href
                    rel.split(' ').contains("icon") -> 1 to href
                    else -> null
                }
            }
            return found.sortedBy { it.first }.map { it.second }.toList()
        }
    }
}
