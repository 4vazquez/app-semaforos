package com.alan.semaforosmx.widget

/**
 * Semáforo nuevo «armado» desde el widget, listo para sincronizar.
 *
 * Flujo de dos pasos (idea de Alan): el conductor está detenido en rojo
 * en un semáforo que la app no conoce, presiona «Aquí hay un semáforo»
 * y el widget queda en modo «listo para sync» (leyenda visible). El
 * semáforo NO se crea todavía: solo cuando el conductor presiona
 * «Sync verde» al cambiar a verde se crea el semáforo en la ubicación
 * guardada y se registra el sync en ese instante. Así el reporte de
 * tiempo queda en el momento del verde, no en el del rojo.
 *
 * Si el conductor nunca presiona sync (o cancela, o expira el tiempo),
 * no se crea nada.
 */
data class PendingNewLight(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val armedAtMillis: Long
)

/**
 * Estado del semáforo nuevo armado en el widget. Lógica pura (sin
 * Android) para poder probarla en unit tests.
 */
class PendingNewLightTracker(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {

    private var pending: PendingNewLight? = null

    val isArmed: Boolean
        get() = pending != null

    val current: PendingNewLight?
        get() = pending

    /** Arma el reporte. Devuelve false si ya había uno armado. */
    fun arm(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        nowMillis: Long
    ): Boolean {
        if (pending != null) return false
        pending = PendingNewLight(latitude, longitude, accuracyMeters, nowMillis)
        return true
    }

    /** Cancela sin crear nada. */
    fun cancel() {
        pending = null
    }

    /** Toma el reporte armado (y lo desarma) para crearlo al sincronizar. */
    fun consume(): PendingNewLight? {
        val result = pending
        pending = null
        return result
    }

    /** ¿El reporte armado expiró (pasó el tiempo sin sincronizar)? */
    fun isExpired(nowMillis: Long): Boolean {
        val armed = pending ?: return false
        return nowMillis - armed.armedAtMillis >= timeoutMillis
    }

    /**
     * ¿Un arranque en [startedAtMillis] completa automáticamente el
     * reporte armado? Sí si hay uno armado, el arranque ocurrió después
     * de armarlo y todavía no expiró. Es la detección pasiva de verde
     * para semáforos nuevos: el conductor armó el reporte detenido en
     * rojo y el arranque GPS marca el momento del verde.
     */
    fun shouldAutoCreateOnMovement(startedAtMillis: Long): Boolean {
        val armed = pending ?: return false
        if (startedAtMillis < armed.armedAtMillis) return false
        return !isExpired(startedAtMillis)
    }

    companion object {
        /** 5 minutos para presionar SYNC VERDE; si no, se cancela solo. */
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
