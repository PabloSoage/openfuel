package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.core.search.Place
import com.varuna.openfuel.ui.RegionNames
import com.varuna.openfuel.ui.SearchState

/**
 * Town, postcode, address or station. The downloaded stations answer while
 * the user types, with no network; OpenStreetMap only when they submit.
 */
@Composable
fun SearchOverlay(
    state: SearchState,
    onQuery: (String) -> Unit,
    onSubmit: (language: String) -> Unit,
    onPick: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val language = LocalConfiguration.current.locales[0].language
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        onSubmit(language)
                    }),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            if (state.query.trim().length >= 2) {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (state.local.isNotEmpty()) {
                        item { Header(stringResource(R.string.search_local_header)) }
                        items(state.local) { place -> PlaceRow(place, onPick) }
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        when {
                            state.remoteLoading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.search_osm_loading), style = MaterialTheme.typography.bodySmall)
                            }
                            state.remote == null -> TextButton(onClick = { keyboard?.hide(); onSubmit(language) }) {
                                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.search_osm, state.query.trim()))
                            }
                            state.remoteFailed -> Text(stringResource(R.string.search_osm_failed), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                            state.remote.isEmpty() -> Text(stringResource(R.string.search_osm_none), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                            else -> Header(stringResource(R.string.search_osm_header))
                        }
                    }
                    state.remote?.takeIf { !state.remoteFailed }?.let { remote -> items(remote) { place -> PlaceRow(place, onPick) } }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun PlaceRow(place: Place, onPick: (Place) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onPick(place) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (place.kind == Place.Kind.STATION) Icons.Filled.LocalGasStation else Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyMedium)
            if (place.detail.isNotBlank()) Text(place.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "This place is outside the region you downloaded" with a one-tap fix. */
@Composable
fun OutsideRegionCard(place: Place, onAdd: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val province = place.provinceId?.let { RegionNames.province(it) } ?: return
    Surface(modifier.fillMaxWidth().padding(12.dp), tonalElevation = 4.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.outside_region, place.name), style = MaterialTheme.typography.bodyMedium)
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                TextButton(onClick = onAdd) { Text(stringResource(R.string.action_add_province, province)) }
            }
        }
    }
}
