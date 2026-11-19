package com.alan.semaforosmx.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
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
import com.alan.semaforosmx.ads.AdsManager
import com.alan.semaforosmx.data.UserIdentity
import com.alan.semaforosmx.data.aggregation.TimingAggregator
import com.alan.semaforosmx.data.model.AntiAbuse
import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.NewLightStatus
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import com.alan.semaforosmx.sensing.GpsVerification
import com.alan.semaforosmx.sensing.LocationHelper
import com.alan.semaforosmx.sensing.MovementDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Servicio en primer plano del widget flotante.
 *
 * El widget trabaja en SEGUNDO PLANO mientras la app está cerrada: el
 * servicio vigila la ubicación y el overlay solo APARECE cuando hay un
 * semáforo cerca (≤ 150 m) y se OCULTA al alejarse (> 200 m, con
 * histéresis para no parpadear en el límite). Nunca se muestra mientras
 * la app está abierta: dentro de la app está el menú; el widget es para
 * cuando manejas con otra app al frente.
 *
 * Dos modos según el nivel del usuario (ver [WidgetMode]):
 * - FULL (Colaborador/Plus): barra vertical en el borde derecho con el
 *   borde LED, los botones «Sync verde» y «Aquí hay un semáforo» y el
 *   slider de opacidad.
 * - LED_ONLY (usuario normal): solo una píldora con el borde LED y la
 *   cuenta regresiva. Sin botones, sin opacidad.
 * El modo se re-evalúa cada 30 s y después de cada aporte: al llegar a
 * los 20 semáforos distintos del mes, el widget pasa a completo solo.
 *
 * Tamaño moderado (decisión de Alan): barra/píldora compacta de como
 * máximo un tercio del ancho de la pantalla, en un borde, arrastrable.
 * Nunca cubre toda la pantalla.
 *
 * - Cada segundo recalcula la cuenta regresiva con el semáforo VERIFICADO
 *   más cercano. Los semáforos UNVERIFIED (grises) nunca sirven alertas.
 * - Alerta (parpadeo + vibración opcional) cuando faltan ≤ 3 s para el verde.
 * - Los botones registran el momento real del cambio y corrigen la base de
 *   datos, con verificación GPS y anti-abuso (ubicación simulada, saltos
 *   imposibles, salto GPS con el coche detenido).
 * - La detección pasiva (acelerómetro + GPS) registra el arranque del coche
 *   como reporte solo si cae dentro de la ventana de verde estimado ± 15 s.
 *
 * Modo prueba (solo Admin, ver [TestModeSimulator]): con el interruptor
 * de Ajustes (detrás del desbloqueo de 7 toques en la versión), el
 * servicio usa una ubicación SIMULADA (~30 m del semáforo verificado más
 * cercano) y un ciclo de tiempos sintético de 30 s, alimentando la misma
 * ruta de proximidad que el GPS real. El widget lleva la marca «PRUEBA»,
 * los botones no guardan nada y la detección pasiva queda desactivada:
 * ningún dato de prueba toca el repositorio real. En v1, con login de
 * Google y roles, el modo prueba se limitará al rol Admin y el desbloqueo
 * de 7 toques desaparecerá.
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

        /**
         * Proximidad del widget (metros): el overlay APARECE cuando hay un
         * semáforo verificado a ≤ 150 m y se OCULTA cuando no hay ninguno
         * a ≤ 200 m. La histéresis (150/200) evita que parpadee en el
         * límite al manejar por el borde del radio.
         */
        const val PROXIMITY_SHOW_RADIUS_METERS = 150.0
        const val PROXIMITY_HIDE_RADIUS_METERS = 200.0

        /** Segundos antes del verde en que empieza la alerta. */
        const val ALERT_THRESHOLD_SECONDS = 3

        /**
         * Tamaño del widget (dp): barra vertical completa (Colaborador) de
         * 120 dp de ancho — un tercio de una pantalla típica de 360 dp —
         * y píldora solo-LED (usuario normal) de 160 × 72 dp. Moderado y
         * compacto: nunca cubre toda la pantalla.
         */
        const val FULL_WIDGET_WIDTH_DP = 120
        const val FULL_WIDGET_HEIGHT_DP = 340
        const val LED_WIDGET_WIDTH_DP = 160
        const val LED_WIDGET_HEIGHT_DP = 72

        /** Posición inicial: barra a la derecha, píldora arriba al centro. */
        const val FULL_WIDGET_TOP_DP = 120
        const val FULL_WIDGET_EDGE_MARGIN_DP = 8
        const val LED_WIDGET_TOP_DP = 24

        /** Cada cuántos segundos (ticks de 1 s) se re-evalúa el modo. */
        const val MODE_RECHECK_TICKS = 30

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

        /**
         * ¿La app está al frente? MainActivity lo actualiza en
         * onResume/onPause. El overlay nunca se muestra mientras la app
         * está abierta: dentro de la app está el menú; el widget trabaja
         * en segundo plano cuando la app está cerrada.
         */
        @Volatile
        var appInForeground: Boolean = false
            private set

        fun setAppInForeground(value: Boolean) {
            appInForeground = value
        }
    }

    // Estado del overlay (Compose).
    private var ledState by mutableStateOf(LedState.UNKNOWN)
    private var secondsLeft by mutableIntStateOf(0)
    private var showCountdown by mutableStateOf(false)
    private var opacity by mutableFloatStateOf(0.9f)
    private var widgetMode by mutableStateOf(WidgetMode.LED_ONLY)

    /** Modo prueba activo: ubicación y tiempos simulados, sin guardar nada. */
    private var isTestMode by mutableStateOf(false)
    /** Inicio del ciclo de prueba actual (ms); el LED cicla cada 30 s. */
    private var testCycleStartMillis: Long = 0L
    /** Ubicación simulada (~30 m del semáforo de prueba). */
    private var testSimulatedLocation: Location? = null
    /** Último estado LED en modo prueba (vibrar solo al entrar en ALERT). */
    private var previousTestLedState: LedState? = null

    /**
     * El interruptor de modo prueba vive en Ajustes (la actividad): este
     * listener lo aplica en cuanto cambia, sin reiniciar el servicio.
     * Actividad y servicio corren en el mismo proceso, así que el listener
     * de preferencias sí llega (ver AndroidManifest: sin android:process).
     */
    private val testModePrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == TEST_MODE_PREF_KEY) {
                scope.launch { applyTestMode(prefs.getBoolean(TEST_MODE_PREF_KEY, false)) }
            }
        }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var previousLocation: Location? = null
    private var nearestLight: TrafficLight? = null
    /** Semáforo verificado más cercano dentro del radio de proximidad. */
    private var nearbyLight: TrafficLight? = null
    private var lastAlertedLightId: String? = null
    /** Último reporte pasivo: (semáforo, momento). Evita duplicados. */
    private var lastPassiveReport: Pair<String, Long>? = null
    /** Ticks de 1 s desde la última re-evaluación del modo de widget. */
    private var ticksSinceModeCheck = 0

    /** Detección pasiva de arranque + estado detenido (anti-abuso). */
    private val movementDetector by lazy {
        MovementDetector(this) { startedAtMillis -> onVehicleStarted(startedAtMillis) }
    }

    /** ID anónimo del usuario, ligado al dispositivo (ver UserIdentity). */
    private val userId: String by lazy { UserIdentity.get(this) }

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
            previousLocation = lastLocation
            lastLocation = location
            // En modo prueba el GPS real no mueve el widget: la ubicación
            // simulada alimenta la misma ruta de proximidad. El fix real
            // sí se guarda (sirve de referencia al elegir el semáforo de
            // prueba y al volver al GPS al apagar el modo).
            if (!isTestMode) {
                scope.launch(Dispatchers.IO) { updateNearestLight(location) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        // ORDEN CRÍTICO (crash v0.3.1, reportado en POCO X6 Pro 18 nov 2026):
        // performAttach registra el observador Recreator, y al despachar
        // ON_CREATE Recreator llama consumeRestoredStateForKey, que exige
        // que performRestore ya haya corrido. Con el orden anterior
        // (ON_CREATE antes de performRestore) el servicio se cerraba al
        // instante en cada arranque y el widget nunca aparecía. El orden
        // correcto es: attach → restore → ON_CREATE.
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        opacity = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(PREF_OPACITY, 0.9f)
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(testModePrefsListener)
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
        // El overlay NO se muestra al arrancar: aparece solo cuando hay un
        // semáforo cerca (lo decide updateNearestLight con cada fix GPS).
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        startLocationUpdates()
        movementDetector.start()
        scope.launch { refreshWidgetMode() }
        // Si el modo prueba quedó encendido en Ajustes, activarlo al
        // arrancar (el estado persiste en preferencias, igual que el del
        // widget).
        if (getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(TEST_MODE_PREF_KEY, false)
        ) {
            scope.launch { applyTestMode(true) }
        }
        startTicker()
    }

    private fun stopWidget() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        tickerJob?.cancel()
        movementDetector.stop()
        locationManager?.removeUpdates(locationListener)
        detachOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Estado transitorio de prueba: al re-arrancar, startWidget lo
        // vuelve a calcular desde la preferencia.
        testSimulatedLocation = null
        previousTestLedState = null
        isTestMode = false
    }

    /**
     * Muestra u oculta el overlay según la proximidad (debe correr en el
     * hilo principal: WindowManager + Compose). El overlay se muestra solo
     * si hay un semáforo cerca Y la app está cerrada; en cuanto te alejas
     * o abres la app, se oculta.
     */
    private fun applyOverlayVisibility(shouldShow: Boolean) {
        if (shouldShow && !appInForeground) {
            attachOverlay()
        } else {
            detachOverlay()
        }
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
                    mode = widgetMode,
                    isTestMode = isTestMode,
                    onOpacityChange = { value ->
                        opacity = value
                        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putFloat(PREF_OPACITY, value).apply()
                    },
                    onSyncVerde = { onSyncVerdePressed() },
                    onAquiHaySemaforo = { onAquiHaySemaforoPressed() },
                    onDrag = { dragAmount -> moveWidget(dragAmount.x, dragAmount.y) },
                    onClose = { stopWidget(); stopSelf() }
                )
            }
        }
        // Ventana compacta y arrastrable en un borde de la pantalla
        // (barra de 120 dp o píldora de 160 dp según el modo): claramente
        // visible, pero nunca cubre toda la pantalla. Los toques fuera de
        // la ventana pasan a la app de abajo.
        val density = resources.displayMetrics.density
        val (widthDp, heightDp) = widgetWindowSizeDp(widgetMode)
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (xDp, yDp) = widgetWindowPositionDp(widgetMode, screenWidthPx, density)
            x = (xDp * density).toInt()
            y = (yDp * density).toInt()
        }
        windowManager?.addView(view, params)
        overlayView = view
        overlayParams = params
    }

    /** Quita el overlay de la pantalla (el servicio sigue vigilando). */
    private fun detachOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        overlayParams = null
    }

    /** Tamaño de la ventana (ancho × alto, en dp) según el modo. */
    private fun widgetWindowSizeDp(mode: WidgetMode): Pair<Int, Int> = when (mode) {
        WidgetMode.FULL -> FULL_WIDGET_WIDTH_DP to FULL_WIDGET_HEIGHT_DP
        WidgetMode.LED_ONLY -> LED_WIDGET_WIDTH_DP to LED_WIDGET_HEIGHT_DP
    }

    /**
     * Posición inicial de la ventana (x, y en dp): la barra completa va en
     * el borde derecho; la píldora solo-LED va arriba al centro.
     */
    private fun widgetWindowPositionDp(
        mode: WidgetMode,
        screenWidthPx: Int,
        density: Float
    ): Pair<Float, Float> = when (mode) {
        WidgetMode.FULL -> {
            val screenWidthDp = screenWidthPx / density
            (screenWidthDp - FULL_WIDGET_WIDTH_DP - FULL_WIDGET_EDGE_MARGIN_DP).toFloat() to
                FULL_WIDGET_TOP_DP.toFloat()
        }
        WidgetMode.LED_ONLY -> {
            val screenWidthDp = screenWidthPx / density
            ((screenWidthDp - LED_WIDGET_WIDTH_DP) / 2f) to LED_WIDGET_TOP_DP.toFloat()
        }
    }

    /**
     * Re-evalúa el modo de widget según el nivel actual del usuario
     * (Colaborador/Plus → completo; Gratis → solo LED). Si el modo cambió
     * y el overlay está visible, la ventana se redimensiona y se
     * reposiciona (la posición arrastrada se restablece: el cambio de modo
     * es raro y el tamaño cambia de todas formas).
     */
    private suspend fun refreshWidgetMode() {
        val contribution = withContext(Dispatchers.IO) {
            RepositoryProvider.repository.contributionFor(
                userId, InMemoryTimingRepository.currentMonth()
            )
        }
        val newMode = AdsManager.widgetMode(contribution)
        if (newMode == widgetMode) return
        widgetMode = newMode
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val density = resources.displayMetrics.density
        val (widthDp, heightDp) = widgetWindowSizeDp(newMode)
        params.width = (widthDp * density).toInt()
        params.height = (heightDp * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val (xDp, yDp) = widgetWindowPositionDp(newMode, screenWidthPx, density)
        params.x = (xDp * density).toInt()
        params.y = (yDp * density).toInt()
        windowManager?.updateViewLayout(view, params)
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

    /**
     * Activa o desactiva el modo prueba (ver [TestModeSimulator]). Al
     * activar, se elige el semáforo verificado más cercano a la última
     * ubicación real (o a Lincoln / Ruiz Cortines si no hay ninguno a
     * menos de 2 km) y se simula estar parado a ~30 m de él, alimentando
     * la MISMA ruta de proximidad que el GPS real ([updateNearestLight]).
     * Al desactivar, el servicio vuelve al GPS real de inmediato.
     *
     * La ubicación simulada NUNCA se guarda en [lastLocation] ni se pasa
     * a la verificación GPS: los reportes quedan bloqueados por las
     * guardas de [onSyncVerdePressed], [onAquiHaySemaforoPressed] y
     * [onVehicleStarted], así que ningún dato de prueba toca el
     * repositorio real.
     */
    private suspend fun applyTestMode(enabled: Boolean) {
        if (enabled == isTestMode) return
        if (enabled) enableTestMode() else disableTestMode()
    }

    private suspend fun enableTestMode() {
        val simulated = withContext(Dispatchers.IO) {
            val repo = RepositoryProvider.repository
            val real = lastLocation
            val target = real?.let {
                TestModeSimulator.selectTargetLight(
                    repo.lightsNear(
                        it.latitude, it.longitude,
                        TestModeSimulator.SEARCH_RADIUS_METERS
                    ),
                    it.latitude, it.longitude,
                    TestModeSimulator.SEARCH_RADIUS_METERS
                )
            } ?: TestModeSimulator.selectTargetLight(
                repo.lightsNear(
                    TestModeSimulator.DEFAULT_LATITUDE,
                    TestModeSimulator.DEFAULT_LONGITUDE,
                    TestModeSimulator.SEARCH_RADIUS_METERS
                ),
                TestModeSimulator.DEFAULT_LATITUDE,
                TestModeSimulator.DEFAULT_LONGITUDE,
                TestModeSimulator.SEARCH_RADIUS_METERS
            )
            target?.let {
                buildSimulatedLocation(TestModeSimulator.simulatedPosition(it))
            }
        }
        if (simulated == null) {
            // Sin semáforos verificados cerca ni en el punto por defecto:
            // no hay nada que simular. El interruptor queda encendido en
            // Ajustes; al arrancar de nuevo el servicio lo reintenta.
            showServiceToast(R.string.test_mode_no_light)
            return
        }
        testCycleStartMillis = System.currentTimeMillis()
        previousTestLedState = null
        testSimulatedLocation = simulated
        isTestMode = true
        withContext(Dispatchers.IO) { updateNearestLight(simulated) }
    }

    private suspend fun disableTestMode() {
        isTestMode = false
        testSimulatedLocation = null
        previousTestLedState = null
        ledState = LedState.UNKNOWN
        showCountdown = false
        // Volver al GPS real de inmediato: re-evaluar con el último fix.
        val real = lastLocation
        if (real != null) {
            withContext(Dispatchers.IO) { updateNearestLight(real) }
        } else {
            withContext(Dispatchers.Main) { applyOverlayVisibility(false) }
        }
    }

    /**
     * Construye la ubicación simulada a partir de una posición calculada
     * ([TestModeSimulator.simulatedPosition]). NO es una ubicación
     * simulada del sistema: es un objeto `Location` normal creado en la
     * app, así que `isFromMockProvider` es falso y no dispara la
     * detección anti-abuso. Solo alimenta [updateNearestLight].
     */
    private fun buildSimulatedLocation(position: Pair<Double, Double>): Location =
        Location("test").apply {
            latitude = position.first
            longitude = position.second
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

    private suspend fun updateNearestLight(location: Location) {
        // Solo semáforos VERIFICADOS sirven alertas: los UNVERIFIED (grises)
        // no tienen datos de confianza todavía.
        val verifiedNear = RepositoryProvider.repository.lightsNear(
            location.latitude, location.longitude, PROXIMITY_HIDE_RADIUS_METERS
        ).filter { it.status == LightStatus.VERIFIED }
        nearbyLight = verifiedNear.minByOrNull {
            it.distanceToMeters(location.latitude, location.longitude)
        }
        // El semáforo para la cuenta regresiva y los botones es el más
        // cercano dentro del radio de detección (40 m).
        nearestLight = verifiedNear
            .filter {
                it.distanceToMeters(location.latitude, location.longitude) <=
                    DETECTION_RADIUS_METERS
            }
            .minByOrNull {
                it.distanceToMeters(location.latitude, location.longitude)
            }
        if (nearestLight == null) {
            ledState = LedState.UNKNOWN
            showCountdown = false
        }

        // Visibilidad del overlay (con histéresis): aparece a ≤ 150 m, se
        // oculta cuando no hay nada a ≤ 200 m, y entre 150 y 200 m se queda
        // como está. Corre en el hilo principal (WindowManager + Compose).
        val distance = nearbyLight?.distanceToMeters(
            location.latitude, location.longitude
        )
        val isVisible = overlayView != null
        val shouldShow = when {
            distance == null -> false
            distance <= PROXIMITY_SHOW_RADIUS_METERS -> true
            distance > PROXIMITY_HIDE_RADIUS_METERS -> false
            else -> isVisible // Zona de histéresis: no cambiar.
        }
        if (shouldShow != isVisible) {
            withContext(Dispatchers.Main) { applyOverlayVisibility(shouldShow) }
        } else if (shouldShow && appInForeground) {
            // La app se abrió mientras el widget estaba visible: ocultarlo.
            withContext(Dispatchers.Main) { applyOverlayVisibility(false) }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                refreshCountdown()
                // En modo prueba, re-alimentar la ubicación simulada cada
                // segundo: si el usuario abrió la app (el overlay se
                // oculta) y la cierra, el widget vuelve a aparecer sin
                // esperar un fix GPS real (que en modo prueba se ignora).
                if (isTestMode) {
                    val simulated = testSimulatedLocation
                    if (simulated != null) {
                        scope.launch(Dispatchers.IO) { updateNearestLight(simulated) }
                    }
                }
                // Re-evaluar el modo cada 30 s: si el usuario llegó a los 20
                // semáforos distintos del mes, el widget pasa a completo sin
                // reiniciar nada.
                if (++ticksSinceModeCheck >= MODE_RECHECK_TICKS) {
                    ticksSinceModeCheck = 0
                    refreshWidgetMode()
                }
                // Si la app se abrió, el overlay se oculta (el menú está
                // dentro de la app; el widget trabaja cuando está cerrada).
                if (appInForeground && overlayView != null) {
                    detachOverlay()
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun refreshCountdown() {
        val light = nearestLight ?: return
        // Doble protección: aunque el semáforo más cercano se calculó con
        // VERIFICADOS, su estado pudo cambiar entre actualizaciones.
        if (light.status != LightStatus.VERIFIED) {
            ledState = LedState.UNKNOWN
            showCountdown = false
            return
        }
        val now = System.currentTimeMillis()
        // Modo prueba: el tiempo es un ciclo sintético de 30 s (ver
        // TestTimingCycle). No se consulta el repositorio real: los datos
        // de prueba nunca se mezclan con la agregación.
        if (isTestMode) {
            val elapsedSeconds = (now - testCycleStartMillis) / 1000L
            val fake = TestTimingCycle.stateAt(elapsedSeconds)
            if (fake.ledState == LedState.ALERT &&
                previousTestLedState != LedState.ALERT
            ) {
                vibrateAlert()
            }
            previousTestLedState = fake.ledState
            ledState = fake.ledState
            secondsLeft = fake.secondsLeft
            showCountdown = fake.showCountdown
            return
        }
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

    /** Toast desde el servicio (corre en el hilo principal del scope). */
    private fun showServiceToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Anti-abuso: ¿el GPS acaba de brincar mientras el coche está detenido?
     * Si el sensor de movimiento dice que el coche está quieto pero el GPS
     * saltó más de 100 m en menos de 30 s, el fix no es fiable (ubicación
     * simulada o GPS brincando) y el reporte se rechaza.
     */
    private fun isSuspiciousJump(): Boolean {
        val prev = previousLocation ?: return false
        val curr = lastLocation ?: return false
        if (!movementDetector.isStopped) return false
        val dtMillis = (curr.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000L
        if (dtMillis < 0 || dtMillis > AntiAbuse.STATIONARY_JUMP_WINDOW_MILLIS) return false
        val distance = GpsVerification.distanceMeters(
            prev.latitude, prev.longitude, curr.latitude, curr.longitude
        )
        return distance > AntiAbuse.STATIONARY_JUMP_METERS
    }

    /** El conductor presionó "Sync verde": el semáforo cambió justo ahora. */
    private fun onSyncVerdePressed() {
        // En modo prueba los botones no guardan nada (además de estar
        // desactivados en el overlay): doble protección, la de verdad es
        // esta — la UI sola no basta.
        if (isTestMode) {
            showServiceToast(R.string.test_mode_no_save)
            return
        }
        scope.launch {
            val light = nearestLight
            val location = lastLocation
            if (light == null) {
                showServiceToast(R.string.no_light_nearby)
                return@launch
            }
            if (location == null) {
                showServiceToast(R.string.no_gps_fix)
                return@launch
            }
            val check = GpsVerification.check(
                location, light.latitude, light.longitude
            )
            if (check != GpsVerification.Check.OK) {
                showServiceToast(GpsVerification.messageRes(check))
                return@launch
            }
            if (isSuspiciousJump()) {
                showServiceToast(R.string.report_rejected)
                return@launch
            }
            val accepted = withContext(Dispatchers.IO) {
                val repo = RepositoryProvider.repository
                val ok = repo.submitReport(
                    TimingReport(
                        lightId = light.id,
                        reportedAtMillis = System.currentTimeMillis(),
                        hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                        dayType = InMemoryTimingRepository.currentDayType(),
                        source = ReportSource.MANUAL_SYNC,
                        gpsAccuracyMeters = location.accuracy,
                        userId = userId
                    )
                )
                if (ok) {
                    repo.recordContribution(
                        userId, InMemoryTimingRepository.currentMonth(), light.id
                    )
                }
                ok
            }
            if (!accepted) {
                showServiceToast(R.string.report_rejected)
                return@launch
            }
            // El aporte puede haber completado la cuota Colaborador: el
            // widget pasa a completo sin esperar el re-chequeo de 30 s.
            refreshWidgetMode()
            // Feedback inmediato: el borde se pone verde (solo si el reporte
            // contó; en caso de rechazo el LED no miente).
            ledState = LedState.GREEN
            showCountdown = false
        }
    }

    /**
     * El conductor presionó "Aquí hay un semáforo": está detenido en un
     * semáforo que la app no conoce. Se crea en la ubicación GPS actual
     * (fix fresco obligatorio) y se registra el sync de verde.
     */
    private fun onAquiHaySemaforoPressed() {
        // En modo prueba los botones no guardan nada (ver onSyncVerdePressed).
        if (isTestMode) {
            showServiceToast(R.string.test_mode_no_save)
            return
        }
        scope.launch {
            val location = LocationHelper.getFreshLocation(this@FloatingWidgetService)
            if (location == null) {
                showServiceToast(R.string.no_gps_fix)
                return@launch
            }
            val check = GpsVerification.check(location, null, null)
            if (check != GpsVerification.Check.OK) {
                showServiceToast(GpsVerification.messageRes(check))
                return@launch
            }
            if (isSuspiciousJump()) {
                showServiceToast(R.string.report_rejected)
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                val repo = RepositoryProvider.repository
                val result = repo.reportNewLightAt(
                    location.latitude, location.longitude, userId, location.accuracy
                )
                if ((result.status == NewLightStatus.CREATED ||
                        result.status == NewLightStatus.CONFIRMED_EXISTING) &&
                    result.light != null
                ) {
                    repo.recordContribution(
                        userId, InMemoryTimingRepository.currentMonth(), result.light.id
                    )
                }
                result
            }
            when (outcome.status) {
                NewLightStatus.CREATED,
                NewLightStatus.CONFIRMED_EXISTING -> {
                    // El aporte puede haber completado la cuota Colaborador.
                    refreshWidgetMode()
                    ledState = LedState.GREEN
                    showCountdown = false
                }
                NewLightStatus.ALREADY_REPORTED ->
                    showServiceToast(R.string.already_reported)
                NewLightStatus.DAILY_CAP_REACHED ->
                    showServiceToast(R.string.daily_cap_reached)
            }
        }
    }

    /**
     * Detección pasiva: el coche arrancó. Solo cuenta como reporte de verde
     * si el arranque cae dentro de la ventana de verde estimado ± 15 s
     * (si no, fue vuelta en rojo, verde desde antes, etc.). Un reporte
     * pasivo por semáforo cada 60 s como máximo. Los reportes pasivos NO
     * cuentan para la cuota Colaborador: el programa premia la contribución
     * activa (ver SPEC.md §9).
     */
    private fun onVehicleStarted(startedAtMillis: Long) {
        // En modo prueba la detección pasiva no reporta: el coche está
        // parado en el sillón, no en un semáforo.
        if (isTestMode) return
        scope.launch(Dispatchers.IO) {
            val light = nearestLight ?: return@launch
            if (light.status != LightStatus.VERIFIED) return@launch
            val last = lastPassiveReport
            if (last != null && last.first == light.id &&
                startedAtMillis - last.second < 60_000L
            ) return@launch
            val now = System.currentTimeMillis()
            val timing = RepositoryProvider.repository.timingFor(
                light.id,
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                InMemoryTimingRepository.currentDayType()
            ) ?: return@launch
            val seconds = TimingAggregator.secondsUntilGreen(timing, now) ?: return@launch
            val estimatedGreenAt = now + seconds * 1000L
            if (!MovementDetector.isWithinGreenWindow(startedAtMillis, estimatedGreenAt)) {
                return@launch
            }
            val accepted = RepositoryProvider.repository.submitReport(
                TimingReport(
                    lightId = light.id,
                    reportedAtMillis = startedAtMillis,
                    hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    dayType = InMemoryTimingRepository.currentDayType(),
                    source = ReportSource.PASSIVE,
                    gpsAccuracyMeters = lastLocation?.accuracy ?: 50f,
                    userId = userId
                )
            )
            if (accepted) lastPassiveReport = light.id to startedAtMillis
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
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(testModePrefsListener)
        stopWidget()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        tickerJob?.cancel()
        scope.cancel()
    }
}
