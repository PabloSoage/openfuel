package com.varuna.openfuel.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "station")
data class StationEntity(
    @PrimaryKey val id: String,
    val brandKey: String,
    val sign: String,
    val address: String,
    val locality: String,
    val municipality: String,
    val province: String,
    val provinceId: String,
    val ccaaId: String,
    val postalCode: String,
    val lat: Double,
    val lon: Double,
    val schedule: String,
    val roadSide: String,
)

/** Today's prices. `fuel` is a [com.varuna.openfuel.core.model.Fuel] name. */
@Entity(tableName = "current_price", primaryKeys = ["stationId", "fuel"])
data class CurrentPriceEntity(
    val stationId: String,
    val fuel: String,
    val price: Double,
)

/** Past days; kept when a station disappears, so its chart survives. */
@Entity(tableName = "history_price", primaryKeys = ["stationId", "fuel", "epochDay"])
data class HistoryPriceEntity(
    val stationId: String,
    val fuel: String,
    val epochDay: Long,
    val price: Double,
)

/** One (province, fuel, day) already downloaded: never asked for twice. */
@Entity(tableName = "history_fetch", primaryKeys = ["provinceId", "fuel", "epochDay"])
data class HistoryFetchEntity(
    val provinceId: String,
    val fuel: String,
    val epochDay: Long,
    val fetchedAt: Long,
    val stationCount: Int,
)

/**
 * The logo cascade's result for one brand. Not a data class: it holds a
 * ByteArray, whose equals() would compare identities.
 */
@Entity(tableName = "brand_logo")
class BrandLogoEntity(
    @PrimaryKey val brandKey: String,
    /** "geoportal", "favicon" or "none". */
    val source: String,
    val png: ByteArray?,
    val checkedAt: Long,
)

@Entity(tableName = "discount_plan")
data class DiscountPlanEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String,
    val amount: Double,
    /** [com.varuna.openfuel.core.discount.DiscountKind] name. */
    val kind: String,
    val kindLabel: String,
    val audienceId: Int?,
    val audienceLabel: String,
    val operatorId: Int?,
    val operatorName: String,
)

@Entity(tableName = "station_plan", primaryKeys = ["stationId", "planId"])
data class StationPlanEntity(
    val stationId: String,
    val planId: Int,
)

@Entity(tableName = "station_plans_fetch")
data class StationPlansFetchEntity(
    @PrimaryKey val stationId: String,
    val fetchedAt: Long,
)

@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey val stationId: String,
    val addedAt: Long,
)
