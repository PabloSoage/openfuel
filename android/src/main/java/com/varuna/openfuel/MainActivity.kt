package com.varuna.openfuel

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.varuna.openfuel.ui.MainViewModel
import com.varuna.openfuel.ui.screens.MainScreen
import com.varuna.openfuel.ui.theme.OpenFuelTheme

/** AppCompatActivity, not ComponentActivity: per-app language on Android < 13 needs AppCompat. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OpenFuelApp).container
        val viewModel = ViewModelProvider(this, MainViewModel.factory(container))[MainViewModel::class.java]
        setContent {
            OpenFuelTheme {
                MainScreen(viewModel)
            }
        }
    }
}
