package com.varuna.openfuel.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.varuna.openfuel.R
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.ui.LatLon
import com.varuna.openfuel.ui.MainViewModel
import com.varuna.openfuel.ui.RegionNames
import com.varuna.openfuel.ui.UiState
import com.varuna.openfuel.ui.fuelLabel
import com.varuna.openfuel.ui.map.StationMap
import com.varuna.openfuel.util.DeviceLocation
import kotlinx.coroutines.launch

private enum class Screen { MAP, LIST, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val locationUnavailable = stringResource(R.string.location_unavailable)
    val locationDenied = stringResource(R.string.location_denied)
    /** "My location": a fix, then the map goes there; a toast when there is none. */
    val centreOnUser: () -> Unit = {
        scope.launch {
            val fix = DeviceLocation.current(context)
            if (fix != null) vm.focusOnUser(fix.latitude, fix.longitude)
            else Toast.makeText(context, locationUnavailable, Toast.LENGTH_SHORT).show()
        }
    }
    var centreAfterGrant by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionGranted = result.values.any { it }
        if (centreAfterGrant) {
            centreAfterGrant = false
            if (permissionGranted) centreOnUser() else Toast.makeText(context, locationDenied, Toast.LENGTH_LONG).show()
        }
    }
    val hadPermission = remember { DeviceLocation.hasPermission(context) }
    LaunchedEffect(Unit) {
        if (!DeviceLocation.hasPermission(context)) permissions.launch(DeviceLocation.PERMISSIONS)
    }
    LaunchedEffect(hadPermission || permissionGranted) {
        if (hadPermission || permissionGranted) DeviceLocation.current(context)?.let { vm.setLocation(it.latitude, it.longitude) }
    }

    val settings = ui.settings
    if (settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (settings.region == null) {
        RegionPicker(initial = null, onConfirm = vm::setRegion, onCancel = null)
        return
    }

    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var brandsOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val focus by vm.focus.collectAsStateWithLifecycle()
    val searchPin by vm.searchPin.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val outside by vm.outside.collectAsStateWithLifecycle()

    if (screen == Screen.SETTINGS) {
        BackHandler { screen = Screen.MAP }
        SettingsScreen(ui = ui, vm = vm, onBack = { screen = Screen.MAP })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { FuelSelector(settings.fuel, onSelect = vm::setFuel) },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) vm.clearSearch() }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = { brandsOpen = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.action_brands))
                    }
                    IconButton(onClick = { vm.refresh(force = true) }, enabled = !ui.refreshing) {
                        if (ui.refreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                    if (screen == Screen.MAP) {
                        IconButton(onClick = { screen = Screen.LIST }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.action_list))
                        }
                    } else {
                        IconButton(onClick = { screen = Screen.MAP }) {
                            Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.action_map))
                        }
                    }
                    IconButton(onClick = { screen = Screen.SETTINGS }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.LIST -> StationList(ui = ui, onOpen = { vm.openStation(it) })
                else -> StationMap(
                    modifier = Modifier.fillMaxSize(),
                    rows = ui.rows,
                    logos = ui.logos,
                    location = ui.location,
                    frameKey = settings.region,
                    focus = focus,
                    searchPin = searchPin?.let { LatLon(it.lat, it.lon) },
                    onStationClick = { vm.openStation(it) },
                )
            }
            if (screen == Screen.MAP) {
                SmallFloatingActionButton(
                    onClick = {
                        if (DeviceLocation.hasPermission(context)) centreOnUser()
                        else { centreAfterGrant = true; permissions.launch(DeviceLocation.PERMISSIONS) }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 44.dp),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.action_my_location))
                }
            }
            outside?.let { place ->
                OutsideRegionCard(
                    place = place,
                    onAdd = { vm.addProvinceOf(place) },
                    onDismiss = vm::dismissOutside,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                )
            }
            if (searchOpen) {
                BackHandler { searchOpen = false; vm.clearSearch() }
                SearchOverlay(
                    state = search,
                    onQuery = vm::setSearchQuery,
                    onSubmit = vm::searchRemote,
                    onPick = { place ->
                        searchOpen = false
                        screen = Screen.MAP
                        vm.pickPlace(place)
                        vm.clearSearch()
                    },
                    onClose = { searchOpen = false; vm.clearSearch() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            StatusLine(ui, Modifier.align(Alignment.BottomCenter))
        }
    }

    detail?.let { StationSheet(detail = it, ui = ui, vm = vm) }

    if (brandsOpen) {
        BrandFilterDialog(
            brands = ui.brands,
            onApply = { hidden -> vm.setHiddenBrands(hidden); brandsOpen = false },
            onDismiss = { brandsOpen = false },
        )
    }
}

@Composable
private fun FuelSelector(fuel: Fuel, onSelect: (Fuel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(stringResource(fuelLabel(fuel)), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.action_choose_fuel))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Fuel.pickable.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(fuelLabel(option))) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(ui: UiState, modifier: Modifier) {
    val settings = ui.settings ?: return
    val region = settings.region?.let { RegionNames.summary(it, stringResource(R.string.region_all_spain)) }.orEmpty()
    val text = when {
        ui.refreshFailed && settings.publishedAt != null ->
            stringResource(R.string.status_offline, settings.publishedAt)
        ui.refreshFailed -> stringResource(R.string.status_error)
        settings.publishedAt != null ->
            stringResource(R.string.status_prices_of, settings.publishedAt, region, ui.rows.size)
        else -> stringResource(R.string.status_loading)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (ui.refreshFailed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
