package com.varuna.openfuel.ui.screens

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.varuna.openfuel.ui.MainViewModel
import com.varuna.openfuel.ui.RegionNames
import com.varuna.openfuel.ui.UiState
import com.varuna.openfuel.ui.fuelLabel
import com.varuna.openfuel.ui.map.StationMap
import com.varuna.openfuel.util.DeviceLocation

private enum class Screen { MAP, LIST, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        DeviceLocation.lastKnown(context)?.let { vm.setLocation(it.latitude, it.longitude) }
    }
    LaunchedEffect(Unit) {
        if (DeviceLocation.hasPermission(context)) {
            DeviceLocation.lastKnown(context)?.let { vm.setLocation(it.latitude, it.longitude) }
        } else {
            permissions.launch(DeviceLocation.PERMISSIONS)
        }
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
                    onStationClick = { vm.openStation(it) },
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
        ui.error != null && settings.publishedAt != null ->
            stringResource(R.string.status_offline, settings.publishedAt)
        ui.error != null -> stringResource(R.string.status_error, ui.error)
        settings.publishedAt != null ->
            stringResource(R.string.status_prices_of, settings.publishedAt, region, ui.rows.size)
        else -> stringResource(R.string.status_loading)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (ui.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
