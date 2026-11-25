package com.github.sarwaruddin.weatherglass.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "0.2.0 (2)" from PackageManager (BuildConfig generation is off in this project). */
private fun versionOf(context: Context): String = try {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    "${info.versionName ?: "?"} ($code)"
} catch (_: Exception) {
    "unknown"
}

private class Source(val name: String, val what: String, val licence: String, val url: String)

private val SOURCES = listOf(
    Source(
        "Open-Meteo", "Forecasts from ECMWF, DWD ICON, NOAA GFS, ECCC GEM and JMA models; geocoding.",
        "CC BY 4.0", "https://open-meteo.com/",
    ),
    Source(
        "CAMS", "Air quality: Copernicus Atmosphere Monitoring Service, served by Open-Meteo.",
        "Copernicus licence · CC BY 4.0 via Open-Meteo", "https://atmosphere.copernicus.eu/",
    ),
    Source(
        "ERA5", "Climate history since 1940: ECMWF reanalysis, Copernicus Climate Change Service, served by Open-Meteo.",
        "Copernicus licence · CC BY 4.0 via Open-Meteo", "https://climate.copernicus.eu/",
    ),
    Source(
        "Weatherglass", "This app is free software: read, change and share the source code.",
        "GNU GPL v3.0", "https://github.com/sarwar-uddin/weatherglass",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember { versionOf(context) }
    val uri = LocalUriHandler.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pal.Card,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Weatherglass",
                    style = Type.Big.copy(fontSize = 34.sp, fontWeight = FontWeight.Thin, letterSpacing = 1.sp),
                    modifier = Modifier.semantics { heading() },
                )
                Text("READ THE AIR.", style = Type.Title.copy(color = Pal.Crimson, letterSpacing = 3.sp))
                Spacer(Modifier.height(6.dp))
                Text("Version $version", style = Type.Small)
            }

            Section("Data sources")
            for (s in SOURCES) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "Open ${s.name} website", role = Role.Button) { runCatching { uri.openUri(s.url) } }
                        .padding(vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = Type.Body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
                        Text("›", style = Type.Body.copy(color = Pal.Sub))
                    }
                    Text(s.what, style = Type.Small)
                    Text(s.licence, style = Type.Small.copy(color = Pal.Silver))
                }
            }
            Text(
                "Forecast data © Open-Meteo.com, licensed CC BY 4.0. Contains modified Copernicus " +
                    "Atmosphere Monitoring Service and Copernicus Climate Change Service information. " +
                    "Neither the European Commission nor ECMWF is responsible for any use of it.",
                style = Type.Small, modifier = Modifier.padding(top = 4.dp),
            )

            Section("Privacy")
            Para("No accounts, no ads, no trackers, no analytics.")
            Para(
                "Coordinates are rounded to two decimals (about 1 km) before they leave the phone, " +
                    "and are sent only to Open-Meteo to fetch the weather. Saved places stay on this device.",
            )

            Section("Not a warning service")
            Para(
                "Weatherglass shows what forecast models say, and models can be wrong. It is not an official " +
                    "warning service. For cyclone, flood and heat warnings, follow the Bangladesh " +
                    "Meteorological Department (BMD) or your national met service.",
            )

            Section("Open source")
            Para("Weatherglass is free, open-source software. No premium tier holds back the useful parts.")

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title.uppercase(), style = Type.Title, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Para(text: String) {
    Text(text, style = Type.Body.copy(fontSize = 14.sp, color = Pal.Silver), modifier = Modifier.padding(vertical = 3.dp))
}
