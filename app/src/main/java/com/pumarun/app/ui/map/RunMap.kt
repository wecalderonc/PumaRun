package com.pumarun.app.ui.map

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pumarun.app.BuildConfig
import com.pumarun.app.R
import com.pumarun.app.domain.LatLon
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

enum class MapCamera { Follow, FitRoute }

/** OpenStreetMap view of [position] and the session [track]. No API key required. */
@Composable
fun RunMap(
    position: LatLon?,
    track: List<List<LatLon>>,
    modifier: Modifier = Modifier,
    camera: MapCamera = MapCamera.Follow,
) {
    val context = LocalContext.current
    var follow by remember(camera) { mutableStateOf(camera == MapCamera.Follow) }
    var ignoreScrollUntil by remember { mutableLongStateOf(0L) }

    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(FOLLOW_ZOOM)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    if (SystemClock.uptimeMillis() > ignoreScrollUntil) follow = false
                    return false
                }

                override fun onZoom(event: ZoomEvent?) = false
            })
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                render(map, context, position, track)
                when {
                    follow && position != null -> {
                        ignoreScrollUntil = SystemClock.uptimeMillis() + PROGRAMMATIC_MS
                        val target = GeoPoint(position.latitude, position.longitude)
                        if (map.zoomLevelDouble < FOLLOW_ZOOM - 1) map.controller.setZoom(FOLLOW_ZOOM)
                        map.controller.setCenter(target)
                    }
                    camera == MapCamera.FitRoute -> {
                        val points = track.flatten().map { GeoPoint(it.latitude, it.longitude) }
                        if (points.size >= 2) {
                            ignoreScrollUntil = SystemClock.uptimeMillis() + PROGRAMMATIC_MS
                            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, FIT_PADDING_PX)
                        }
                    }
                }
                map.invalidate()
            },
        )
        if (position == null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Text(
                    stringResource(R.string.map_waiting),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (!follow && position != null) {
            FilledIconButton(
                onClick = { follow = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_recenter))
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ) {
            Text(
                stringResource(R.string.map_attribution),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun render(map: MapView, context: Context, position: LatLon?, track: List<List<LatLon>>) {
    map.overlays.removeAll { it is Polyline || it is Marker }
    track.forEach { segment ->
        if (segment.size < 2) return@forEach
        map.overlays.add(
            Polyline(map).apply {
                setPoints(segment.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.color = ROUTE_COLOR
                outlinePaint.strokeWidth = ROUTE_WIDTH_PX
            },
        )
    }
    position?.let {
        map.overlays.add(
            Marker(map).apply {
                this.position = GeoPoint(it.latitude, it.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_map_dot)
                infoWindow = null
            },
        )
    }
}

private fun configureOsmdroid(context: Context) {
    val config = Configuration.getInstance()
    config.userAgentValue =
        "PumaRun/${BuildConfig.VERSION_NAME} (+https://github.com/wecalderonc/PumaRun)"
    config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
    config.osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
}

private const val FOLLOW_ZOOM = 17.0
private const val PROGRAMMATIC_MS = 500L
private const val FIT_PADDING_PX = 80
private const val ROUTE_COLOR = 0xFFFC5200.toInt()
private const val ROUTE_WIDTH_PX = 14f
