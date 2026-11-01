package io.canavar.vakit.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.VakitApp
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.location.LocationScheduler
import kotlinx.coroutines.launch

@Composable
fun StatusRoute() {
    val context = LocalContext.current
    val container = (context.applicationContext as VakitApp).container
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<StatusUiState?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    val hasLocation =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    val hasBackground = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(reload) {
        state = container.loadStatus(hasLocation, hasBackground)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || coarse) LocationScheduler.runNow(context)
        reload++
    }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { reload++ }

    LaunchedEffect(Unit) {
        if (!hasLocation) {
            val settings = container.settings.read()
            if (!settings.permissionAsked) {
                container.settings.setPermissionAsked()
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    StatusScreen(
        state = state,
        onRefreshLocation = {
            LocationScheduler.runNow(context)
            container.complicationRefresher.requestUpdate()
            reload++
        },
        onPickDistrict = {
            pickLauncher.launch(Intent(context, DistrictPickerActivity::class.java))
        },
        onGrantLocation = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onUseGps = {
            scope.launch {
                container.settings.setAutoLocation(true)
                LocationScheduler.runNow(context)
                reload++
            }
        },
    )
}

@Composable
fun StatusScreen(
    state: StatusUiState?,
    onRefreshLocation: () -> Unit,
    onPickDistrict: () -> Unit,
    onGrantLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseGps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state == null) {
            Text("Yükleniyor…")
            return@Column
        }
        Text("Vakit", style = MaterialTheme.typography.title3)
        Text(state.districtName ?: "İlçe seçilmedi")
        state.province
            ?.takeIf { it != state.districtName }
            ?.let { Text(it) }
        state.next?.let {
            Text("Sonraki: ${it.prayer.displayName} ${TimeFormat.hhmm(it.at)}")
        }
        Spacer(Modifier.height(8.dp))
        state.today.forEach { (prayer, time) ->
            Text("${prayer.displayName}: $time")
        }
        state.lastCheckAt?.let { Text("Son kontrol: ${minutesAgo(it)} dk önce") }
        state.lastFixAt?.let { Text("Son konum: ${minutesAgo(it)} dk önce") }
        if (state.locationBlocked) {
            Text("Konum alınamadı")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefreshLocation) { Text("Konumu yenile") }
        Button(onClick = onPickDistrict) { Text("İlçe seç") }
        if (!state.hasLocationPermission) {
            Button(onClick = onGrantLocation) { Text("İzin ver") }
        } else if (!state.hasBackgroundPermission) {
            Button(onClick = onOpenSettings) { Text("Arka plan izni") }
        }
        if (!state.autoLocation) {
            Button(onClick = onUseGps) { Text("GPS kullan") }
        }
    }
}

private fun minutesAgo(epochMs: Long): Long =
    ((System.currentTimeMillis() - epochMs) / 60_000).coerceAtLeast(0)
