package com.alan.semaforosmx.data.auth

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.alan.semaforosmx.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Autenticación con Google (SSO) + sesión de Supabase (v1.1).
 *
 * Flujo: Credential Manager pide a Google un ID token (la contraseña
 * nunca se ve); ese token se canjea en Supabase Auth por una sesión
 * (access + refresh token); el rol se lee de la fila `users` (RLS:
 * solo la fila propia). La sesión se guarda cifrada en el teléfono
 * (EncryptedSharedPreferences) y se refresca sola al vencer.
 *
 * Sin configuración (falta la URL de Supabase o el Client ID de Google
 * en local.properties), el login no está disponible y la app sigue en
 * modo local anónimo: [AuthState.loginAvailable] es false y la UI lo
 * explica en vez de fallar.
 *
 * Inicializar una vez en Application.onCreate con [init].
 */
object AuthManager {

    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "semaforos_auth_prefs"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "display_name"
    private const val KEY_PHOTO = "photo_url"
    private const val KEY_ROLE = "role"

    /** ¿La app tiene URL + clave anónima de Supabase configuradas? */
    val supabaseConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /** ¿El login con Google está disponible? (Supabase + Client ID). */
    val loginAvailable: Boolean
        get() = supabaseConfigured &&
            BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank()

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow(AuthState(loginAvailable = loginAvailable))
    /** Estado observable por la UI (Compose: collectAsState). */
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authApi: SupabaseAuthApi? by lazy {
        if (supabaseConfigured) {
            SupabaseAuthApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            null
        }
    }

    /** Preferencias cifradas (con respaldo a prefs privadas si el Keystore falla). */
    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Keystore no disponible, usando prefs privadas", e)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** Inicialización: cargar la sesión guardada y refrescarla si venció. */
    fun init(context: Context) {
        appContext = context.applicationContext
        scope.launch { restoreSession() }
    }

    private suspend fun restoreSession() {
        val session = readSession() ?: run {
            _authState.value = AuthState(loginAvailable = loginAvailable)
            return
        }
        val user = readCachedUser()
        if (user == null) {
            clearSession()
            _authState.value = AuthState(loginAvailable = loginAvailable)
            return
        }
        _authState.value = AuthState(user = user, loginAvailable = loginAvailable)
        if (session.isExpired()) {
            refreshSession()
        }
    }

    /**
     * Inicia sesión con Google. Debe llamarse desde una corrutina con
     * la Activity al frente (Credential Manager muestra el selector
     * de cuentas). Devuelve el estado resultante.
     */
    suspend fun signIn(activity: Activity): AuthState {
        if (!loginAvailable) {
            return AuthState(error = "login_not_configured")
        }
        _authState.value = _authState.value.copy(isLoading = true, error = null)
        val idToken = try {
            requestGoogleIdToken(activity)
        } catch (e: GetCredentialCancellationException) {
            _authState.value = _authState.value.copy(isLoading = false)
            return _authState.value
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager falló", e)
            val state = AuthState(error = "google_sign_in_failed", loginAvailable = true)
            _authState.value = state
            return state
        } catch (e: Exception) {
            Log.w(TAG, "Error inesperado en el login", e)
            val state = AuthState(error = "google_sign_in_failed", loginAvailable = true)
            _authState.value = state
            return state
        }
        if (idToken == null) {
            val state = AuthState(error = "google_sign_in_failed", loginAvailable = true)
            _authState.value = state
            return state
        }
        return try {
            val api = authApi ?: throw IllegalStateException("Supabase no configurado")
            val session = api.signInWithGoogleIdToken(idToken).toSession()
            // La fila `users` la crea un trigger al registrarse; puede
            // tardar un instante: reintentar unas veces antes de rendirse.
            var row: SupabaseAuthApi.UserRow? = null
            repeat(5) { attempt ->
                row = runCatching {
                    api.fetchUserRow(session.userId, session.accessToken)
                }.getOrNull()
                if (row != null) return@repeat
                delay(500L * (attempt + 1))
            }
            val user = row?.toAppUser() ?: AppUser(
                id = session.userId,
                email = session.email,
                displayName = null,
                photoUrl = null,
                role = UserRole.USER
            )
            saveSession(session, user)
            val state = AuthState(user = user, loginAvailable = true)
            _authState.value = state
            state
        } catch (e: Exception) {
            Log.w(TAG, "Canje de token falló", e)
            val state = AuthState(error = "supabase_sign_in_failed", loginAvailable = true)
            _authState.value = state
            state
        }
    }

    /** Pide a Google el ID token con Credential Manager. */
    private suspend fun requestGoogleIdToken(activity: Activity): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = CredentialManager.create(activity).getCredential(activity, request)
        val credential = result.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return null
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    /** Refresca la sesión si venció. Devuelve true si hay sesión válida. */
    suspend fun refreshIfNeeded(): Boolean {
        val session = readSession() ?: return false
        if (!session.isExpired()) return true
        return refreshSession()
    }

    private suspend fun refreshSession(): Boolean {
        val session = readSession() ?: return false
        val api = authApi ?: return false
        return try {
            val fresh = api.refreshSession(session.refreshToken).toSession()
            val user = _authState.value.user ?: readCachedUser() ?: return false
            saveSession(fresh, user)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Refresco de sesión falló, cerrando sesión", e)
            clearSession()
            _authState.value = AuthState(loginAvailable = loginAvailable)
            false
        }
    }

    /** Cierra sesión (servidor + local). */
    fun signOut() {
        val token = readSession()?.accessToken
        scope.launch {
            if (token != null) authApi?.signOut(token)
        }
        clearSession()
        _authState.value = AuthState(loginAvailable = loginAvailable)
    }

    /**
     * Borra la cuenta: anonimiza los reportes y borra los datos propios
     * en Supabase (RPC `delete_my_account`), y cierra la sesión local.
     * Devuelve false si el borrado en el servidor falló.
     */
    suspend fun deleteAccount(): Boolean {
        val session = readSession() ?: run {
            clearSession()
            _authState.value = AuthState(loginAvailable = loginAvailable)
            return true
        }
        val api = authApi ?: return false
        return try {
            api.deleteMyAccount(session.accessToken)
            runCatching { api.signOut(session.accessToken) }
            clearSession()
            _authState.value = AuthState(loginAvailable = loginAvailable)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Borrado de cuenta falló", e)
            false
        }
    }

    // --- Acceso síncrono para el repositorio y el widget -----------------
    // El interceptor de Retrofit y el servicio del widget no pueden
    // suspender: leen la sesión guardada. El refresco ocurre al arrancar
    // la app (init) y antes de las operaciones con login.

    /** Access token guardado, o null si no hay sesión o ya venció. */
    fun cachedAccessToken(): String? {
        if (!::appContext.isInitialized) return null
        val session = readSession() ?: return null
        return if (session.isExpired()) null else session.accessToken
    }

    /** ¿Hay sesión guardada vigente? (Para las puertas del widget.) */
    fun isSignedInCached(): Boolean = cachedAccessToken() != null

    /** ID de usuario de Supabase guardado, o null si no hay sesión vigente. */
    fun cachedUserId(): String? {
        if (!::appContext.isInitialized) return null
        val session = readSession() ?: return null
        return if (session.isExpired()) null else session.userId
    }

    /** Rol guardado, o null si no hay sesión. */
    fun cachedUserRole(): UserRole? = readCachedUser()?.role

    /** Nombre visible guardado (para la mensajería ligera), o null. */
    fun cachedDisplayName(): String? = readCachedUser()?.displayName

    // --- Persistencia ----------------------------------------------------

    private fun saveSession(session: AuthSession, user: AppUser) {
        securePrefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES, session.expiresAtMillis)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, user.displayName)
            .putString(KEY_PHOTO, user.photoUrl)
            .putString(KEY_ROLE, user.role.name)
            .apply()
    }

    private fun readSession(): AuthSession? {
        if (!::appContext.isInitialized) return null
        val access = securePrefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = securePrefs.getString(KEY_REFRESH, null) ?: return null
        val expires = securePrefs.getLong(KEY_EXPIRES, 0L)
        val userId = securePrefs.getString(KEY_USER_ID, null) ?: return null
        val email = securePrefs.getString(KEY_EMAIL, null) ?: ""
        return AuthSession(access, refresh, expires, userId, email)
    }

    private fun readCachedUser(): AppUser? {
        if (!::appContext.isInitialized) return null
        val userId = securePrefs.getString(KEY_USER_ID, null) ?: return null
        val email = securePrefs.getString(KEY_EMAIL, null) ?: return null
        return AppUser(
            id = userId,
            email = email,
            displayName = securePrefs.getString(KEY_NAME, null),
            photoUrl = securePrefs.getString(KEY_PHOTO, null),
            role = UserRole.fromString(securePrefs.getString(KEY_ROLE, null))
        )
    }

    private fun clearSession() {
        if (!::appContext.isInitialized) return
        securePrefs.edit().clear().apply()
    }
}
