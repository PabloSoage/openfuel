package com.varuna.openfuel.core.update

import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.net.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReleasesTest {

    private fun release(tag: String, name: String, body: String, vararg apks: String) = """
        {"tag_name":"$tag","name":"$name","body":"$body","draft":false,
         "html_url":"https://github.com/PabloSoage/openfuel/releases/tag/$tag",
         "assets":[${apks.joinToString(",") { """{"name":"$it","browser_download_url":"https://example.org/$tag/$it","size":15000000}""" }}]}
    """.trimIndent()

    private val list = "[" + listOf(
        release("v0.2.0-beta", "Beta v0.2.0", "Search.", "android-arm64-v8a-release.apk", "android-x86_64-release.apk"),
        release("v0.1.0-beta", "Beta v0.1.0", "First.", "android-arm64-v8a-release.apk", "android-x86_64-release.apk"),
    ).joinToString(",") + "]"

    private fun client(listBody: String?, latestBody: String?) = HttpClient { url, _ ->
        val body = if (url == Releases.LIST_API) listBody else latestBody
        if (body == null) HttpResponse(503, ByteArray(0), null) else HttpResponse(200, body.toByteArray(), "application/json")
    }

    @Test fun `versions compare by number, not as text`() {
        assertEquals(listOf(0, 2, 0), Releases.parseVersion("v0.2.0-beta"))
        assertEquals(listOf(1, 3, 0), Releases.parseVersion("1.3"))
        assertTrue(Releases.isNewer("v0.10.0-beta", "0.9.0"))
        assertTrue(Releases.isNewer("v0.2.0-beta", "0.1.0"))
        assertFalse(Releases.isNewer("v0.1.0-beta", "0.1.0"))
        assertFalse(Releases.isNewer("nightly", "0.1.0"))
    }

    @Test fun `the newest missed release, with every missed changelog`() {
        val update = Releases(client(list, null)).check("0.0.9", listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("v0.2.0-beta", update.tag)
        assertEquals("0.2.0", update.version)
        assertEquals("android-arm64-v8a-release.apk", update.apk?.name)
        assertTrue(update.body.startsWith("## Beta v0.2.0\n\nSearch."))
        assertTrue("## Beta v0.1.0" in update.body)
    }

    @Test fun `one missed release keeps its notes as they are`() {
        val update = Releases(client(list, null)).check("0.1.0", listOf("x86_64"))!!
        assertEquals("Search.", update.body)
        assertEquals("android-x86_64-release.apk", update.apk?.name)
    }

    @Test fun `up to date is null`() {
        assertNull(Releases(client(list, null)).check("0.2.0", listOf("arm64-v8a")))
    }

    /** GitHub's list has lagged a whole day behind `latest` (seen in Rustify). */
    @Test fun `latest counts even when the list is stale or down`() {
        val latest = release("v0.3.0-beta", "Beta v0.3.0", "Later.", "android-arm64-v8a-release.apk")
        assertEquals("v0.3.0-beta", Releases(client(list, latest)).check("0.2.0", listOf("arm64-v8a"))?.tag)
        assertEquals("v0.3.0-beta", Releases(client(null, latest)).check("0.2.0", listOf("arm64-v8a"))?.tag)
    }

    @Test fun `no answer at all is an error, not up to date`() {
        try {
            Releases(client(null, null)).check("0.1.0", listOf("arm64-v8a"))
            fail("expected an exception")
        } catch (_: IllegalStateException) {
        }
    }

    @Test fun `no APK for this ABI falls back to the release page`() {
        val update = Releases(client(list, null)).check("0.1.0", listOf("armeabi-v7a"))!!
        assertNull(update.apk)
        assertEquals("https://github.com/PabloSoage/openfuel/releases/tag/v0.2.0-beta", update.htmlUrl)
    }
}
