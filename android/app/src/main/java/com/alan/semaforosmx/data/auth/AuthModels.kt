package com.alan.semaforosmx.data.auth

/**
 * Modelo de autenticación y roles (v1.1, SPEC §4.7 y §6.9).
 *
 * Login híbrido con Google (SSO): sin login se puede ver el mapa y
 * recibir la alerta del verde; para aportar (sync desde el widget,
 * agregar o reportar semáforos, ser Colaborador) hay que iniciar
 * sesión con Google. La contraseña de Google nunca se recibe ni se
 * guarda: solo llegan nombre, correo, foto e identificador de Google.
 *
 * Este archivo solo tiene modelos y lógica pura (sin Android), para
 * poder probarlo con JUnit. La sesión real vive en [AuthManager].
 */

/** Roles de la tabla `users` (SPEC §6.9). */
enum class UserRole {
    /** Usuario común con login (todavía sin aportes). */
    USER,

    /** Conductor del programa (p. ej. comunidades Uber/DiDi). */
    COLLABORATOR,

    /** Solo Alan: privilegios totales. */
    ADMIN;

    companion object {
        /** Parsea el rol que devuelve Supabase; lo desconocido es USER. */
        fun fromString(value: String?): UserRole =
            runCatching { valueOf(value ?: "") }.getOrDefault(USER)
    }
}

/** Usuario autenticado (fila de `users` + datos de Google). */
data class AppUser(
    /** UUID de Supabase Auth (`auth.users.id`). */
    val id: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
    val role: UserRole
)

/** Sesión de Supabase (GoTrue) guardada en el teléfono. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Vencimiento del access token, en milisegundos (UTC). */
    val expiresAtMillis: Long,
    val userId: String,
    val email: String
) {
    /** ¿El access token ya venció (o vence en menos de 60 s)? */
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis + 60_000L >= expiresAtMillis

    companion object {
        /** Margen de refresco: 60 s antes de vencer. */
        const val REFRESH_MARGIN_MILLIS = 60_000L
    }
}

/**
 * Estado de autenticación que observa la UI.
 *
 * @param user usuario autenticado, o null si no hay sesión.
 * @param isLoading true mientras se inicia o se cierra sesión.
 * @param error mensaje de error del último intento (null si no hubo).
 * @param loginAvailable false cuando la app no está configurada para
 *   el login (falta la URL de Supabase o el Client ID de Google en
 *   local.properties): el botón explica que aún no está disponible.
 */
data class AuthState(
    val user: AppUser? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginAvailable: Boolean = false
) {
    val isSignedIn: Boolean get() = user != null
    val isAdmin: Boolean get() = user?.role == UserRole.ADMIN
}

/**
 * Puertas de permisos del cliente (SPEC §6.9). Son solo UX: la
 * separación real se hace cumplir en Supabase con RLS; estas reglas
 * deciden qué botones se muestran y qué mensaje recibe el usuario.
 */
object RoleGate {
    /** Sync desde el widget: exige login (con Supabase configurado). */
    fun canSyncFromWidget(user: AppUser?, supabaseConfigured: Boolean): Boolean =
        !supabaseConfigured || user != null

    /** Agregar o reportar semáforos: exige login (con Supabase). */
    fun canCorrectMap(user: AppUser?, supabaseConfigured: Boolean): Boolean =
        !supabaseConfigured || user != null

    /** Ver aportes propios y progreso: exige login (con Supabase). */
    fun canSeeContributions(user: AppUser?, supabaseConfigured: Boolean): Boolean =
        !supabaseConfigured || user != null

    /** Modo prueba: solo Admin (o el desbloqueo de desarrollador). */
    fun canUseTestMode(user: AppUser?, devUnlocked: Boolean): Boolean =
        devUnlocked || user?.role == UserRole.ADMIN
}
