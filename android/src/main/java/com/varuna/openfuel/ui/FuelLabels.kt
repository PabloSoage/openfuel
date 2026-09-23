package com.varuna.openfuel.ui

import androidx.annotation.StringRes
import com.varuna.openfuel.R
import com.varuna.openfuel.core.model.Fuel

@StringRes
fun fuelLabel(fuel: Fuel): Int = when (fuel) {
    Fuel.GOA -> R.string.fuel_goa
    Fuel.GOA_PREMIUM -> R.string.fuel_goa_premium
    Fuel.G95E5 -> R.string.fuel_g95e5
    Fuel.G95E10 -> R.string.fuel_g95e10
    Fuel.G95E5_PREMIUM -> R.string.fuel_g95e5_premium
    Fuel.G98E5 -> R.string.fuel_g98e5
    Fuel.G98E10 -> R.string.fuel_g98e10
    Fuel.BIODIESEL -> R.string.fuel_biodiesel
    Fuel.GOB -> R.string.fuel_gob
    Fuel.GLP -> R.string.fuel_glp
    Fuel.GNC -> R.string.fuel_gnc
    Fuel.GNL -> R.string.fuel_gnl
    Fuel.DIESEL_RENOVABLE -> R.string.fuel_diesel_renovable
    Fuel.ADBLUE -> R.string.fuel_adblue
    Fuel.G95E85 -> R.string.fuel_g95e85
    Fuel.BIOETANOL -> R.string.fuel_bioetanol
    Fuel.HIDROGENO -> R.string.fuel_hidrogeno
    Fuel.G95E25 -> R.string.fuel_g95e25
    Fuel.GASOLINA_RENOVABLE -> R.string.fuel_gasolina_renovable
    Fuel.BIOGAS_GNC -> R.string.fuel_biogas_gnc
    Fuel.BIOGAS_GNL -> R.string.fuel_biogas_gnl
}
