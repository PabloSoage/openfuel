package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.ui.StationRow
import com.varuna.openfuel.ui.UiState
import com.varuna.openfuel.ui.theme.BandColors
import com.varuna.openfuel.util.Format

/** Phase 2: the same stations as the map, sorted by effective price or distance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationList(ui: UiState, onOpen: (String) -> Unit) {
    var byDistance by rememberSaveable { mutableStateOf(false) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }

    val rows = ui.rows
        .filter { !favouritesOnly || it.favourite }
        .let { list ->
            if (byDistance && ui.location != null) list.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
            else list.sortedBy { it.effective.price }
        }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            FilterChip(
                selected = !byDistance,
                onClick = { byDistance = false },
                label = { Text(stringResource(R.string.sort_price)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = byDistance,
                onClick = { byDistance = true },
                enabled = ui.location != null,
                label = { Text(stringResource(R.string.sort_distance)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = favouritesOnly,
                onClick = { favouritesOnly = !favouritesOnly },
                label = { Text(stringResource(R.string.filter_favourites)) },
            )
        }
        if (rows.isEmpty()) {
            Text(stringResource(R.string.list_empty), modifier = Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            items(rows, key = { it.station.id }) { row ->
                StationListRow(row, ui.logos[row.station.brand.key], onOpen)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StationListRow(row: StationRow, logo: ByteArray?, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(row.station.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandIcon(row.station.brand, logo, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.station.sign.ifBlank { row.station.brand.displayName }, style = MaterialTheme.typography.bodyLarge)
                if (row.favourite) Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.padding(start = 4.dp))
            }
            val place = listOfNotNull(
                row.station.locality.takeIf { it.isNotBlank() },
                row.distanceKm?.let { stringResource(R.string.distance_km, Format.km(it)) },
            ).joinToString(" · ")
            Text(place, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Format.price(row.effective.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BandColors.of(row.band),
            )
            if (row.effective.plan != null) {
                Text(
                    stringResource(R.string.list_pump_price, Format.price(row.price)),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

