package com.varuna.openfuel.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Intents {
    // Double.toString always uses '.', which is what the URL needs.
    fun mapsUrl(lat: Double, lon: Double): String = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"

    /** Opens the Google Maps app when installed, the browser otherwise. */
    fun openInMaps(context: Context, lat: Double, lon: Double) = openUrl(context, mapsUrl(lat, lon))

    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
