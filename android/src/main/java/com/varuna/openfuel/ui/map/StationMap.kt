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
import com.varuna.openfuel.ui.MapFocus
import com.varuna.openfuel.ui.StationRow
import com.varuna.openfuel.ui.theme.BandColors
import com.varuna.openfuel.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import org.maplibre.android.style.layers.CircleLayer
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
    /** A new value (another region) frames the map again. */
    frameKey: Any?,
    /** A camera request (search, "my location"); each new value is animated to. */
    focus: MapFocus?,
    /** Where the last search landed, drawn as a pin. */
    searchPin: LatLon?,
    onStationClick: (String) -> Unit,
) {
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var framed by remember(frameKey) { mutableStateOf(Framing.NONE) }
    val clicks by rememberUpdatedState(onStationClick)
    val iconPx = with(LocalDensity.current) { ICON_DP.dp.roundToPx() }

    MapLibreView(modifier, Endpoints.MAP_STYLE) { m, s ->
        installLayer(s)
        installOverlays(s)
        m.addOnMapClickListener { point ->
            val screen = m.projection.toScreenLocation(point)
            val id = m.queryRenderedFeatures(screen, LAYER_ID).firstOrNull()?.getStringProperty("id")
            if (id != null) clicks(id)
            id != null
        }
        map = m
        style = s
    }

    // Icons only change when a brand appears or a logo arrives, not on every
    // price or plan update; addImage replaces an image with the same id.
    val brands = remember(rows) { rows.map { it.station.brand }.distinctBy { it.key } }
    val brandKeys = remember(brands) { brands.map { it.key }.toSet() }
    LaunchedEffect(style, brandKeys, logos) {
        val s = style ?: return@LaunchedEffect
        val icons = withContext(Dispatchers.Default) { brands.associate { it.key to MarkerIcons.forBrand(it, logos[it.key], iconPx) } }
        icons.forEach { (key, bitmap) -> s.addImage(key, bitmap) }
    }
    LaunchedEffect(style, rows) {
        val s = style ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { geoJson(rows) }
        (s.getSource(SOURCE_ID) as? GeoJsonSource)?.setGeoJson(json)
    }

    // Framed once per region: on the user when they are inside it, otherwise
    // on the whole region. A location that arrives after the region framing
    // still wins, once.
    LaunchedEffect(map, rows.isNotEmpty(), location, frameKey) {
        val m = map ?: return@LaunchedEffect
        if (framed == Framing.LOCATION || rows.size < 2) return@LaunchedEffect
        // newLatLngBounds needs the view's size; before layout it frames the world.
        var waited = 0
        while ((m.width <= 0f || m.height <= 0f) && waited < 40) {
            delay(50)
            waited++
        }
        val bounds = LatLngBounds.Builder().apply {
            rows.forEach { include(LatLng(it.station.lat, it.station.lon)) }
        }.build()
        val here = location?.let { LatLng(it.lat, it.lon) }
        when {
            here != null && bounds.contains(here) -> {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(here, 12.0))
                framed = Framing.LOCATION
            }
            framed == Framing.NONE -> {
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                framed = Framing.REGION
            }
        }
    }

    // Search and "my location" move the camera; the dot and the pin follow their state.
    LaunchedEffect(map, focus) {
        val m = map ?: return@LaunchedEffect
        val f = focus ?: return@LaunchedEffect
        framed = Framing.LOCATION // a place the user asked for beats any automatic framing
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lon), f.zoom))
    }
    LaunchedEffect(style, location) {
        (style?.getSource(ME_SOURCE) as? GeoJsonSource)?.setGeoJson(location?.let { point(it) } ?: EMPTY)
    }
    LaunchedEffect(style, searchPin) {
        (style?.getSource(PIN_SOURCE) as? GeoJsonSource)?.setGeoJson(searchPin?.let { point(it) } ?: EMPTY)
    }
}

private const val ME_SOURCE = "me"
private const val PIN_SOURCE = "search-pin"

private fun point(p: LatLon): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.lon},${p.lat}]},"properties":{}}]}"""

/** The user's position and the search pin, in the style Rustify uses for its own dots. */
private fun installOverlays(style: Style) {
    if (style.getSource(PIN_SOURCE) == null) {
        style.addSource(GeoJsonSource(PIN_SOURCE, EMPTY))
        style.addLayer(
            CircleLayer("search-pin-layer", PIN_SOURCE).withProperties(
                PropertyFactory.circleColor("#C62828"),
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }
    if (style.getSource(ME_SOURCE) == null) {
        style.addSource(GeoJsonSource(ME_SOURCE, EMPTY))
        style.addLayer(
            CircleLayer("me-layer", ME_SOURCE).withProperties(
                PropertyFactory.circleColor("#1E88E5"),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
            ),
        )
    }
}

private enum class Framing { NONE, REGION, LOCATION }

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
