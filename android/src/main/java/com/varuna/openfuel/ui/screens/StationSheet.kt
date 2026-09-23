package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.R
import com.varuna.openfuel.core.analysis.Nearby
import com.varuna.openfuel.core.brand.FuelProduct
import com.varuna.openfuel.core.brand.FuelProducts
import com.varuna.openfuel.core.discount.DiscountKind
import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.discount.EffectivePriceCalculator
import com.varuna.openfuel.core.fiscal.FiscalBreakdown
import com.varuna.openfuel.core.geo.Geo
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.ui.MainViewModel
import com.varuna.openfuel.ui.StationDetail
import com.varuna.openfuel.ui.UiState
import com.varuna.openfuel.ui.fuelLabel
import com.varuna.openfuel.util.Format
import com.varuna.openfuel.util.Intents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationSheet(detail: StationDetail, ui: UiState, vm: MainViewModel) {
    val settings = ui.settings ?: return
    val station = detail.station
    val fuel = settings.fuel
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val favourite = station.id in ui.favourites
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val products = FuelProducts.default

    // The stations around this one for the selected fuel, by the price the user pays.
    val nearby = remember(ui.rows, station.id, settings.radiusKm) {
        val effective = ui.rows.associate { it.station.id to it.effective.price }
        Nearby.around(station, settings.radiusKm.toDouble(), ui.rows.map { it.station }) { effective[it.id] }
    }
    var nearbyOpen by rememberSaveable(station.id) { mutableStateOf(false) }
    var expandedFuels by rememberSaveable(station.id) { mutableStateOf(setOf(fuel.name)) }

    ModalBottomSheet(onDismissRequest = vm::closeStation, sheetState = sheetState) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandIcon(station.brand, ui.logos[station.brand.key], size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(station.sign.ifBlank { station.brand.displayName }, style = MaterialTheme.typography.titleLarge)
                        val distance = ui.location?.let { Geo.distanceKm(it.lat, it.lon, station.lat, station.lon) }
                        val sub = listOfNotNull(
                            station.brand.displayName.takeUnless { station.brand.independent },
                            distance?.let { stringResource(R.string.distance_km, Format.km(it)) },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { vm.setFavourite(station.id, !favourite) }) {
                        Icon(
                            if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(if (favourite) R.string.action_unfavourite else R.string.action_favourite),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(station.address, style = MaterialTheme.typography.bodyMedium)
                Text(listOf(station.postalCode, station.locality).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.bodyMedium)
                if (station.schedule.isNotBlank()) {
                    Text(stringResource(R.string.station_hours, station.schedule), style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    AssistChip(
                        onClick = { Intents.openInMaps(context, station.lat, station.lon) },
                        label = { Text(stringResource(R.string.action_open_in_maps)) },
                        leadingIcon = { Icon(Icons.Filled.Directions, contentDescription = null) },
                    )
                    val shareText = stringResource(
                        R.string.share_text,
                        station.sign.ifBlank { station.brand.displayName },
                        station.address,
                        station.prices[fuel]?.let { Format.price(it) } ?: "—",
                        stringResource(fuelLabel(fuel)),
                        Intents.mapsUrl(station.lat, station.lon),
                    )
                    AssistChip(
                        onClick = { Intents.share(context, shareText) },
                        label = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                    )
                }
                HorizontalDivider()
            }

            item { SectionTitle(stringResource(R.string.section_prices)) }
            val ordered = station.prices.entries.sortedWith(compareBy({ it.key != fuel }, { it.key.ordinal }))
            items(ordered, key = { it.key.name }) { (f, price) ->
                val product = products.find(station.brand.key, f)
                val expanded = f.name in expandedFuels
                PriceRow(
                    fuel = f,
                    price = price,
                    highlighted = f == fuel,
                    product = product,
                    expanded = expanded,
                    onToggle = { expandedFuels = if (expanded) expandedFuels - f.name else expandedFuels + f.name },
                )
                if (f == fuel) {
                    val effective = EffectivePriceCalculator.best(price, detail.plans, settings.ownedPlans)
                    effective.plan?.let { plan ->
                        Text(
                            stringResource(R.string.effective_price, Format.price(effective.price), plan.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                if (expanded && f.taxCategory != null) {
                    ProductClaims(product, products.baseline(language), language) { Intents.openUrl(context, it) }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.section_breakdown, stringResource(fuelLabel(fuel))))
                val price = station.prices[fuel]
                val schedule = ui.schedule
                if (price != null && schedule != null) {
                    BreakdownBar(FiscalBreakdown.of(price, fuel, MainViewModel.today(), station.territory, schedule))
                } else {
                    Text(stringResource(R.string.breakdown_no_price), style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                val lines = buildList {
                    detail.relative?.let { r ->
                        add(
                            // A tie with the cheapest (0.0 c/l) reads as "the cheapest".
                            if (r.cheapest.id == station.id || r.centsAboveCheapest < 0.05) stringResource(R.string.margin_cheapest, settings.radiusKm, r.compared)
                            else stringResource(R.string.margin_above_cheapest, Format.cents(r.centsAboveCheapest), settings.radiusKm, r.compared),
                        )
                    }
                    detail.ownAverage?.let { a ->
                        add(
                            if (a.centsVsAverage <= 0) stringResource(R.string.vs_average_below, Format.cents(-a.centsVsAverage), a.days)
                            else stringResource(R.string.vs_average_above, Format.cents(a.centsVsAverage), a.days),
                        )
                    }
                }
                if (lines.isNotEmpty() || nearby.size > 1) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SectionTitle(stringResource(R.string.section_compare))
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                    if (nearby.size > 1) {
                        TextButton(onClick = { nearbyOpen = !nearbyOpen }) {
                            Text(
                                if (nearbyOpen) stringResource(R.string.nearby_hide)
                                else stringResource(R.string.nearby_show, nearby.size, settings.radiusKm),
                            )
                        }
                    }
                }
            }
            if (nearbyOpen) {
                val own = nearby.firstOrNull { it.station.id == station.id }?.price
                items(nearby, key = { "near-" + it.station.id }) { entry ->
                    NearbyRow(entry, own, isThis = entry.station.id == station.id, logo = ui.logos[entry.station.brand.key]) {
                        vm.openStation(entry.station.id)
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.section_discounts))
                when {
                    detail.plansLoading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    detail.plansFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.discounts_failed), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::retryPlans) { Text(stringResource(R.string.action_retry)) }
                    }
                    detail.plans.isEmpty() -> Text(stringResource(R.string.discounts_none), style = MaterialTheme.typography.bodySmall)
                    else -> detail.plans.forEach { plan ->
                        PlanRow(plan, owned = plan.id in settings.ownedPlans, onOwned = { vm.setPlanOwned(plan.id, it) })
                    }
                }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.section_history, settings.historyDays))
                when {
                    settings.historyDays == 0 -> Text(stringResource(R.string.history_off), style = MaterialTheme.typography.bodySmall)
                    detail.historyLoading -> CircularProgressIndicator(Modifier.padding(8.dp))
                    detail.history.size < 2 -> Text(stringResource(R.string.history_none), style = MaterialTheme.typography.bodySmall)
                    else -> HistoryChart(detail.history, Modifier.fillMaxWidth().height(160.dp))
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun PriceRow(fuel: Fuel, price: Double, highlighted: Boolean, product: FuelProduct?, expanded: Boolean, onToggle: () -> Unit) {
    val weight = if (highlighted) FontWeight.Bold else FontWeight.Normal
    // Only fuels with a tax category are petrol or diesel, the ones brands make claims about.
    val expandable = fuel.taxCategory != null
    Row(
        Modifier.fillMaxWidth().let { if (expandable) it.clickable(onClick = onToggle) else it }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(fuelLabel(fuel)), fontWeight = weight)
            product?.let {
                val premium = if (it.premium) " · " + stringResource(R.string.product_premium) else ""
                Text(it.name + premium, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.price_per_litre, Format.price(price)), fontWeight = weight)
        if (expandable) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** What the brand says about this fuel, with its source; or the legal baseline when nothing was researched. */
@Composable
private fun ProductClaims(product: FuelProduct?, baseline: String, language: String, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp)) {
        if (product == null) {
            Text(stringResource(R.string.product_none), style = MaterialTheme.typography.bodySmall)
        } else {
            product.claimsIn(language).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.product_claims_note, Format.day(product.checked) + "/" + product.checked.year),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.product_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onOpen(product.source) }.padding(4.dp),
                )
            }
        }
        Text(baseline, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NearbyRow(entry: Nearby.Entry, ownPrice: Double?, isThis: Boolean, logo: ByteArray?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isThis, onClick = onOpen).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandIcon(entry.station.brand, logo, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.station.sign.ifBlank { entry.station.brand.displayName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isThis) FontWeight.Bold else FontWeight.Normal,
            )
            val where = listOf(entry.station.locality, if (isThis) "" else stringResource(R.string.distance_km, Format.km(entry.distanceKm)))
                .filter { it.isNotBlank() }.joinToString(" · ")
            Text(where, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.price(entry.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            val delta = when {
                isThis -> stringResource(R.string.nearby_this)
                ownPrice != null && kotlin.math.abs(entry.price - ownPrice) < 0.0005 -> stringResource(R.string.nearby_same)
                ownPrice != null && entry.price > ownPrice -> stringResource(R.string.nearby_delta, Format.cents((entry.price - ownPrice) * 100))
                ownPrice != null -> "−" + stringResource(R.string.discount_cents, Format.cents((ownPrice - entry.price) * 100))
                else -> ""
            }
            if (delta.isNotEmpty()) Text(delta, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlanRow(plan: DiscountPlan, owned: Boolean, onOwned: (Boolean) -> Unit) {
    val appliable = plan.appliesToEveryone && plan.kind != DiscountKind.OTHER
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = owned, onCheckedChange = onOwned, enabled = appliable)
        Column(Modifier.weight(1f)) {
            Text(plan.name, style = MaterialTheme.typography.bodyMedium)
            val amount = when (plan.kind) {
                DiscountKind.PERCENT -> stringResource(R.string.discount_percent, Format.cents(plan.amount))
                DiscountKind.CENTS_PER_LITRE -> stringResource(R.string.discount_cents, Format.cents(plan.amount))
                DiscountKind.OTHER -> plan.kindLabel
            }
            val audience = if (plan.appliesToEveryone) "" else " · " + plan.audienceLabel
            Text(amount + audience, style = MaterialTheme.typography.bodySmall)
            if (!appliable) Text(stringResource(R.string.discount_not_applied), style = MaterialTheme.typography.bodySmall)
        }
    }
}
