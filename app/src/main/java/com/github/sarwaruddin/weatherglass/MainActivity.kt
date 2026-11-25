package com.github.sarwaruddin.weatherglass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.github.sarwaruddin.weatherglass.ui.WeatherglassTheme
import com.github.sarwaruddin.weatherglass.ui.MainViewModel
import com.github.sarwaruddin.weatherglass.ui.WeatherScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.locate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xCC070708.toInt()),
        )
        super.onCreate(savedInstanceState)
        com.github.sarwaruddin.weatherglass.widget.WidgetUpdater.schedule(this)
        setContent {
            WeatherglassTheme {
                val s by vm.state.collectAsState()
                WeatherScreen(
                    s = s,
                    onRefresh = vm::refresh,
                    onSelect = vm::select,
                    onSearch = vm::search,
                    onAdd = vm::add,
                    onRemove = vm::remove,
                    onLocate = ::locate,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resume()
    }

    private fun locate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED) vm.locate()
        else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
