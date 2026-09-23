package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.varuna.openfuel.ui.BrandFilterItem

@Composable
fun BrandFilterDialog(brands: List<BrandFilterItem>, onApply: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    var hidden by remember { mutableStateOf(brands.filter { it.hidden }.map { it.filterKey }.toSet()) }
    val independent = stringResource(R.string.brand_independent)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brands_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(brands, key = { it.filterKey }) { brand ->
                    val visible = brand.filterKey !in hidden
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            hidden = if (visible) hidden + brand.filterKey else hidden - brand.filterKey
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = visible,
                            onCheckedChange = { checked ->
                                hidden = if (checked) hidden - brand.filterKey else hidden + brand.filterKey
                            },
                        )
                        Text("${brand.name.ifBlank { independent }} (${brand.stations})")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(hidden) }) { Text(stringResource(R.string.action_apply)) } },
        dismissButton = {
            TextButton(onClick = { hidden = emptySet() }) { Text(stringResource(R.string.action_show_all)) }
        },
    )
}
