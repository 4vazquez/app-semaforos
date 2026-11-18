package com.alan.semaforosmx.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlin.math.sqrt

/**
 * Detección pasiva de arranque del vehículo (diseño del MVP, stub funcional).
 *
 * Idea: si el teléfono detecta que el coche arrancó justo cuando el
 * semáforo debía ponerse en verde, ese arranque ES un reporte de tiempo,
 * sin que el conductor presione nada.
 *
 * Señales combinadas:
 * - Acelerómetro: aceleración sostenida > 0.5 m/s² durante > 1.5 s
 *   después de un periodo detenido.
 * - GPS: velocidad de < 1 km/h a > 10 km/h en menos de 5 s.
 *
 * Regla anti-falsos-positivos (ver SPEC.md §7): el arranque solo cuenta
 * como reporte si ocurre dentro de la ventana de verde estimado ± 15 s.
 * Si el conductor arranca fuera de esa ventana (vuelta en rojo, verde
 * desde antes), no se reporta nada.
 *
 * Estado: la clase registra los sensores y detecta el arranque; la
 * calibración fina de umbrales requiere pruebas de campo y queda para v1.
 */
class MovementDetector(
    private val context: Context,
    private val onVehicleStarted: (startedAtMillis: Long) -> Unit
) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var accelSamples = ArrayDeque<Float>()
    private var wasStopped = false
    private var stoppedSinceMillis: Long = 0L
    private var lastSpeedKmh: Float = 0f
    private var running = false

    /**
     * ¿El vehículo está detenido ahora mismo? (Última velocidad GPS < 1 km/h.)
     * Lo usa el anti-abuso: si el coche está quieto pero el GPS brinca
     * entre semáforos, el fix no es fiable (ver FloatingWidgetService).
     */
    val isStopped: Boolean get() = wasStopped

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // Magnitud de la aceleración lineal (sin gravedad aproximada).
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val magnitude = sqrt(ax * ax + ay * ay + az * az)
            // La gravedad aporta ~9.8; lo que interesa es el exceso.
            val linear = magnitude - SensorManager.GRAVITY_EARTH
            accelSamples.addLast(kotlin.math.abs(linear))
            if (accelSamples.size > 30) accelSamples.removeFirst()
            checkStart()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedKmh = location.speed * 3.6f
            val nowStopped = speedKmh < 1f
            if (nowStopped && !wasStopped) {
                stoppedSinceMillis = System.currentTimeMillis()
            }
            if (!nowStopped && wasStopped && speedKmh > 10f) {
                // Arrancó después de estar detenido: posible reporte.
                onVehicleStarted(System.currentTimeMillis())
            }
            wasStopped = nowStopped
            lastSpeedKmh = speedKmh
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    /**
     * ¿La aceleración reciente indica un arranque?
     * Sostenida: promedio de las últimas ~30 muestras > 0.5 m/s².
     */
    private fun checkStart() {
        if (accelSamples.size < 20 || !wasStopped) return
        val avg = accelSamples.average()
        if (avg > 0.5) {
            // Arranque por acelerómetro (respaldo del GPS).
            onVehicleStarted(System.currentTimeMillis())
            accelSamples.clear()
        }
    }

    fun start() {
        if (running) return
        running = true
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(
                accelListener, sensor, SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        try {
            @Suppress("MissingPermission")
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1_000L, 0f, locationListener
            )
        } catch (_: SecurityException) {
            // Sin permiso de ubicación no hay detección pasiva.
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(accelListener)
        locationManager.removeUpdates(locationListener)
    }

    companion object {
        /**
         * ¿Un arranque a esta hora cuenta como reporte de verde?
         * Solo si cae dentro de la ventana [verdeEstimado − 15 s, verdeEstimado + 15 s].
         */
        fun isWithinGreenWindow(
            startedAtMillis: Long,
            estimatedGreenAtMillis: Long,
            windowSeconds: Long = 15
        ): Boolean {
            val delta = kotlin.math.abs(startedAtMillis - estimatedGreenAtMillis)
            return delta <= windowSeconds * 1000
        }
    }
}
