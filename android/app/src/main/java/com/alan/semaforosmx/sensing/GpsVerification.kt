package com.alan.semaforosmx.sensing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Verificación por GPS contra el farmeo «desde el sillón».
 *
 * Regla (Alan): un sync o un reporte de corrección de mapa solo CUENTA si
 * el GPS confirma que el usuario está realmente en ese semáforo en el
 * momento del toque. Sin GPS válido, el reporte se rechaza con un mensaje
 * amable (la app nunca inventa datos de ubicación).
 *
 * - Proximidad: el usuario debe estar a ≤ 100 m del semáforo. Los syncs
 *   usan un radio de búsqueda más estricto (40 m, el radio de detección),
 *   así que la regla se cumple con margen; los retiros usan los 100 m.
 * - Frescura: el fix debe tener menos de 30 s (nada de ubicaciones en
 *   caché de hace horas: `getLastKnownLocation` sola no basta).
 * - Precisión: el fix debe traer precisión ≤ 50 m.
 * - Autenticidad: el fix NO debe venir de un proveedor simulado (mock
 *   location): Android lo marca y el reporte se rechaza.
 *
 * Para «Aquí hay un semáforo» la comprobación de proximidad es inherente
 * (el semáforo nuevo se crea EN la ubicación GPS actual), pero la
 * exigencia de fix fresco y preciso aplica igual.
 */
object GpsVerification {

    /** Distancia máxima (m) entre el usuario y el semáforo para reportar. */
    const val MAX_REPORT_DISTANCE_METERS = 100.0

    /** Antigüedad máxima de un fix GPS para aceptarlo (30 s). */
    const val MAX_LOCATION_AGE_MILLIS = 30_000L

    /** Precisión máxima aceptable de un fix (50 m). */
    const val MAX_ACCURACY_METERS = 50f

    /** Resultado de verificar un fix GPS contra un semáforo. */
    enum class Check {
        /** Todo en orden: se puede registrar el reporte. */
        OK,
        /** No hay ubicación disponible. */
        NO_LOCATION,
        /** La ubicación viene de un proveedor simulado (mock). */
        MOCK,
        /** El fix es viejo (caché) o no trae marca de tiempo fiable. */
        STALE,
        /** El fix es demasiado impreciso (> 50 m). */
        INACCURATE,
        /** El usuario está a más de 100 m del semáforo. */
        TOO_FAR
    }

    /** ¿El fix viene de un proveedor simulado (ubicación falsa)? */
    @Suppress("DEPRECATION") // isFromMockProvider funciona en todos los API.
    fun isMock(location: Location): Boolean = location.isFromMockProvider

    /** ¿El fix tiene menos de [MAX_LOCATION_AGE_MILLIS]? */
    fun isFresh(location: Location): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (ageNanos < 0) return false // Reloj del fix en el futuro: no fiable.
        return ageNanos <= MAX_LOCATION_AGE_MILLIS * 1_000_000L
    }

    /** ¿El fix trae precisión y es ≤ [MAX_ACCURACY_METERS]? */
    fun isAccurateEnough(location: Location): Boolean =
        location.hasAccuracy() && location.accuracy <= MAX_ACCURACY_METERS

    /** Distancia haversine en metros entre dos puntos. */
    fun distanceMeters(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** Mensaje amable (recurso de string) para cada fallo de verificación. */
    fun messageRes(check: Check): Int = when (check) {
        Check.OK -> 0 // No se usa: OK no muestra mensaje.
        Check.NO_LOCATION -> com.alan.semaforosmx.R.string.no_gps_fix
        Check.MOCK -> com.alan.semaforosmx.R.string.gps_mock
        Check.STALE -> com.alan.semaforosmx.R.string.gps_stale
        Check.INACCURATE -> com.alan.semaforosmx.R.string.gps_inaccurate
        Check.TOO_FAR -> com.alan.semaforosmx.R.string.must_be_near_light
    }

    /**
     * Verificación completa de un fix contra un semáforo.
     * Si [lightLatitude]/[lightLongitude] son null, solo se verifican
     * frescura y precisión (caso «Aquí hay un semáforo»: el punto nuevo
     * ES la ubicación actual).
     *
     * [allowMock] es el beneficio Plus de ubicación simulada: con el
     * toggle activado, un fix simulado no se rechaza con MOCK (el
     * llamador marca el reporte como `simulated` y no cuenta para la
     * cuota ni el ranking). Sin Plus, un fix simulado siempre se rechaza.
     */
    fun check(
        location: Location?,
        lightLatitude: Double?,
        lightLongitude: Double?,
        bypassAntiAbuse: Boolean = false,
        allowMock: Boolean = false
    ): Check {
        if (location == null) return Check.NO_LOCATION
        // Privilegios totales del ADMIN (solo Alan): puede probar con
        // ubicación simulada (fake GPS) desde cualquier parte.
        if (bypassAntiAbuse) return Check.OK
        // Plus con fake GPS activado: el fix simulado se acepta para
        // uso personal; el reporte va marcado y no cuenta (SPEC §9).
        if (isMock(location) && !allowMock) return Check.MOCK
        if (!isFresh(location)) return Check.STALE
        if (!isAccurateEnough(location)) return Check.INACCURATE
        if (lightLatitude != null && lightLongitude != null) {
            val distance = distanceMeters(
                location.latitude, location.longitude, lightLatitude, lightLongitude
            )
            if (distance > MAX_REPORT_DISTANCE_METERS) return Check.TOO_FAR
        }
        return Check.OK
    }
}

/**
 * Obtiene un fix GPS fresco.
 *
 * 1. Si la última ubicación conocida ya es fresca y precisa, se devuelve
 *    tal cual (rápido, sin encender el GPS).
 * 2. Si no, se pide una actualización al GPS (con la red como respaldo) y
 *    se espera hasta [timeoutMs] por un fix que cumpla frescura/precisión.
 * 3. Si no llega nada a tiempo, devuelve null (la UI muestra el mensaje).
 *
 * El llamador debe haber pedido ya los permisos de ubicación.
 */
object LocationHelper {

    @SuppressLint("MissingPermission") // El llamador pidió los permisos antes.
    suspend fun getFreshLocation(
        context: Context,
        timeoutMs: Long = 12_000L
    ): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // 1. Última conocida, si sirve.
        val lastKnown = try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (lastKnown != null &&
            !GpsVerification.isMock(lastKnown) &&
            GpsVerification.isFresh(lastKnown) &&
            GpsVerification.isAccurateEnough(lastKnown)
        ) {
            return lastKnown
        }
        // 2. Pedir un fix nuevo con tiempo límite.
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!GpsVerification.isMock(location) &&
                            GpsVerification.isFresh(location) &&
                            GpsVerification.isAccurateEnough(location) &&
                            cont.isActive
                        ) {
                            runCatching { manager.removeUpdates(this) }
                            cont.resume(location)
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
                    if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        manager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 0L, 0f,
                            listener, Looper.getMainLooper()
                        )
                    }
                    if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
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
}
