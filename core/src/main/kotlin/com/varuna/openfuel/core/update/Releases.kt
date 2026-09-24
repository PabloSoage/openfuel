package com.varuna.openfuel.core.update

import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.parse.array
import com.varuna.openfuel.core.parse.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * New versions, from the repo's GitHub releases. Same approach as Rustify's
 * updater: tags like `v0.2.0-beta`, one APK per ABI attached to each release.
 */
class Releases(private val http: HttpClient) {

    data class Asset(val name: String, val url: String, val size: Long)

    data class Release(
        val tag: String,
        val title: String,
        val body: String,
        val htmlUrl: String,
        val assets: List<Asset>,
    )

    /** The newest release above the installed version, with the notes of every one missed. */
    data class Update(
        val tag: String,
        val version: String,
        val title: String,
        /** Changelog from the installed version up to [tag], newest first. */
        val body: String,
        val htmlUrl: String,
        /** Null when no attached APK matches the device: the release page is the fallback. */
        val apk: Asset?,
    )

    /**
     * Null when up to date. Throws when GitHub cannot be reached, so the caller
     * can tell "up to date" from "could not check".
     */
    fun check(installed: String, abis: List<String>): Update? {
        // Both endpoints, as in Rustify: the list includes pre-releases but GitHub has
        // served it stale for a day; `latest` is always current but hides pre-releases.
        val listed = runCatching { parseList(get(LIST_API)) }.getOrDefault(emptyList())
        val latest = runCatching { parseRelease(Json.parseToJsonElement(get(LATEST_API)) as JsonObject) }.getOrNull()
        if (listed.isEmpty() && latest == null) error("GitHub releases unavailable")
        return pick(listOfNotNull(latest) + listed, installed, abis)
    }

    private fun get(url: String): String {
        val r = http.get(url, mapOf("Accept" to "application/vnd.github+json"))
        if (!r.isSuccess) error("GitHub HTTP ${r.code}")
        return r.text()
    }

    companion object {
        const val OWNER = "PabloSoage"
        const val REPO = "openfuel"
        const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"
        const val LIST_API = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"
        const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

        private val VERSION = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

        /** [major, minor, patch] from "v0.2.0-beta", "0.2.0" or "0.2"; empty when there is none. */
        fun parseVersion(s: String): List<Int> {
            val m = VERSION.find(s) ?: return emptyList()
            return listOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt())
        }

        fun isNewer(candidate: String, installed: String): Boolean {
            val a = parseVersion(candidate)
            val b = parseVersion(installed)
            if (a.isEmpty() || b.isEmpty()) return false
            return compare(a, b) > 0
        }

        private fun compare(a: List<Int>, b: List<Int>): Int =
            a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: 0

        fun parseList(body: String): List<Release> =
            (Json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) null else parseRelease(o)
            }

        fun parseRelease(o: JsonObject): Release? {
            val tag = o.string("tag_name")?.takeIf { it.isNotBlank() } ?: return null
            return Release(
                tag = tag,
                title = o.string("name")?.takeIf { it.isNotBlank() } ?: tag,
                body = o.string("body").orEmpty().trim(),
                htmlUrl = o.string("html_url")?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE,
                assets = o.array("assets").orEmpty().mapNotNull { a ->
                    val ao = a as? JsonObject ?: return@mapNotNull null
                    Asset(
                        name = ao.string("name") ?: return@mapNotNull null,
                        url = ao.string("browser_download_url") ?: return@mapNotNull null,
                        size = (ao["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
                    )
                },
            )
        }

        fun pick(releases: List<Release>, installed: String, abis: List<String>): Update? {
            val missed = releases
                .distinctBy { it.tag }
                .filter { isNewer(it.tag, installed) }
                // By number: as text "0.10.0" would sort below "0.9.0".
                .sortedWith { x, y -> compare(parseVersion(y.tag), parseVersion(x.tag)) }
            val newest = missed.firstOrNull() ?: return null
            return Update(
                tag = newest.tag,
                version = parseVersion(newest.tag).joinToString("."),
                title = newest.title,
                body = if (missed.size == 1) newest.body else missed.joinToString("\n\n") { "## ${it.title}\n\n${it.body}".trim() },
                htmlUrl = newest.htmlUrl,
                apk = apkFor(newest.assets, abis),
            )
        }

        /** The APK named after the device's first supported ABI, else one with no ABI in its name. */
        fun apkFor(assets: List<Asset>, abis: List<String>): Asset? {
            val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            abis.forEach { abi -> apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it } }
            return apks.firstOrNull { a -> KNOWN_ABIS.none { a.name.contains(it, ignoreCase = true) } }
        }

        private val KNOWN_ABIS = listOf("arm64-v8a", "armeabi", "x86_64", "x86")
    }
}
