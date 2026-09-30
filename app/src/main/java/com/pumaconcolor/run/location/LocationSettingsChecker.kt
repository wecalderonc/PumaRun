package com.pumaconcolor.run.location

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

sealed interface LocationSettingsResult {
    data object Satisfied : LocationSettingsResult
    data class Resolvable(val resolution: PendingIntent) : LocationSettingsResult
    data object Unavailable : LocationSettingsResult
}

object LocationSettingsChecker {

    suspend fun check(context: Context): LocationSettingsResult {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, FusedLocationSource.UPDATE_INTERVAL_MS).build()
            )
            .setAlwaysShow(true)
            .build()
        return try {
            LocationServices.getSettingsClient(context).checkLocationSettings(request).await()
            LocationSettingsResult.Satisfied
        } catch (e: ResolvableApiException) {
            LocationSettingsResult.Resolvable(e.resolution)
        } catch (e: Exception) {
            LocationSettingsResult.Unavailable
        }
    }
}
