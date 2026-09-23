package com.varuna.openfuel.data

import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.discount.DiscountKind
import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Station
import com.varuna.openfuel.data.db.CurrentPriceEntity
import com.varuna.openfuel.data.db.DiscountPlanEntity
import com.varuna.openfuel.data.db.StationEntity

fun Station.toEntity() = StationEntity(
    id = id, brandKey = brand.key, sign = sign, address = address, locality = locality,
    municipality = municipality, province = province, provinceId = provinceId, ccaaId = ccaaId,
    postalCode = postalCode, lat = lat, lon = lon, schedule = schedule, roadSide = roadSide,
)

fun Station.priceEntities(): List<CurrentPriceEntity> =
    prices.map { (fuel, price) -> CurrentPriceEntity(id, fuel.name, price) }

fun StationEntity.toStation(prices: List<CurrentPriceEntity>, catalog: BrandCatalog) = Station(
    id = id, sign = sign, brand = catalog.fromKey(brandKey, sign), address = address, locality = locality,
    municipality = municipality, province = province, provinceId = provinceId, ccaaId = ccaaId,
    postalCode = postalCode, lat = lat, lon = lon, schedule = schedule, roadSide = roadSide,
    prices = prices.mapNotNull { p -> Fuel.fromName(p.fuel)?.let { it to p.price } }.toMap(),
)

fun DiscountPlan.toEntity() = DiscountPlanEntity(
    id = id, name = name, description = description, amount = amount, kind = kind.name, kindLabel = kindLabel,
    audienceId = audienceId, audienceLabel = audienceLabel, operatorId = operatorId, operatorName = operatorName,
)

fun DiscountPlanEntity.toPlan() = DiscountPlan(
    id = id, name = name, description = description, amount = amount,
    kind = runCatching { DiscountKind.valueOf(kind) }.getOrDefault(DiscountKind.OTHER), kindLabel = kindLabel,
    audienceId = audienceId, audienceLabel = audienceLabel, operatorId = operatorId, operatorName = operatorName,
)
