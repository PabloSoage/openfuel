package com.varuna.openfuel.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.varuna.openfuel.ui.PriceBand

private val Amber = Color(0xFFE8913A)

private val LightColors = lightColorScheme(primary = Color(0xFFB4530A), secondary = Amber)
private val DarkColors = darkColorScheme(primary = Amber, secondary = Color(0xFFB4530A))

@Composable
fun OpenFuelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Colours of the price bands, identical in light and dark so the map reads the same. */
object BandColors {
    val cheap = Color(0xFF1B8A3A)
    val mid = Color(0xFFB7791F)
    val dear = Color(0xFFC62828)

    fun of(band: PriceBand): Color = when (band) {
        PriceBand.CHEAP -> cheap
        PriceBand.MID -> mid
        PriceBand.DEAR -> dear
    }

    fun hex(band: PriceBand): String = when (band) {
        PriceBand.CHEAP -> "#1B8A3A"
        PriceBand.MID -> "#B7791F"
        PriceBand.DEAR -> "#C62828"
    }
}
