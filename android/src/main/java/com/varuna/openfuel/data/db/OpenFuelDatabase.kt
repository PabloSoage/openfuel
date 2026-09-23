package com.varuna.openfuel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StationEntity::class,
        CurrentPriceEntity::class,
        HistoryPriceEntity::class,
        HistoryFetchEntity::class,
        BrandLogoEntity::class,
        DiscountPlanEntity::class,
        StationPlanEntity::class,
        StationPlansFetchEntity::class,
        FavouriteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OpenFuelDatabase : RoomDatabase() {
    abstract fun stations(): StationDao
    abstract fun prices(): PriceDao
    abstract fun logos(): LogoDao
    abstract fun plans(): PlanDao
    abstract fun favourites(): FavouriteDao

    companion object {
        fun build(context: Context): OpenFuelDatabase =
            Room.databaseBuilder(context.applicationContext, OpenFuelDatabase::class.java, "openfuel.db")
                // Everything here can be downloaded again except favourites and
                // owned plans (the latter live in DataStore). Until version 2
                // exists there is nothing to migrate; after that, write migrations.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
