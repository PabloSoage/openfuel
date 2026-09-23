package com.varuna.openfuel.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// SQLite on API 26 allows at most 999 bound variables per statement. No query
// here binds a list of stations: lists are of provinces (at most 52) and
// station sets are expressed as sub-queries.

@Dao
interface StationDao {
    @Query("SELECT * FROM station")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("SELECT * FROM station WHERE id = :id")
    suspend fun byId(id: String): StationEntity?

    @Query("SELECT * FROM station WHERE brandKey = :brandKey LIMIT :limit")
    suspend fun byBrand(brandKey: String, limit: Int): List<StationEntity>

    @Query("SELECT DISTINCT brandKey FROM station")
    suspend fun brandKeys(): List<String>

    @Query("SELECT id FROM station")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(stations: List<StationEntity>)

    @Query("DELETE FROM station WHERE provinceId IN (:provinceIds)")
    suspend fun deleteInProvinces(provinceIds: List<String>)

    @Query("DELETE FROM station WHERE provinceId NOT IN (:provinceIds)")
    suspend fun deleteOutsideProvinces(provinceIds: List<String>)
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM current_price")
    fun observeCurrent(): Flow<List<CurrentPriceEntity>>

    @Query("SELECT * FROM current_price WHERE stationId = :stationId")
    suspend fun currentFor(stationId: String): List<CurrentPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrent(prices: List<CurrentPriceEntity>)

    @Query("DELETE FROM current_price WHERE stationId IN (SELECT id FROM station WHERE provinceId IN (:provinceIds))")
    suspend fun deleteCurrentInProvinces(provinceIds: List<String>)

    @Query("DELETE FROM current_price WHERE stationId NOT IN (SELECT id FROM station)")
    suspend fun deleteOrphanCurrent()

    @Query("SELECT * FROM history_price WHERE stationId = :stationId AND fuel = :fuel AND epochDay >= :fromDay ORDER BY epochDay")
    suspend fun history(stationId: String, fuel: String, fromDay: Long): List<HistoryPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(prices: List<HistoryPriceEntity>)

    @Query("SELECT epochDay FROM history_fetch WHERE provinceId = :provinceId AND fuel = :fuel AND epochDay >= :fromDay")
    suspend fun fetchedDays(provinceId: String, fuel: String, fromDay: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFetch(fetch: HistoryFetchEntity)
}

@Dao
interface LogoDao {
    @Query("SELECT * FROM brand_logo")
    fun observeAll(): Flow<List<BrandLogoEntity>>

    @Query("SELECT * FROM brand_logo WHERE brandKey = :brandKey")
    suspend fun byKey(brandKey: String): BrandLogoEntity?

    @Upsert
    suspend fun upsert(logo: BrandLogoEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM discount_plan ORDER BY operatorName, name")
    fun observeCatalog(): Flow<List<DiscountPlanEntity>>

    @Query("SELECT * FROM station_plan")
    fun observeStationPlans(): Flow<List<StationPlanEntity>>

    @Query("SELECT p.* FROM discount_plan p JOIN station_plan s ON s.planId = p.id WHERE s.stationId = :stationId ORDER BY p.name")
    suspend fun plansFor(stationId: String): List<DiscountPlanEntity>

    @Query("SELECT fetchedAt FROM station_plans_fetch WHERE stationId = :stationId")
    suspend fun fetchedAt(stationId: String): Long?

    @Upsert
    suspend fun upsertPlans(plans: List<DiscountPlanEntity>)

    @Query("DELETE FROM station_plan WHERE stationId = :stationId")
    suspend fun clearStation(stationId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStationPlans(links: List<StationPlanEntity>)

    @Upsert
    suspend fun markFetched(fetch: StationPlansFetchEntity)
}

@Dao
interface FavouriteDao {
    @Query("SELECT stationId FROM favourite")
    fun observeIds(): Flow<List<String>>

    @Upsert
    suspend fun add(favourite: FavouriteEntity)

    @Query("DELETE FROM favourite WHERE stationId = :stationId")
    suspend fun remove(stationId: String)
}
