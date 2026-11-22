package com.alan.semaforosmx.data

import android.content.Context

/**
 * Preferencias de Plus (SPEC §9): suscripción de pago con sin anuncios
 * + ubicación simulada (fake GPS) para uso personal.
 *
 * La compra (Play Billing) llega en una fase posterior; por ahora la
 * condición Plus vive en una preferencia local (`plus_enabled`, por
 * defecto apagada) que solo se activa desde las opciones de
 * desarrollador («Simular Plus») para probar el flujo. Cuando Play
 * Billing exista, esa preferencia la controlará la compra.
 *
 * Regla de integridad: un reporte hecho con ubicación simulada se
 * guarda marcado (`simulated`) pero NO cuenta para la cuota
 * Colaborador ni para el ranking, y no alimenta el modelo de tiempos.
 */
object PlusPrefs {

    private const val PREFS_NAME = "semaforos_mx_prefs"
    private const val KEY_PLUS_ENABLED = "plus_enabled"
    private const val KEY_FAKE_GPS = "plus_fake_gps"

    /** ¿El usuario tiene Plus? (sin Play Billing aún: pref local). */
    fun isPlus(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PLUS_ENABLED, false)

    /** Activa/desactiva la condición Plus (solo dev / Play Billing). */
    fun setPlus(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PLUS_ENABLED, enabled).apply()
        if (!enabled) {
            // Sin Plus no hay fake GPS.
            prefs(context).edit().putBoolean(KEY_FAKE_GPS, false).apply()
        }
    }

    /** ¿El toggle de ubicación simulada está activado (y hay Plus)? */
    fun isFakeGpsEnabled(context: Context): Boolean =
        isPlus(context) && prefs(context).getBoolean(KEY_FAKE_GPS, false)

    /** Activa/desactiva el uso de ubicación simulada (solo con Plus). */
    fun setFakeGps(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FAKE_GPS, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
