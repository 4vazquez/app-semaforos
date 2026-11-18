package com.alan.semaforosmx.data.repository

import android.content.Context
import android.util.Log
import com.alan.semaforosmx.data.GeoJsonLightsLoader

/**
 * Proveedor del repositorio activo.
 *
 * MVP: [InMemoryTimingRepository] (sin servidor), sembrado con los
 * 1 579 semáforos reales de Monterrey desde el GeoJSON de OpenStreetMap.
 *
 * Para activar Supabase en v1:
 * 1. Crear el proyecto en supabase.com y las tablas del SPEC.md §6.
 * 2. Poner la URL y la clave anónima en `local.properties`:
 *    SUPABASE_URL=https://xxxxx.supabase.co
 *    SUPABASE_ANON_KEY=eyJhbGciOi...
 * 3. Cambiar [USE_SUPABASE] a true.
 * 4. Importar el GeoJSON a la tabla `traffic_lights` (el `id` de cada
 *    semáforo es el ID del nodo de OSM, estable entre exportaciones).
 */
object RepositoryProvider {

    private const val TAG = "RepositoryProvider"
    private const val USE_SUPABASE = false

    // Se leen de BuildConfig cuando se configuren en local.properties (v1).
    private const val SUPABASE_URL = ""
    private const val SUPABASE_ANON_KEY = ""

    val repository: TimingRepository by lazy {
        if (USE_SUPABASE) {
            SupabaseTimingRepository(SUPABASE_URL, SUPABASE_ANON_KEY)
        } else {
            InMemoryTimingRepository()
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
