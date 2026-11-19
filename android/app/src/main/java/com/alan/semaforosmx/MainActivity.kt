package com.alan.semaforosmx

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alan.semaforosmx.ads.AdsManager
import com.alan.semaforosmx.ads.BannerAd
import com.alan.semaforosmx.data.UserIdentity
import com.alan.semaforosmx.data.auth.AuthManager
import com.alan.semaforosmx.data.auth.AuthState
import com.alan.semaforosmx.data.auth.RoleGate
import com.alan.semaforosmx.data.legal.TermsAcceptance
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.NewLightStatus
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.model.UserContribution
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import com.alan.semaforosmx.sensing.GpsVerification
import com.alan.semaforosmx.sensing.LocationHelper
import com.alan.semaforosmx.ui.alertas.AlertasScreen
import com.alan.semaforosmx.ui.aportes.MisAportesScreen
import com.alan.semaforosmx.ui.map.TrafficLightMap
import com.alan.semaforosmx.ui.settings.SettingsScreen
import com.alan.semaforosmx.ui.sync.AquiHaySemaforoButton
import com.alan.semaforosmx.ui.sync.SyncVerdeButton
import com.alan.semaforosmx.ui.terms.LegalDocument
import com.alan.semaforosmx.ui.terms.TermsMode
import com.alan.semaforosmx.ui.terms.TermsScreen
import com.alan.semaforosmx.ui.theme.SemaforosMxTheme
import com.alan.semaforosmx.update.ReleaseInfo
import com.alan.semaforosmx.update.SelfUpdater
import com.alan.semaforosmx.update.UpdateDialog
import com.alan.semaforosmx.update.UpdateUiState
import com.alan.semaforosmx.widget.DEV_UNLOCK_PREF_KEY
import com.alan.semaforosmx.widget.FloatingWidgetService
import com.alan.semaforosmx.widget.TEST_MODE_PREF_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Actividad principal: menú inferior (Mapa / Mis aportes / Alertas /
 * Ajustes) + puerta de Términos y Condiciones.
 *
 * El menú solo existe DENTRO de la app: cuando la app está cerrada no hay
 * menú en pantalla; en su lugar el widget flotante trabaja en segundo
 * plano y aparece solo cuando hay un semáforo cerca (ver
 * [FloatingWidgetService]). La actividad avisa al servicio cuando está al
 * frente (onResume/onPause) para que el overlay se oculte mientras el
 * usuario usa el menú.
 *
 * Flujo de arranque: primero los Términos y Condiciones (pantalla de
 * aceptación en la primera apertura; sin «Acepto» no se usa la app),
 * después el menú. Los permisos se piden al arrancar: ubicación, overlay
 * (widget flotante) y notificaciones.
 */
class MainActivity : ComponentActivity() {

    private enum class AppScreen { MAP, APORTES, ALERTAS, SETTINGS, TERMS_VIEW, PRIVACY_VIEW }

    /**
     * Contador de onResume: permite reanudar la descarga de una
     * actualización cuando el usuario vuelve de Ajustes tras conceder el
     * permiso de instalar (ver AppRoot).
     */
    private var resumeCount by mutableIntStateOf(0)

    /**
     * Paso 1 del permiso de ubicación: fina/gruesa. Al concederse, se
     * ofrece el paso 2 (ubicación en segundo plano) con una explicación.
     * En Android 11+ NO se puede pedir la ubicación en segundo plano en
     * la misma llamada que la de primer plano: el sistema la ignora.
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // El mapa funciona sin ubicación; el widget la necesita.
        val foregroundGranted =
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (foregroundGranted) maybeOfferBackgroundLocation()
    }

    /**
     * Paso 2: ubicación en segundo plano («Permitir todo el tiempo»),
     * pedida POR SEPARADO y solo después de la de primer plano (regla de
     * Android 11+). Si el usuario la niega, no se insiste: la pantalla
     * Alertas muestra cómo activarla en Ajustes del sistema.
     */
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        markBackgroundLocationAsked()
        showBackgroundLocationRationale = false
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Opcional: solo afecta la notificación del widget. */ }

    /** Muestra el diálogo que explica por qué hace falta «todo el tiempo». */
    private var showBackgroundLocationRationale by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialPermissions()
        setContent {
            SemaforosMxTheme {
                AppRoot(
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    hasOverlayPermission = { Settings.canDrawOverlays(this) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        // La app está al frente: el widget se oculta (el menú está aquí).
        FloatingWidgetService.setAppInForeground(true)
    }

    override fun onPause() {
        super.onPause()
        // La app se va a segundo plano: el widget puede aparecer si hay un
        // semáforo cerca.
        FloatingWidgetService.setAppInForeground(false)
    }

    private fun requestInitialPermissions() {
        // Paso 1: SOLO ubicación de primer plano. La de segundo plano se
        // pide después y por separado (ver maybeOfferBackgroundLocation):
        // en Android 11+, pedirla en la misma llamada se ignora y el
        // widget quedaría sin GPS con la app cerrada.
        val missing = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            locationPermissionLauncher.launch(missing.toTypedArray())
        } else {
            // Primer plano ya concedido (p. ej. instalación anterior):
            // ofrecer el segundo paso si aún falta.
            maybeOfferBackgroundLocation()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Paso 2: ofrece la ubicación en segundo plano UNA sola vez (si falta
     * el permiso y nunca se ha pedido). Muestra el diálogo con la
     * explicación; el usuario decide si continuar o no.
     */
    private fun maybeOfferBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("background_location_asked", false)) return
        showBackgroundLocationRationale = true
    }

    private fun markBackgroundLocationAsked() {
        getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("background_location_asked", true).apply()
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

    /**
     * Raíz de la app: puerta de términos + menú inferior (mapa / mis
     * aportes / alertas / ajustes). Los Términos en relectura son una
     * pantalla aparte (sin menú). Navegación por estado, sin librería de
     * navegación.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppRoot(
        onRequestOverlayPermission: () -> Unit,
        hasOverlayPermission: () -> Boolean
    ) {
        val context = this
        var termsAccepted by remember {
            mutableStateOf(TermsAcceptance.isAccepted(context))
        }
        var termsText by remember { mutableStateOf("") }
        var privacyText by remember { mutableStateOf("") }
        var screen by remember { mutableStateOf(AppScreen.MAP) }
        var widgetActive by remember {
            mutableStateOf(
                context.getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
                    .getBoolean("widget_active", false)
            )
        }
        var vibrationEnabled by remember {
            mutableStateOf(
                context.getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
                    .getBoolean("vibration_enabled", true)
            )
        }
        // Opciones de desarrollador (7 toques en la versión) y Modo prueba:
        // persisten en preferencias, igual que el estado del widget.
        var devUnlocked by remember {
            mutableStateOf(
                context.getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
                    .getBoolean(DEV_UNLOCK_PREF_KEY, false)
            )
        }
        var testModeEnabled by remember {
            mutableStateOf(
                context.getSharedPreferences("semaforos_mx_prefs", Context.MODE_PRIVATE)
                    .getBoolean(TEST_MODE_PREF_KEY, false)
            )
        }
        // Sesión de Google/Supabase (v1.1): sin login, el mapa y la alerta
        // funcionan igual; aportar exige login cuando hay Supabase.
        val authState by AuthManager.authState.collectAsState()
        // ID para el repositorio: con Supabase es el ID de la cuenta de
        // Google (null sin login: syncs anónimos del Mapa); en modo local
        // es el ID anónimo del dispositivo.
        val repoUserId: String? = if (RepositoryProvider.useSupabase) {
            authState.user?.id
        } else {
            UserIdentity.get(context)
        }

        // Estado del actualizador propio (solo build sideload).
        var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Hidden) }
        val updateScope = rememberCoroutineScope()

        // Puerta de correcciones del mapa: sin login, avisar e ir a Ajustes.
        val onRequireLogin: () -> Unit = {
            updateScope.launch { showToast(R.string.login_required_toast) }
            screen = AppScreen.SETTINGS
        }

        LaunchedEffect(Unit) {
            termsText = withContext(Dispatchers.IO) {
                runCatching { TermsAcceptance.loadTermsText(context) }.getOrDefault("")
            }
            privacyText = withContext(Dispatchers.IO) {
                runCatching { TermsAcceptance.loadPrivacyText(context) }.getOrDefault("")
            }
        }

        if (!termsAccepted) {
            TermsScreen(
                documentText = termsText,
                mode = TermsMode.ACCEPTANCE,
                privacyText = privacyText,
                onAccept = {
                    TermsAcceptance.accept(context)
                    termsAccepted = true
                },
                onDecline = { finish() }
            )
            return
        }

        // Si el widget estaba activo al cerrar la app (o tras reiniciar el
        // teléfono / un cierre del servicio), re-arrancarlo: el estado
        // persiste en preferencias, no solo en memoria. Sin permiso de
        // superposición no se arranca (Alertas lo vuelve a pedir).
        LaunchedEffect(Unit) {
            if (widgetActive && Settings.canDrawOverlays(context)) {
                FloatingWidgetService.start(context)
            }
        }

        // Revisar actualizaciones una vez al arrancar. El chequeo de red
        // es como máximo una vez al día y falla en silencio (ver
        // SelfUpdater): si no hay nada nuevo, no pasa nada.
        LaunchedEffect(Unit) {
            if (!SelfUpdater.isEnabled()) return@LaunchedEffect
            val release = withContext(Dispatchers.IO) {
                SelfUpdater.checkForUpdate(context)
            }
            if (release != null) {
                updateState = UpdateUiState.Available(release)
            }
        }

        // Al volver de Ajustes (permiso de instalar), reanudar la
        // descarga si el permiso ya está concedido.
        LaunchedEffect(resumeCount) {
            val pending = updateState as? UpdateUiState.NeedPermission
                ?: return@LaunchedEffect
            if (SelfUpdater.canInstallPackages(context)) {
                startUpdateDownload(updateScope, pending.release) { updateState = it }
            }
        }

        // Relectura de Términos desde Ajustes: pantalla completa, sin menú.
        if (screen == AppScreen.TERMS_VIEW) {
            TermsScreen(
                documentText = termsText,
                mode = TermsMode.VIEW,
                onClose = { screen = AppScreen.SETTINGS }
            )
            return
        }

        // Relectura del Aviso de Privacidad desde Ajustes: igual que los
        // Términos, pantalla completa sin menú.
        if (screen == AppScreen.PRIVACY_VIEW) {
            TermsScreen(
                documentText = privacyText,
                mode = TermsMode.VIEW,
                document = LegalDocument.PRIVACY,
                onClose = { screen = AppScreen.SETTINGS }
            )
            return
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (screen) {
                                    AppScreen.MAP -> R.string.map_title
                                    AppScreen.APORTES -> R.string.aportes_title
                                    AppScreen.ALERTAS -> R.string.alertas_title
                                    AppScreen.SETTINGS -> R.string.settings
                                    AppScreen.TERMS_VIEW -> R.string.terms_title
                                    AppScreen.PRIVACY_VIEW -> R.string.privacy_title
                                }
                            )
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == AppScreen.MAP,
                        onClick = { screen = AppScreen.MAP },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = stringResource(R.string.nav_map)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_map)) }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.APORTES,
                        onClick = { screen = AppScreen.APORTES },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = stringResource(R.string.nav_aportes)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_aportes)) }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.ALERTAS,
                        onClick = { screen = AppScreen.ALERTAS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.nav_alertas)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_alertas)) }
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.SETTINGS,
                        onClick = { screen = AppScreen.SETTINGS },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        },
                        label = { Text(stringResource(R.string.settings)) }
                    )
                }
            }
        ) { padding ->
            when (screen) {
                AppScreen.MAP -> MapTabContent(
                    repoUserId = repoUserId,
                    authState = authState,
                    onRequireLogin = onRequireLogin,
                    modifier = Modifier.padding(padding)
                )
                AppScreen.APORTES -> MisAportesScreen(
                    repoUserId = repoUserId,
                    supabaseConfigured = RepositoryProvider.useSupabase,
                    isSignedIn = authState.isSignedIn,
                    userRole = authState.user?.role,
                    onSignInClick = { screen = AppScreen.SETTINGS },
                    modifier = Modifier.padding(padding)
                )
                AppScreen.ALERTAS -> AlertasScreen(
                    userId = repoUserId,
                    widgetActive = widgetActive,
                    hasBackgroundLocation =
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED,
                    onToggleWidget = {
                        if (!hasOverlayPermission()) {
                            onRequestOverlayPermission()
                            return@AlertasScreen
                        }
                        if (widgetActive) {
                            FloatingWidgetService.stop(context)
                        } else {
                            FloatingWidgetService.start(context)
                        }
                        widgetActive = !widgetActive
                        context.getSharedPreferences(
                            "semaforos_mx_prefs", Context.MODE_PRIVATE
                        ).edit().putBoolean("widget_active", widgetActive).apply()
                    },
                    modifier = Modifier.padding(padding)
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    vibrationEnabled = vibrationEnabled,
                    onVibrationChange = { enabled ->
                        vibrationEnabled = enabled
                        context.getSharedPreferences(
                            "semaforos_mx_prefs", Context.MODE_PRIVATE
                        ).edit().putBoolean("vibration_enabled", enabled).apply()
                    },
                    onTermsClick = { screen = AppScreen.TERMS_VIEW },
                    onPrivacyClick = { screen = AppScreen.PRIVACY_VIEW },
                    authState = authState,
                    onSignInClick = {
                        updateScope.launch { AuthManager.signIn(this@MainActivity) }
                    },
                    onSignOutClick = { AuthManager.signOut() },
                    onDeleteAccountClick = {
                        updateScope.launch {
                            val ok = AuthManager.deleteAccount()
                            showToast(
                                if (ok) R.string.account_deleted
                                else R.string.account_delete_failed
                            )
                        }
                    },
                    devUnlocked = devUnlocked,
                    onDevUnlock = {
                        devUnlocked = true
                        context.getSharedPreferences(
                            "semaforos_mx_prefs", Context.MODE_PRIVATE
                        ).edit().putBoolean(DEV_UNLOCK_PREF_KEY, true).apply()
                    },
                    testModeEnabled = testModeEnabled,
                    onTestModeChange = { enabled ->
                        testModeEnabled = enabled
                        context.getSharedPreferences(
                            "semaforos_mx_prefs", Context.MODE_PRIVATE
                        ).edit().putBoolean(TEST_MODE_PREF_KEY, enabled).apply()
                    },
                    modifier = Modifier.padding(padding)
                )
                AppScreen.TERMS_VIEW -> Unit // Se maneja arriba (sin menú).
                AppScreen.PRIVACY_VIEW -> Unit // Se maneja arriba (sin menú).
            }
        }

        // Diálogo de actualización (solo si hay versión nueva pendiente).
        UpdateDialog(
            state = updateState,
            onUpdate = { release -> startUpdateFlow(updateScope, release) { updateState = it } },
            onLater = { release ->
                SelfUpdater.dismiss(context, release.tag)
                updateState = UpdateUiState.Hidden
            },
            onRetry = { release -> startUpdateFlow(updateScope, release) { updateState = it } },
            onGrantPermission = { SelfUpdater.openInstallPermissionSettings(context) }
        )

        // Paso 2 del permiso de ubicación: explicación en español ANTES de
        // pedir «Permitir todo el tiempo» (Android 11+ exige pedirlo por
        // separado de la ubicación de primer plano). Se ofrece una sola
        // vez; si el usuario dice que no, no se insiste.
        if (showBackgroundLocationRationale) {
            AlertDialog(
                onDismissRequest = {
                    markBackgroundLocationAsked()
                    showBackgroundLocationRationale = false
                },
                title = { Text(stringResource(R.string.background_location_title)) },
                text = { Text(stringResource(R.string.background_location_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackgroundLocationRationale = false
                            backgroundLocationLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        }
                    ) { Text(stringResource(R.string.background_location_allow)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            markBackgroundLocationAsked()
                            showBackgroundLocationRationale = false
                        }
                    ) { Text(stringResource(R.string.background_location_later)) }
                }
            )
        }
    }

    /**
     * Flujo de actualización con un toque: si falta el permiso de
     * instalar, pedirlo primero (Ajustes del sistema); si ya está,
     * descargar e instalar.
     */
    private fun startUpdateFlow(
        scope: CoroutineScope,
        release: ReleaseInfo,
        setState: (UpdateUiState) -> Unit
    ) {
        if (!SelfUpdater.canInstallPackages(this)) {
            setState(UpdateUiState.NeedPermission(release))
            return
        }
        startUpdateDownload(scope, release, setState)
    }

    /**
     * Descarga el APK con progreso y lanza el instalador del sistema. El
     * usuario confirma la instalación en el diálogo de Android; la app
     * nunca instala sola.
     */
    private fun startUpdateDownload(
        scope: CoroutineScope,
        release: ReleaseInfo,
        setState: (UpdateUiState) -> Unit
    ) {
        scope.launch {
            setState(UpdateUiState.Downloading(release, 0f))
            val file = SelfUpdater.downloadApk(this@MainActivity, release.apkUrl) { progress ->
                setState(UpdateUiState.Downloading(release, progress))
            }
            if (file == null) {
                setState(UpdateUiState.Failed(release))
                return@launch
            }
            SelfUpdater.clearPendingUpdate(this@MainActivity)
            SelfUpdater.installApk(this@MainActivity, file)
            setState(UpdateUiState.Hidden)
        }
    }

    /**
     * Pestaña Mapa: mapa de semáforos + botones Sync verde y «Aquí hay un
     * semáforo» + contador de contribuciones + banner de anuncios. Sin
     * Scaffold propio: la barra superior y el menú los pone la actividad.
     */
    @Composable
    private fun MapTabContent(
        repoUserId: String?,
        authState: AuthState,
        onRequireLogin: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val context = this
        val scope = rememberCoroutineScope()
        var lights by remember { mutableStateOf<List<TrafficLight>>(emptyList()) }
        var contribution by remember {
            mutableStateOf(UserContribution("", InMemoryTimingRepository.currentMonth()))
        }
        var selectedLight by remember { mutableStateOf<TrafficLight?>(null) }
        var selectedRemovalCount by remember { mutableIntStateOf(0) }
        var selectedAlreadyReported by remember { mutableStateOf(false) }

        // Corregir el mapa (agregar / reportar retiro) exige login con
        // Supabase; el sync desde el Mapa no (SPEC §4.7).
        val canCorrect = RoleGate.canCorrectMap(
            authState.user, RepositoryProvider.useSupabase
        )

        suspend fun refresh() {
            val cityLights = withContext(Dispatchers.IO) {
                RepositoryProvider.repository.lightsInCity("Monterrey")
            }
            val current = if (repoUserId != null) {
                withContext(Dispatchers.IO) {
                    RepositoryProvider.repository.contributionFor(
                        repoUserId, InMemoryTimingRepository.currentMonth()
                    )
                }
            } else {
                UserContribution("", InMemoryTimingRepository.currentMonth())
            }
            lights = cityLights
            contribution = current
        }

        LaunchedEffect(Unit) { refresh() }
        // Al iniciar sesión, el ID cambia (dispositivo → cuenta): refrescar.
        LaunchedEffect(repoUserId) { refresh() }

        // Al abrir el detalle de un semáforo, cargar sus reportes de retiro.
        LaunchedEffect(selectedLight) {
            val light = selectedLight ?: return@LaunchedEffect
            selectedRemovalCount = withContext(Dispatchers.IO) {
                RepositoryProvider.repository.removalCount(light.id)
            }
            selectedAlreadyReported = if (repoUserId != null) {
                withContext(Dispatchers.IO) {
                    RepositoryProvider.repository.hasReportedRemoval(light.id, repoUserId)
                }
            } else {
                false
            }
        }

        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Mapa (ocupa el espacio disponible).
            TrafficLightMap(
                lights = lights,
                onMapLongPress = { lat, lon ->
                    if (!canCorrect || repoUserId == null) {
                        onRequireLogin()
                    } else {
                        scope.launch {
                            val canAdd = withContext(Dispatchers.IO) {
                                RepositoryProvider.repository.canSubmitNewLight(repoUserId)
                            }
                            if (!canAdd) {
                                showToast(R.string.daily_cap_reached)
                                return@launch
                            }
                            withContext(Dispatchers.IO) {
                                RepositoryProvider.repository.addLight(
                                    TrafficLight(
                                        latitude = lat,
                                        longitude = lon,
                                        reportedBy = repoUserId
                                    )
                                )
                            }
                            refresh()
                            showToast(R.string.light_added)
                        }
                    }
                },
                onMarkerClick = { light -> selectedLight = light },
                modifier = Modifier.weight(1f)
            )

            // Panel inferior: contador, botones, actualizar.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TODO: el texto dice «Syncs» pero el contador ahora es
                // actualizaciones en semáforos distintos al mes. Alan
                // revisa los textos finales por separado; no cambiar
                // hasta confirmar la copia.
                Text(
                    text = if (contribution.isAdFree) {
                        stringResource(R.string.ad_free_active)
                    } else {
                        stringResource(
                            R.string.syncs_this_month,
                            contribution.updateCount
                        )
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
                            scope.launch { submitSyncFromApp(repoUserId) { refresh() } }
                        }
                    )
                    AquiHaySemaforoButton(
                        onReport = {
                            if (!canCorrect || repoUserId == null) {
                                onRequireLogin()
                            } else {
                                scope.launch { submitAquiHaySemaforo(repoUserId) { refresh() } }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { scope.launch { refresh() } }) {
                    Text("Actualizar mapa")
                }
                TextButton(
                    onClick = {
                        if (!canCorrect || repoUserId == null) {
                            onRequireLogin()
                        } else {
                            scope.launch { submitYaNoExiste(repoUserId, null) { refresh() } }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ya_no_existe))
                }
                Text(
                    text = stringResource(R.string.sync_verde_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.aqui_hay_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Banner de anuncios (oculto para Colaboradores y Plus).
            if (AdsManager.shouldShowAds(contribution)) {
                BannerAd()
            }
        }

        // Detalle del semáforo tocado en el mapa (con «Ya no existe»).
        selectedLight?.let { light ->
            AlertDialog(
                onDismissRequest = { selectedLight = null },
                title = { Text(stringResource(R.string.light_details)) },
                text = {
                    Column {
                        Text(light.name ?: "Semáforo")
                        if (light.status == LightStatus.UNVERIFIED) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.light_unverified))
                        }
                        if (selectedRemovalCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(
                                    R.string.removal_count,
                                    selectedRemovalCount
                                )
                            )
                        }
                        if (selectedAlreadyReported) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.already_reported))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !selectedAlreadyReported,
                        onClick = {
                            selectedLight = null
                            if (!canCorrect || repoUserId == null) {
                                onRequireLogin()
                            } else {
                                scope.launch { submitYaNoExiste(repoUserId, light) { refresh() } }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.report_removed))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedLight = null }) {
                        Text(stringResource(R.string.close_button))
                    }
                }
            )
        }
    }

    /** Toast en el hilo principal (los flujos corren en IO). */
    private suspend fun showToast(resId: Int, arg: Int? = null) {
        withContext(Dispatchers.Main) {
            val text = if (arg != null) getString(resId, arg) else getString(resId)
            Toast.makeText(this@MainActivity, text, Toast.LENGTH_SHORT).show()
        }
    }

    /** Mensaje amable para cada fallo de verificación GPS. */
    private fun messageFor(check: GpsVerification.Check): Int =
        GpsVerification.messageRes(check)

    /** Resultado del flujo de sync verde (para el mensaje correcto). */
    private sealed interface SyncResult {
        data object NoLight : SyncResult
        data class CheckFailed(val check: GpsVerification.Check) : SyncResult
        data object Rejected : SyncResult
        data object Accepted : SyncResult
    }

    /**
     * Sync verde desde la app: fix GPS fresco, semáforo más cercano (40 m),
     * verificación de proximidad y registro del reporte. Solo cuenta para
     * la cuota si el reporte fue aceptado. Sin login ([userId] null, solo
     * con Supabase), el reporte es anónimo: alimenta la agregación pero
     * no acredita cuota (SPEC §4.7).
     */
    private suspend fun submitSyncFromApp(userId: String?, onDone: suspend () -> Unit) {
        val location = LocationHelper.getFreshLocation(this)
        if (location == null) {
            showToast(R.string.no_gps_fix)
            return
        }
        val result = withContext(Dispatchers.IO) {
            val repo = RepositoryProvider.repository
            val nearestLight = repo.lightsNear(
                location.latitude, location.longitude,
                FloatingWidgetService.DETECTION_RADIUS_METERS
            ).minByOrNull {
                it.distanceToMeters(location.latitude, location.longitude)
            } ?: return@withContext SyncResult.NoLight
            val check = GpsVerification.check(
                location, nearestLight.latitude, nearestLight.longitude
            )
            if (check != GpsVerification.Check.OK) {
                return@withContext SyncResult.CheckFailed(check)
            }
            val acceptedReport = repo.submitReport(
                TimingReport(
                    lightId = nearestLight.id,
                    reportedAtMillis = System.currentTimeMillis(),
                    hourSlot = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    dayType = InMemoryTimingRepository.currentDayType(),
                    source = ReportSource.MANUAL_SYNC,
                    gpsAccuracyMeters = location.accuracy,
                    userId = userId
                )
            )
            if (!acceptedReport) return@withContext SyncResult.Rejected
            if (userId != null) {
                repo.recordContribution(
                    userId, InMemoryTimingRepository.currentMonth(), nearestLight.id
                )
            }
            SyncResult.Accepted
        }
        when (result) {
            SyncResult.NoLight -> showToast(R.string.no_light_nearby)
            is SyncResult.CheckFailed -> showToast(messageFor(result.check))
            // Rechazo anti-abuso (salto imposible): mensaje genérico a
            // propósito, no se le explica al abusador qué falló.
            SyncResult.Rejected -> showToast(R.string.report_rejected)
            SyncResult.Accepted -> {
                showToast(R.string.sync_recorded)
                onDone()
            }
        }
    }

    /**
     * «Aquí hay un semáforo»: crea el semáforo en la ubicación GPS actual
     * (fix fresco y preciso obligatorio) y registra el sync de verde.
     */
    private suspend fun submitAquiHaySemaforo(userId: String, onDone: suspend () -> Unit) {
        val location = LocationHelper.getFreshLocation(this)
        if (location == null) {
            showToast(R.string.no_gps_fix)
            return
        }
        val check = GpsVerification.check(location, null, null)
        if (check != GpsVerification.Check.OK) {
            showToast(messageFor(check))
            return
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
            NewLightStatus.CREATED -> showToast(R.string.light_added)
            NewLightStatus.CONFIRMED_EXISTING -> showToast(R.string.light_confirmed)
            NewLightStatus.ALREADY_REPORTED -> showToast(R.string.already_reported)
            NewLightStatus.DAILY_CAP_REACHED -> showToast(R.string.daily_cap_reached)
        }
        onDone()
    }

    /**
     * «Ya no existe»: reporta que un semáforo fue retirado. Si [target] es
     * null, usa el semáforo más cercano (100 m). Siempre verifica el GPS
     * contra el semáforo y respeta el anti-spam (un reporte por usuario).
     */
    private suspend fun submitYaNoExiste(
        userId: String,
        target: TrafficLight?,
        onDone: suspend () -> Unit
    ) {
        val location = LocationHelper.getFreshLocation(this)
        if (location == null) {
            showToast(R.string.no_gps_fix)
            return
        }
        val light = target ?: withContext(Dispatchers.IO) {
            RepositoryProvider.repository.lightsNear(
                location.latitude, location.longitude,
                GpsVerification.MAX_REPORT_DISTANCE_METERS
            ).minByOrNull {
                it.distanceToMeters(location.latitude, location.longitude)
            }
        }
        if (light == null) {
            showToast(R.string.no_light_nearby)
            return
        }
        val check = GpsVerification.check(location, light.latitude, light.longitude)
        if (check != GpsVerification.Check.OK) {
            showToast(messageFor(check))
            return
        }
        val outcome = withContext(Dispatchers.IO) {
            val repo = RepositoryProvider.repository
            if (repo.hasReportedRemoval(light.id, userId)) return@withContext null
            val result = repo.reportRemoved(light.id, userId)
            if (!result.alreadyReported) {
                repo.recordContribution(
                    userId, InMemoryTimingRepository.currentMonth(), light.id
                )
            }
            result
        }
        if (outcome == null) {
            showToast(R.string.already_reported)
            return
        }
        if (outcome.nowHidden) {
            showToast(R.string.light_hidden)
        } else {
            showToast(R.string.removal_reported, outcome.distinctCount)
        }
        onDone()
    }
}
