package com.alan.semaforosmx.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.TrafficLight
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.coroutines.resume

/** Zoom al centrar en el usuario: nivel de calle. */
private const val MY_LOCATION_ZOOM = 17.0

/** Duración de la animación de centrado (ms). */
private const val ANIMATION_SPEED_MS = 1000L

/** Tiempo máximo esperando un fix fresco al centrar el mapa (ms). */
private const val FRESH_FIX_TIMEOUT_MS = 12_000L

/**
 * Mapa de semáforos con OpenStreetMap (osmdroid).
 *
 * Sin llave de API ni facturación: los mosaicos vienen de OpenStreetMap.
 * - Centro inicial: Monterrey, N.L. (25.6866, -100.3161).
 * - Marcadores: círculos rojos con borde blanco, uno por semáforo
 *   (igual que los circleMarkers del prototipo web de Alan en Leaflet).
 *   La base semilla son los 1 579 semáforos del GeoJSON de OSM.
 *   Los semáforos SIN VERIFICAR (agregados por usuarios, aún sin 3
 *   confirmaciones) usan marcador GRIS y no sirven alertas.
 * - Toque en un marcador: abre el detalle del semáforo (con la acción
 *   «Ya no existe» si el semáforo fue retirado en la vida real).
 * - Toque prolongado: agregar un semáforo nuevo en esa ubicación.
 * - Mi ubicación: punto azul (solo con permiso de ubicación) + botón
 *   flotante abajo a la derecha, estilo Google Maps, que centra el mapa
 *   en la posición actual con animación (zoom 17).
 */
@Composable
fun TrafficLightMap(
    lights: List<TrafficLight>,
    onMapLongPress: (latitude: Double, longitude: Double) -> Unit,
    onMarkerClick: (TrafficLight) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        createMapView(context, onMapLongPress)
    }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        mapView.onResume()
        // onResume() reintenta activar el overlay de ubicación aunque no
        // haya permiso (osmdroid lo traga y solo registra un error en el
        // log); se sincroniza aquí para dejarlo en el estado correcto.
        syncLocationOverlay(mapView)
        onDispose { mapView.onPause() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { view ->
                refreshMarkers(view, lights, onMarkerClick)
                // El permiso puede concederse con el mapa visible (el
                // diálogo lo pide MainActivity al arrancar): sincronizar
                // el punto azul en cada recomposición.
                syncLocationOverlay(view)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Botón «Mi ubicación» estilo Google Maps: abajo a la derecha del
        // mapa, sobre el panel de botones y el menú inferior.
        FloatingActionButton(
            onClick = {
                if (locating) return@FloatingActionButton
                if (!hasLocationPermission(context)) {
                    // Sin permiso: explicar, no pedir. MainActivity es
                    // quien pide los permisos al arrancar.
                    Toast.makeText(
                        context,
                        R.string.my_location_no_permission,
                        Toast.LENGTH_LONG
                    ).show()
                    return@FloatingActionButton
                }
                locating = true
                scope.launch {
                    val point = fetchLocationForMap(context)
                    locating = false
                    if (point == null) {
                        Toast.makeText(
                            context, R.string.no_gps_fix, Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        mapView.controller.animateTo(
                            point, MY_LOCATION_ZOOM, ANIMATION_SPEED_MS
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            if (locating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = stringResource(R.string.my_location_button)
                )
            }
        }
    }
}

/** ¿La app tiene permiso de ubicación en primer plano (fina o gruesa)? */
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Activa el punto azul de «Mi ubicación» solo con permiso de ubicación.
 * Sin permiso el overlay queda inactivo (no se pide nada desde el mapa:
 * MainActivity es quien pide los permisos).
 */
private fun syncLocationOverlay(mapView: MapView) {
    val overlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>()
        .firstOrNull() ?: return
    if (hasLocationPermission(mapView.context)) {
        if (!overlay.isMyLocationEnabled) overlay.enableMyLocation()
    } else {
        if (overlay.isMyLocationEnabled) overlay.disableMyLocation()
    }
}

/**
 * Ubicación para centrar el mapa: primero la última conocida si es
 * reciente (GPS, si no la red), si no un fix fresco con tiempo límite.
 * Devuelve null si no hay ubicación disponible (sin GPS ni red activos,
 * o sin fix a tiempo): la UI muestra el mensaje.
 *
 * A diferencia de los reportes (GpsVerification), centrar el mapa no
 * exige precisión ≤ 50 m: un fix grueso también sirve para ubicarse.
 * Lo que sí se exige: fix reciente y no simulado (ver
 * [MapLocationPicker]).
 */
@SuppressLint("MissingPermission") // El botón verifica el permiso antes.
@Suppress("DEPRECATION") // isFromMockProvider funciona en todos los API.
private suspend fun fetchLocationForMap(
    context: Context,
    timeoutMs: Long = FRESH_FIX_TIMEOUT_MS
): GeoPoint? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    // 1. Última conocida, si sirve (salto instantáneo, sin encender el GPS).
    val gps = runCatching {
        manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }.getOrNull()
    val network = runCatching {
        manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    }.getOrNull()
    MapLocationPicker.pickLastKnown(
        gps?.toCandidate(), network?.toCandidate()
    )?.let { return GeoPoint(it.latitude, it.longitude) }

    // 2. Sin fix reciente: pedir uno nuevo con tiempo límite. Si ningún
    // proveedor está activo, no hay nada que esperar.
    val gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    if (!gpsEnabled && !networkEnabled) return null
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!location.isFromMockProvider && cont.isActive) {
                        runCatching { manager.removeUpdates(this) }
                        cont.resume(
                            GeoPoint(location.latitude, location.longitude)
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(
                    provider: String?, status: Int, extras: Bundle?
                ) = Unit
            }
            cont.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
            try {
                if (gpsEnabled) {
                    manager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 0L, 0f,
                        listener, Looper.getMainLooper()
                    )
                }
                if (networkEnabled) {
                    manager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0L, 0f,
                        listener, Looper.getMainLooper()
                    )
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

/** Convierte un fix de Android en candidato puro para [MapLocationPicker]. */
@Suppress("DEPRECATION") // isFromMockProvider funciona en todos los API.
private fun Location.toCandidate(): MapLocationPicker.Candidate {
    val ageMillis =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
    return MapLocationPicker.Candidate(
        provider = provider ?: "",
        latitude = latitude,
        longitude = longitude,
        ageMillis = ageMillis,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        isMock = isFromMockProvider
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

        // Mi ubicación: punto azul. Solo se activa con permiso
        // (MainActivity pide los permisos al arrancar); sin permiso el
        // overlay queda inactivo hasta que se conceda (ver
        // syncLocationOverlay, que se corre en cada recomposición).
        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
        if (hasLocationPermission(context)) {
            locationOverlay.enableMyLocation()
        }
        overlays.add(locationOverlay)
    }
}

private fun refreshMarkers(
    mapView: MapView,
    lights: List<TrafficLight>,
    onMarkerClick: (TrafficLight) -> Unit
) {
    // Quitar marcadores viejos (conservar los demás overlays).
    mapView.overlays.removeAll { it is Marker }
    val redIcon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_marker_red)
    val greyIcon = ContextCompat.getDrawable(mapView.context, R.drawable.ic_marker_grey)
    lights.forEach { light ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(light.latitude, light.longitude)
            title = light.name ?: "Semáforo"
            snippet = if (light.status == LightStatus.UNVERIFIED) {
                mapView.context.getString(R.string.light_unverified)
            } else {
                "Toca para ver detalles"
            }
            icon = if (light.status == LightStatus.UNVERIFIED) greyIcon else redIcon
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ ->
                onMarkerClick(light)
                true // Consumir el toque: el detalle lo muestra la app.
            }
        }
        mapView.overlays.add(marker)
    }
    mapView.invalidate()
}
