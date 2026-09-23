package com.varuna.openfuel.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.varuna.openfuel.BuildConfig
import com.varuna.openfuel.R
import com.varuna.openfuel.core.discount.DiscountKind
import com.varuna.openfuel.data.SettingsStore
import com.varuna.openfuel.ui.MainViewModel
import com.varuna.openfuel.ui.RegionNames
import com.varuna.openfuel.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(ui: UiState, vm: MainViewModel, onBack: () -> Unit) {
    val settings = ui.settings ?: return
    var pickingRegion by remember { mutableStateOf(false) }
    var brandsOpen by remember { mutableStateOf(false) }

    if (pickingRegion) {
        RegionPicker(
            initial = settings.region,
            onConfirm = { vm.setRegion(it); pickingRegion = false },
            onCancel = { pickingRegion = false },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(stringResource(R.string.settings_region))
            Text(settings.region?.let { RegionNames.summary(it, stringResource(R.string.region_all_spain)) }.orEmpty())
            OutlinedButton(onClick = { pickingRegion = true }) { Text(stringResource(R.string.action_change)) }

            HorizontalDivider()
            Section(stringResource(R.string.settings_history))
            Choices(SettingsStore.HISTORY_CHOICES, settings.historyDays, { if (it == 0) stringResource(R.string.history_choice_off) else stringResource(R.string.days_n, it) }) {
                vm.setHistoryDays(it)
            }
            Text(stringResource(R.string.settings_history_explain), style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Section(stringResource(R.string.settings_radius))
            Choices(SettingsStore.RADIUS_CHOICES, settings.radiusKm, { stringResource(R.string.distance_km, it.toString()) }) {
                vm.setRadiusKm(it)
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_plans))
            if (ui.planCatalog.isEmpty()) {
                Text(stringResource(R.string.settings_plans_empty), style = MaterialTheme.typography.bodySmall)
            } else {
                ui.planCatalog.groupBy { it.operatorName }.forEach { (operator, plans) ->
                    Text(operator, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                    plans.forEach { plan ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = plan.id in settings.ownedPlans,
                                onCheckedChange = { vm.setPlanOwned(plan.id, it) },
                                enabled = plan.appliesToEveryone && plan.kind != DiscountKind.OTHER,
                            )
                            Text(plan.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_brands))
            OutlinedButton(onClick = { brandsOpen = true }) {
                Text(stringResource(R.string.settings_brands_hidden, settings.hiddenBrands.size))
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_language))
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            Choices(listOf("", "en", "es"), current, {
                when (it) {
                    "" -> stringResource(R.string.language_system)
                    "en" -> "English"
                    else -> "Español"
                }
            }) { tag ->
                AppCompatDelegate.setApplicationLocales(
                    if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                )
            }

            HorizontalDivider()
            Section(stringResource(R.string.settings_about))
            Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_attribution), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.about_tax_schedule, ui.schedule?.version ?: 0, ui.schedule?.updated.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }

    if (brandsOpen) {
        BrandFilterDialog(
            brands = ui.brands,
            onApply = { vm.setHiddenBrands(it); brandsOpen = false },
            onDismiss = { brandsOpen = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}
