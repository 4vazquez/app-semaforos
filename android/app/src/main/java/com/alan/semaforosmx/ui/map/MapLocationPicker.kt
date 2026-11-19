package com.alan.semaforosmx.ui.map

/**
 * Elección de la ubicación para centrar el mapa (lógica pura, testeable
 * sin clases de Android: los fix se representan como [Candidate]).
 *
 * Regla del botón «Mi ubicación» (v1.0):
 * 1. Si la última ubicación conocida del GPS es reciente, se usa tal cual
 *    (salto instantáneo, sin encender el GPS).
 * 2. Si no, se usa la última conocida de la red, si es reciente.
 * 3. Si ninguna sirve, hay que pedir un fix fresco (el llamador lo hace
 *    con LocationManager y un tiempo límite).
 *
 * A diferencia de los reportes (GpsVerification), centrar el mapa NO
 * exige precisión ≤ 50 m: un fix grueso también sirve para ubicarse en
 * el mapa. Lo que sí se exige: fix reciente (≤ 2 min) y no simulado.
 */
object MapLocationPicker {

    /**
     * Antigüedad máxima de una última ubicación conocida para usarla tal
     * cual (2 min). Más vieja que eso, el usuario pudo haberse movido
     * bastante: mejor pedir un fix fresco.
     */
    const val MAX_LAST_KNOWN_AGE_MILLIS = 120_000L

    /** Un fix de ubicación, sin depender de android.location.Location. */
    data class Candidate(
        val provider: String,
        val latitude: Double,
        val longitude: Double,
        /** Antigüedad del fix en ms (0 = justo ahora). */
        val ageMillis: Long,
        /** Precisión en metros, o null si el fix no la trae. */
        val accuracyMeters: Float?,
        /** ¿El fix viene de un proveedor simulado (ubicación falsa)? */
        val isMock: Boolean
    )

    /** ¿El fix sirve para centrar el mapa? Reciente y no simulado. */
    fun isUsable(candidate: Candidate?): Boolean {
        if (candidate == null) return false
        if (candidate.isMock) return false
        if (candidate.ageMillis < 0) return false // Reloj en el futuro: no fiable.
        return candidate.ageMillis <= MAX_LAST_KNOWN_AGE_MILLIS
    }

    /**
     * Elige la mejor última ubicación conocida: GPS si sirve, si no la
     * red. Devuelve null si ninguna sirve (hay que pedir un fix fresco).
     */
    fun pickLastKnown(gps: Candidate?, network: Candidate?): Candidate? {
        if (isUsable(gps)) return gps
        if (isUsable(network)) return network
        return null
    }
}
