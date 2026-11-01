package io.canavar.vakit.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.canavar.vakit.data.LocationFix
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FusedLocationSource(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun current(): LocationFix? {
        if (!hasLocationPermission()) return null
        val token = CancellationTokenSource()
        val fresh = try {
            withTimeoutOrNull(45_000) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    token.token,
                ).await()
            }
        } catch (error: Exception) {
            null
        }
        val location = fresh ?: try {
            client.lastLocation.await()
        } catch (error: Exception) {
            null
        }
        return location?.let {
            LocationFix(it.latitude, it.longitude, System.currentTimeMillis())
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
}
