package com.alan.semaforosmx.ui.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.TrafficLight
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Mapa de semáforos con OpenStreetMap (osmdroid).
 *
 * Sin llave de API ni facturación: los mosaicos vienen de OpenStreetMap.
 * - Centro inicial: Monterrey, N.L. (25.6866, -100.3161).
 * - Marcadores: círculos rojos con borde blanco, uno por semáforo
 *   (igual que los circleMarkers del prototipo web de Alan en Leaflet).
 *   La base semilla son los 1 579 semáforos del GeoJSON de OSM.
 * - Toque prolongado: agregar un semáforo nuevo en esa ubicación.
 * - Mi ubicación: punto azul con seguimiento opcional.
 */
@Composable
fun TrafficLightMap(
    lights: List<TrafficLight>,
    onMapLongPress: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        createMapView(context, onMapLongPress)
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            refreshMarkers(view, lights)
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun createMapView(
    context: Context,
    onMapLongPress: (Double, Double) -> Unit
): MapView {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    )
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(13.0)
        // Centro de Monterrey.
        controller.setCenter(GeoPoint(25.6866, -100.3161))

        // Toque prolongado para agregar semáforos.
        overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

                override fun longPressHelper(p: GeoPoint?): Boolean {
                    if (p != null) onMapLongPress(p.latitude, p.longitude)
                    return true
                }
            })
        )

        // Mi ubicación.
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
        locationOverlay.enableMyLocation()
        overlays.add(locationOverlay)
    }
}

private fun refreshMarkers(mapView: MapView, lights: List<TrafficLight>) {
    // Quitar marcadores viejos (conservar los demás overlays).
    mapView.overlays.removeAll { it is Marker }
    val redIcon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_marker_red)
    lights.forEach { light ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(light.latitude, light.longitude)
            title = light.name ?: "Semáforo"
            snippet = "Toca para ver detalles"
            icon = redIcon
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(marker)
    }
    mapView.invalidate()
}
