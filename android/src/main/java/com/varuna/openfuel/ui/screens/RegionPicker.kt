package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.model.Regions
import com.varuna.openfuel.ui.RegionNames

private enum class Mode { COMMUNITIES, PROVINCES, ALL }

/**
 * First-run and settings region picker. Communities download ~0.2–2 MB,
 * provinces less; all of Spain is 12 MB and says so.
 */
@Composable
fun RegionPicker(initial: RegionSelection?, onConfirm: (RegionSelection) -> Unit, onCancel: (() -> Unit)?) {
    var mode by remember {
        mutableStateOf(
            when (initial) {
                RegionSelection.AllSpain -> Mode.ALL
                is RegionSelection.Provinces -> Mode.PROVINCES
                else -> Mode.COMMUNITIES
            },
        )
    }
    var communities by remember { mutableStateOf((initial as? RegionSelection.Communities)?.ids ?: emptySet()) }
    var provinces by remember { mutableStateOf((initial as? RegionSelection.Provinces)?.ids ?: emptySet()) }

    Surface(Modifier.fillMaxSize()) {
        // Edge-to-edge: keep the title off the status bar and the button off the gesture bar.
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Text(stringResource(R.string.region_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.region_explain), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            ModeRow(mode == Mode.COMMUNITIES, stringResource(R.string.region_mode_communities)) { mode = Mode.COMMUNITIES }
            ModeRow(mode == Mode.PROVINCES, stringResource(R.string.region_mode_provinces)) { mode = Mode.PROVINCES }
            ModeRow(mode == Mode.ALL, stringResource(R.string.region_mode_all)) { mode = Mode.ALL }

            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                when (mode) {
                    Mode.COMMUNITIES -> items(Regions.communities, key = { it.id }) { c ->
                        CheckRow(c.id in communities, RegionNames.community(c.id)) {
                            communities = if (c.id in communities) communities - c.id else communities + c.id
                        }
                    }
                    Mode.PROVINCES -> items(Regions.provinces.sortedBy { RegionNames.province(it) }, key = { it.id }) { p ->
                        CheckRow(p.id in provinces, "${RegionNames.province(p)} · ${RegionNames.community(p.communityId)}") {
                            provinces = if (p.id in provinces) provinces - p.id else provinces + p.id
                        }
                    }
                    Mode.ALL -> item {
                        Text(stringResource(R.string.region_all_warning), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val selection: RegionSelection? = when (mode) {
                Mode.ALL -> RegionSelection.AllSpain
                Mode.COMMUNITIES -> communities.takeIf { it.isNotEmpty() }?.let { RegionSelection.Communities(it) }
                Mode.PROVINCES -> provinces.takeIf { it.isNotEmpty() }?.let { RegionSelection.Provinces(it) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { selection?.let(onConfirm) }, enabled = selection != null) {
                    Text(stringResource(R.string.action_download))
                }
            }
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}
