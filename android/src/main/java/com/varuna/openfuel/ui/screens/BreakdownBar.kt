package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.core.fiscal.FiscalBreakdown
import com.varuna.openfuel.util.Format

private val VatColor = Color(0xFF5C6BC0)
private val ExciseColor = Color(0xFF8E24AA)
private val RestColor = Color(0xFF26A69A)

/** VAT · excise · rest of one litre, with the legal source of the rates. */
@Composable
fun BreakdownBar(breakdown: FiscalBreakdown) {
    when (breakdown) {
        is FiscalBreakdown.Unavailable -> Text(
            stringResource(
                when (breakdown.reason) {
                    FiscalBreakdown.Reason.TERRITORY -> R.string.breakdown_territory
                    FiscalBreakdown.Reason.PRODUCT -> R.string.breakdown_product
                    FiscalBreakdown.Reason.NO_PERIOD -> R.string.breakdown_no_period
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        is FiscalBreakdown.Available -> Column {
            Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp))) {
                Box(Modifier.weight(breakdown.vat.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(VatColor))
                Box(Modifier.weight(breakdown.excise.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(ExciseColor))
                Box(Modifier.weight(breakdown.rest.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(RestColor))
            }
            Legend(VatColor, stringResource(R.string.breakdown_vat, Format.percent(breakdown.vatRate)), breakdown.vat, breakdown.vat / breakdown.price)
            Legend(ExciseColor, stringResource(R.string.breakdown_excise), breakdown.excise, breakdown.excise / breakdown.price)
            Legend(RestColor, stringResource(R.string.breakdown_rest), breakdown.rest, breakdown.rest / breakdown.price)
            Text(
                stringResource(R.string.breakdown_taxes_total, Format.euros(breakdown.taxes), Format.percent(breakdown.taxShare)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!breakdown.period.confirmed) {
                Text(
                    stringResource(R.string.breakdown_unconfirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.breakdown_source, breakdown.period.source),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, value: Double, share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp).weight(1f))
        Text(Format.euros(value), style = MaterialTheme.typography.bodySmall)
        Text(Format.percent(share), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp).padding(start = 8.dp))
    }
}
