package com.varuna.openfuel.core.net

import org.junit.Assert.assertEquals
import org.junit.Test

class FaviconFetcherTest {
    @Test fun `png icon links, apple-touch-icon first, svg and ico ignored`() {
        val html = """
            <link rel="icon" href="/favicon.ico">
            <link rel="icon" type="image/png" sizes="32x32" href="/img/icon-32.png?v=2">
            <link rel="mask-icon" href="/safari.svg">
            <LINK REL='apple-touch-icon' HREF='https://cdn.example.com/touch.png'>
            <link rel="stylesheet" href="/a.png">
        """.trimIndent()
        assertEquals(
            listOf("https://cdn.example.com/touch.png", "/img/icon-32.png?v=2"),
            FaviconFetcher.iconLinks(html),
        )
    }
}
