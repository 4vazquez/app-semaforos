package com.alan.semaforosmx.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.alan.semaforosmx.MainActivity
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.aggregation.TimingAggregator
import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Servicio en primer plano del widget flotante.
 *
 * - Muestra un overlay (SYSTEM_ALERT_WINDOW) con el borde LED y el botón
 *   "Sync verde" sobre cualquier app.
 * - Cada segundo recalcula la cuenta regresiva con el semáforo más cercano.
 * - Alerta (parpadeo + vibración opcional) cuando faltan ≤ 3 s para el verde.
 * - El botón "Sync verde" registra el momento real del cambio y corrige
 *   la base de datos.
 */
class FloatingWidgetService : Service(), SavedStateRegistryOwner {

    companion object {
        const val ACTION_START = "com.alan.semaforosmx.widget.START"
        const val ACTION_STOP = "com.alan.semaforosmx.widget.STOP"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "semaforos_mx_widget"
        private const val PREFS = "semaforos_mx_prefs"
        private const val PREF_OPACITY = "widget_opacity"
        private const val PREF_VIBRATION = "vibration_enabled"

        /** Radio de detección de semáforos cercanos (metros). */
        const val DETECTION_RADIUS_METERS = 40.0

        /** Segundos antes del verde en que empieza la alerta. */
        const val ALERT_THRESHOLD_SECONDS = 3

        /** Tamaño y posición inicial del widget flotante. */
        const val WIDGET_WIDTH_DP = 320
        const val WIDGET_HEIGHT_DP = 340
        const val WIDGET_INITIAL_TOP_DP = 120

        /** Arranca el widget desde cualquier Activity. */
        fun start(context: Context) {
            val intent = Intent(context, FloatingWidgetService::class.java)
                .setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        /** Detiene el widget desde cualquier Activity. */
        fun stop(context: Context) {
            val intent = Intent(context, FloatingWidgetService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    // Estado del overlay (Compose).
    private var ledState by mutableStateOf(LedState.UNKNOWN)
    private var secondsLeft by mutableIntStateOf(0)
    private var showCountdown by mutableStateOf(false)
    private var opacity by mutableFloatStateOf(0.9f)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var nearestLight: TrafficLight? = null
    private var lastAlertedLightId: String? = null

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // SavedStateRegistryOwner exige también ser LifecycleOwner: el servicio
    // maneja su propio ciclo de vida (el overlay de Compose lo necesita).
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            scope.launch(Dispatchers.IO) { updateNearestLight(location) }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
        opacity = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(PREF_OPACITY, 0.9f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWidget()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startWidget()
        }
        return START_STICKY
    }

    private fun startWidget() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        attachOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        startLocationUpdates()
        startTicker()
    }

    private fun stopWidget() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        tickerJob?.cancel()
        locationManager?.removeUpdates(locationListener)
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWidgetService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWidgetService)
            setContent {
                WidgetOverlay(
                    ledState = ledState,
                    secondsLeft = secondsLeft.takeIf { showCountdown },
                    showCountdown = showCountdown,
                    opacity = opacity,
                    onOpacityChange = { value ->
                        opacity = value
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putFloat(PREF_OPACITY, value).apply()
                    },
                    onSyncVerde = { onSyncVerdePressed() },
                    onDrag = { dragAmount -> moveWidget(dragAmount.x, dragAmount.y) },
                    onClose = { stopWidget(); stopSelf() }
                )
            }
        }
        // Ventana flotante pequeña y arrastrable (espejo del prototipo web:
        // el conductor la mueve a donde no le estorbe). No ocupa toda la
        // pantalla: el borde LED va en el contorno del widget.
        val density = resources.displayMetrics.density
        val widthPx = (WIDGET_WIDTH_DP * density).toInt()
        val heightPx = (WIDGET_HEIGHT_DP * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Los toques fuera de la ventana pasan a la app de abajo.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidthPx - widthPx) / 2
            y = (WIDGET_INITIAL_TOP_DP * density).toInt()
        }
        windowManager?.addView(view, params)
        overlayView = view
        overlayParams = params
    }

    /** Mueve la ventana del widget siguiendo el arrastre del asa. */
    private fun moveWidget(dx: Float, dy: Float) {
        val params = overlayParams ?: return
        val view = overlayView ?: return
        params.x = (params.x + dx).toInt()
        params.y = (params.y + dy).toInt()
        windowManager?.updateViewLayout(view, params)
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            @Suppress("MissingPermission") // El permiso se pide en MainActivity antes de arrancar.
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2_000L, // cada 2 s
                5f, // o cada 5 m
                locationListener
            )
        } catch (_: SecurityException) {
            // Sin permiso de ubicación el widget no puede detectar semáforos.
            ledState = LedState.UNKNOWN
        }
    }

    private suspend fun updateNearestLight(location: Location) {
        val lights = RepositoryProvider.repository.lightsNear(
            location.latitude, location.longitude, DETECTION_RADIUS_METERS
        )
        nearestLight = lights.minByOrNull {
            it.distanceToMeters(location.latitude, location.longitude)
        }
        if (nearestLight == null) {
            ledState = LedState.UNKNOWN
            showCountdown = false
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                refreshCountdown()
                delay(1_000L)
            }
        }
    }

    private suspend fun refreshCountdown() {
        val light = nearestLight ?: return
        val now = System.currentTimeMillis()
        val hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dayType = InMemoryTimingRepository.currentDayType()
        val timing = RepositoryProvider.repository.timingFor(light.id, hourSlot, dayType)
        if (timing == null) {
            ledState = LedState.UNKNOWN
            showCountdown = false
            return
        }
        val seconds = TimingAggregator.secondsUntilGreen(timing, now)
        showCountdown = timing.isPredictive && seconds != null
        if (seconds == null) {
            ledState = if (timing.isApproximate) LedState.WAITING else LedState.UNKNOWN
            return
        }
        secondsLeft = seconds
        ledState = when {
            seconds == 0 -> LedState.GREEN
            seconds <= ALERT_THRESHOLD_SECONDS -> {
                if (lastAlertedLightId != light.id + now / 60_000) {
                    lastAlertedLightId = light.id + now / 60_000
                    vibrateAlert()
                }
                LedState.ALERT
            }
            else -> LedState.WAITING
        }
    }

    /** El conductor presionó "Sync verde": el semáforo cambió justo ahora. */
    private fun onSyncVerdePressed() {
        val light = nearestLight
        val location = lastLocation
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (light != null) {
                RepositoryProvider.repository.submitReport(
                    TimingReport(
                        lightId = light.id,
                        reportedAtMillis = now,
                        hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        dayType = InMemoryTimingRepository.currentDayType(),
                        source = ReportSource.MANUAL_SYNC,
                        gpsAccuracyMeters = location?.accuracy ?: 50f,
                        userId = userId
                    )
                )
            }
            RepositoryProvider.repository.recordSync(
                userId, InMemoryTimingRepository.currentMonth()
            )
        }
        // Feedback inmediato: el borde se pone verde aunque no hubiera dato.
        ledState = LedState.GREEN
        showCountdown = false
    }

    /** ID anónimo del usuario, generado una vez en el dispositivo. */
    private val userId: String by lazy {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("user_id", null) ?: run {
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("user_id", id).apply()
            id
        }
    }

    private fun vibrateAlert() {
        val enabled = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_VIBRATION, true)
        if (!enabled) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(150)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.widget_running),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.widget_running))
            .setContentText(getString(R.string.widget_tap_to_open))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopWidget()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        tickerJob?.cancel()
        scope.cancel()
    }
}
