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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.VakitApp
import io.canavar.vakit.core.launchSafely
import io.canavar.vakit.domain.TimeFormat
import io.canavar.vakit.location.LocationScheduler

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

    val storedSettings by container.settings.changes.collectAsState(initial = null)

    LaunchedEffect(reload, storedSettings) {
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
    val exactAlarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        scope.launchSafely { container.scheduleNextReminder() }
        reload++
    }

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
                ),
            )
        },
        onGrantExactAlarms = {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                exactAlarmLauncher.launch(intent)
            } catch (_: Exception) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        },
        onUseGps = {
            scope.launchSafely {
                container.settings.setAutoLocation(true)
                container.settings.clearDistrict()
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
    onGrantExactAlarms: () -> Unit,
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
        Chip(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Konumu yenile") },
            onClick = onRefreshLocation,
        )
        Chip(
            modifier = Modifier.fillMaxWidth(),
            label = { Text("İlçe seç") },
            onClick = onPickDistrict,
        )
        if (!state.hasLocationPermission) {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("İzin ver") },
                onClick = onGrantLocation,
            )
        } else if (!state.hasBackgroundPermission) {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Arka plan izni") },
                onClick = onOpenSettings,
            )
        }
        if (!state.hasExactAlarmPermission && state.districtName != null) {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tam hatırlatma izni") },
                onClick = onGrantExactAlarms,
            )
        }
        if (!state.autoLocation) {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("GPS kullan") },
                onClick = onUseGps,
            )
        }
    }
}

private fun minutesAgo(epochMs: Long): Long =
    ((System.currentTimeMillis() - epochMs) / 60_000).coerceAtLeast(0)
