package com.varuna.openfuel.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.core.net.Endpoints
import com.varuna.openfuel.ui.LatLon
import com.varuna.openfuel.ui.StationRow
import com.varuna.openfuel.ui.theme.BandColors
import com.varuna.openfuel.util.Format
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val SOURCE_ID = "stations"
private const val LAYER_ID = "stations-symbols"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/**
 * One GeoJSON source, one symbol layer: brand icon plus price, the price
 * coloured by band. `symbol-sort-key` is the price rank, so where symbols
 * collide the cheaper station is the one drawn. No clustering is needed at the
 * ~700 stations of a community.
 */
@Composable
fun StationMap(
    modifier: Modifier,
    rows: List<StationRow>,
    logos: Map<String, ByteArray>,
    location: LatLon?,
    onStationClick: (String) -> Unit,
) {
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var framed by remember { mutableStateOf(false) }
    val clicks by rememberUpdatedState(onStationClick)
    val iconPx = with(LocalDensity.current) { ICON_DP.dp.roundToPx() }

    MapLibreView(modifier, Endpoints.MAP_STYLE) { m, s ->
        installLayer(s)
        m.addOnMapClickListener { point ->
            val screen = m.projection.toScreenLocation(point)
            val id = m.queryRenderedFeatures(screen, LAYER_ID).firstOrNull()?.getStringProperty("id")
            if (id != null) clicks(id)
            id != null
        }
        map = m
        style = s
    }

    LaunchedEffect(style, rows, logos) {
        val s = style ?: return@LaunchedEffect
        val brands = rows.map { it.station.brand }.distinctBy { it.key }
        for (brand in brands) {
            // Re-added when a logo arrives: addImage replaces an image with the same id.
            s.addImage(brand.key, MarkerIcons.forBrand(brand, logos[brand.key], iconPx))
        }
        (s.getSource(SOURCE_ID) as? GeoJsonSource)?.setGeoJson(geoJson(rows))
    }

    LaunchedEffect(map, rows.isNotEmpty(), location) {
        val m = map ?: return@LaunchedEffect
        if (framed) return@LaunchedEffect
        when {
            location != null -> {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.lat, location.lon), 12.0))
                framed = true
            }
            rows.size >= 2 -> {
                val bounds = LatLngBounds.Builder().apply {
                    rows.forEach { include(LatLng(it.station.lat, it.station.lon)) }
                }.build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                framed = true
            }
        }
    }
}

private const val ICON_DP = 34

private fun installLayer(style: Style) {
    if (style.getSource(SOURCE_ID) == null) style.addSource(GeoJsonSource(SOURCE_ID, EMPTY))
    if (style.getLayer(LAYER_ID) != null) return
    style.addLayer(
        SymbolLayer(LAYER_ID, SOURCE_ID).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(false),
            PropertyFactory.textField(Expression.get("label")),
            // OpenFreeMap serves Noto Sans glyphs; without this MapLibre asks for
            // its default font stack and the prices may not render at all.
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.9f)),
            PropertyFactory.textColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.6f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.symbolSortKey(Expression.get("sortKey")),
        ),
    )
}

private fun geoJson(rows: List<StationRow>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        for (row in rows) {
            addJsonObject {
                put("type", "Feature")
                putJsonObject("geometry") {
                    put("type", "Point")
                    putJsonArray("coordinates") {
                        add(row.station.lon)
                        add(row.station.lat)
                    }
                }
                putJsonObject("properties") {
                    put("id", row.station.id)
                    put("icon", row.station.brand.key)
                    put("label", Format.price(row.effective.price))
                    put("color", BandColors.hex(row.band))
                    put("sortKey", row.rank)
                }
            }
        }
    }
}.toString()
