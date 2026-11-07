package io.canavar.vakit.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import io.canavar.vakit.VakitApp
import io.canavar.vakit.core.launchSafely
import io.canavar.vakit.domain.District
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DistrictPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DistrictPickerRoute()
            }
        }
    }
}

@Composable
fun DistrictPickerRoute() {
    val context = LocalContext.current
    val container = (context.applicationContext as VakitApp).container
    val scope = rememberCoroutineScope()
    var districts by remember { mutableStateOf<List<District>>(emptyList()) }
    var province by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        districts = withContext(Dispatchers.IO) { container.timetable.districts() }
    }

    if (province == null) {
        val provinces = districts.map { it.province }.distinct().sorted()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(provinces) { name ->
                Chip(
                    label = { Text(name) },
                    onClick = { province = name },
                )
            }
        }
    } else {
        val selected = province
        val towns = districts.filter { it.province == selected }.sortedBy { it.name }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(towns) { district ->
                Chip(
                    label = { Text(district.name) },
                    onClick = {
                        scope.launchSafely {
                            try {
                                container.settings.setDistrict(district, auto = false)
                                container.scheduleNextEvents()
                                container.complicationRefresher.requestUpdate()
                            } finally {
                                (context as? Activity)?.finish()
                            }
                        }
                    },
                )
            }
        }
    }
}
