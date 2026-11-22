package com.alan.semaforosmx.data.repository

import android.content.Context
import android.util.Log
import com.alan.semaforosmx.BuildConfig
import com.alan.semaforosmx.data.GeoJsonLightsLoader
import com.alan.semaforosmx.data.auth.AuthManager

/**
 * Proveedor del repositorio activo.
 *
 * Modo local (sin configurar): [InMemoryTimingRepository], sembrado con
 * los 1 579 semáforos reales de Monterrey desde el GeoJSON de
 * OpenStreetMap. Modo Supabase (v1.1): si `local.properties` trae
 * SUPABASE_URL y SUPABASE_ANON_KEY, se usa [SupabaseTimingRepository]
 * con el JWT del usuario cuando hay sesión (RLS decide qué ve cada
 * uno). Ver `supabase/README.md` para levantar el proyecto.
 */
object RepositoryProvider {

    private const val TAG = "RepositoryProvider"

    /** ¿La app está conectada a Supabase? (URL + clave en local.properties). */
    val useSupabase: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val repository: TimingRepository by lazy {
        if (useSupabase) {
            SupabaseTimingRepository(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY,
                tokenProvider = { AuthManager.cachedAccessToken() }
            )
        } else {
            InMemoryTimingRepository()
        }
    }

    /**
     * Repositorio de mensajería ligera por semáforo («¿Ahí hay algo?»).
     * Singleton: el widget y el mapa comparten el mismo estado.
     * Sin Supabase configurado, la mensajería funciona en memoria
     * (solo visible en el propio teléfono hasta que el backend viva).
     */
    val lightMessages: LightMessagesRepository by lazy {
        if (useSupabase) {
            SupabaseLightMessagesRepository(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY,
                tokenProvider = { AuthManager.cachedAccessToken() }
            )
        } else {
            InMemoryLightMessagesRepository()
        }
    }

    /**
     * Repositorio del ranking semanal de Colaboradores (SPEC §6.10).
     * Singleton: Mis aportes comparte el mismo estado. En modo local
     * calcula el ranking desde los reportes en memoria; con Supabase
     * usa la función `top_collaborators_week` y la preferencia de
     * aparición vive en la tabla `users`.
     */
    val ranking: RankingRepository by lazy {
        if (useSupabase) {
            SupabaseRankingRepository(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY,
                tokenProvider = { AuthManager.cachedAccessToken() }
            )
        } else {
            InMemoryRankingRepository(repository as InMemoryTimingRepository)
        }
    }

    /**
     * Repositorio de verificación de conductor (sello Colaborador,
     * SPEC §6.9). Singleton: la pantalla de verificación comparte el
     * mismo estado. En modo local guarda las solicitudes en memoria;
     * con Supabase sube la foto al bucket privado `license-photos` y
     * crea la fila en `verification_requests`.
     */
    val verification: VerificationRepository by lazy {
        if (useSupabase) {
            SupabaseVerificationRepository(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY,
                tokenProvider = { AuthManager.cachedAccessToken() }
            )
        } else {
            InMemoryVerificationRepository()
        }
    }

    /**
     * Inicialización al arrancar la app: siembra la base en memoria con
     * los semáforos del GeoJSON. Se llama desde la clase Application.
     * Si la carga falla, el repositorio usa los cruces de respaldo.
     */
    fun init(context: Context) {
        val repo = repository
        if (repo is InMemoryTimingRepository) {
            val lights = try {
                GeoJsonLightsLoader.load(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar semaforos_mty.geojson, usando respaldo", e)
                emptyList()
            }
            repo.seedLights(lights)
            Log.i(TAG, "Base sembrada con ${lights.size} semáforos de Monterrey")
        }
    }
}
