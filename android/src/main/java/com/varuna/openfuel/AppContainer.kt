package com.varuna.openfuel

import android.content.Context
import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.net.FaviconFetcher
import com.varuna.openfuel.core.net.GeoportalApi
import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.net.JdkHttpClient
import com.varuna.openfuel.core.net.OfficialApi
import com.varuna.openfuel.core.search.Nominatim
import com.varuna.openfuel.data.LogoResolver
import com.varuna.openfuel.data.OpenFuelRepository
import com.varuna.openfuel.data.SettingsStore
import com.varuna.openfuel.data.TaxScheduleProvider
import com.varuna.openfuel.data.db.OpenFuelDatabase

/** Hand-wired dependencies: a handful of objects do not justify a DI framework. */
class AppContainer(context: Context) {
    val catalog: BrandCatalog = BrandCatalog.default
    val http: HttpClient = JdkHttpClient(USER_AGENT)
    val database: OpenFuelDatabase = OpenFuelDatabase.build(context)
    val settings = SettingsStore(context.applicationContext)
    // Optional enrichment: a slow geoportal must not keep a spinner up for long.
    private val geoportal = GeoportalApi(JdkHttpClient(USER_AGENT, connectTimeoutMs = 5_000, readTimeoutMs = 8_000))
    val repository = OpenFuelRepository(database, settings, OfficialApi(http, catalog), geoportal, catalog)
    val taxSchedules = TaxScheduleProvider(http, settings)
    val logos = LogoResolver(database, geoportal, FaviconFetcher(http), http, catalog)
    /** Address search; its usage policy requires this identifying User-Agent. */
    val nominatim = Nominatim(http)

    companion object {
        val USER_AGENT = "openfuel/${BuildConfig.VERSION_NAME} (+https://github.com/PabloSoage/openfuel)"
    }
}
