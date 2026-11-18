package com.alan.semaforosmx

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alan.semaforosmx.ads.BannerAd
import com.alan.semaforosmx.data.UserIdentity
import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import com.alan.semaforosmx.ui.map.TrafficLightMap
import com.alan.semaforosmx.ui.sync.SyncVerdeButton
import com.alan.semaforosmx.ui.theme.SemaforosMxTheme
import com.alan.semaforosmx.widget.FloatingWidgetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Pantalla principal: mapa de semáforos + botón Sync verde + control
 * del widget flotante + contador de syncs + banner de anuncios.
 *
 * Al arrancar pide los permisos necesarios en orden: ubicación, overlay
 * (widget flotante) y notificaciones.
 */
class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* El mapa funciona sin ubicación; el widget la necesita. */ }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Opcional: solo afecta la notificación del widget. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialPermissions()
        setContent {
            SemaforosMxTheme {
                MainScreen(
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    hasOverlayPermission = { Settings.canDrawOverlays(this) }
                )
            }
        }
    }

    private fun requestInitialPermissions() {
        val locationPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        val missing = locationPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            locationPermissionLauncher.launch(missing.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(
        onRequestOverlayPermission: () -> Unit,
        hasOverlayPermission: () -> Boolean
    ) {
        val context = this
        val scope = rememberCoroutineScope()
        var lights by remember { mutableStateOf<List<TrafficLight>>(emptyList()) }
        var syncCount by remember { mutableIntStateOf(0) }
        var widgetActive by remember { mutableStateOf(false) }
        val userId = remember { UserIdentity.get(context) }

        suspend fun refresh() {
            val cityLights = withContext(Dispatchers.IO) {
                RepositoryProvider.repository.lightsInCity("Monterrey")
            }
            val contribution = withContext(Dispatchers.IO) {
                RepositoryProvider.repository.contributionFor(
                    userId, InMemoryTimingRepository.currentMonth()
                )
            }
            lights = cityLights
            syncCount = contribution.syncCount
        }

        LaunchedEffect(Unit) { refresh() }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.map_title)) })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Mapa (ocupa el espacio disponible).
                TrafficLightMap(
                    lights = lights,
                    onMapLongPress = { lat, lon ->
                        scope.launch(Dispatchers.IO) {
                            RepositoryProvider.repository.addLight(
                                TrafficLight(latitude = lat, longitude = lon)
                            )
                            refresh()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    R.string.light_added,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                // Panel inferior: contador, sync, widget.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (syncCount >= 20) {
                            stringResource(R.string.ad_free_active)
                        } else {
                            stringResource(R.string.syncs_this_month, syncCount)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SyncVerdeButton(
                            onSync = {
                                scope.launch { submitSyncFromApp(userId, onDone = { refresh() }) }
                            }
                        )
                        Column {
                            Button(
                                onClick = {
                                    if (!hasOverlayPermission()) {
                                        onRequestOverlayPermission()
                                        return@Button
                                    }
                                    if (widgetActive) {
                                        FloatingWidgetService.stop(context)
                                    } else {
                                        FloatingWidgetService.start(context)
                                    }
                                    widgetActive = !widgetActive
                                }
                            ) {
                                Text(
                                    stringResource(
                                        if (widgetActive) R.string.widget_deactivate
                                        else R.string.widget_activate
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { scope.launch { refresh() } }) {
                                Text("Actualizar mapa")
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.sync_verde_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Banner de anuncios (oculto en modo sin anuncios).
                if (syncCount < 20) {
                    BannerAd()
                }
            }
        }
    }

    /**
     * Sync verde desde la app: usa la última ubicación conocida para
     * encontrar el semáforo más cercano y registrar el reporte.
     */
    private suspend fun submitSyncFromApp(userId: String, onDone: suspend () -> Unit) {
        withContext(Dispatchers.IO) {
            val locationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = try {
                @Suppress("MissingPermission")
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) {
                null
            }
            val now = System.currentTimeMillis()
            if (location != null) {
                val nearest = RepositoryProvider.repository.lightsNear(
                    location.latitude, location.longitude,
                    FloatingWidgetService.DETECTION_RADIUS_METERS
                ).minByOrNull {
                    it.distanceToMeters(location.latitude, location.longitude)
                }
                if (nearest != null) {
                    RepositoryProvider.repository.submitReport(
                        TimingReport(
                            lightId = nearest.id,
                            reportedAtMillis = now,
                            hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                            dayType = InMemoryTimingRepository.currentDayType(),
                            source = ReportSource.MANUAL_SYNC,
                            gpsAccuracyMeters = location.accuracy,
                            userId = userId
                        )
                    )
                }
            }
            RepositoryProvider.repository.recordSync(
                userId, InMemoryTimingRepository.currentMonth()
            )
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, R.string.sync_recorded, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
}
