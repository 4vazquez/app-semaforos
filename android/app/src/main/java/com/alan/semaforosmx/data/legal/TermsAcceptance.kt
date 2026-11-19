package com.alan.semaforosmx.data.legal

import android.content.Context

/**
 * Aceptación de los Términos y Condiciones (primera apertura).
 *
 * - El texto completo vive en `assets/terminos_y_condiciones.md`, copia
 *   exacta de `TERMINOS_Y_CONDICIONES.md` del repo (v1.7, borrador).
 *   ⚠️ El texto es un BORRADOR: debe revisarlo un abogado antes del
 *   lanzamiento público (ver SPEC.md §10).
 * - El Aviso de Privacidad vive en `assets/aviso_de_privacidad.md`
 *   (copia exacta de `AVISO_DE_PRIVACIDAD.md`, v1.2) y puede leerse
 *   desde la pantalla de aceptación y desde Ajustes: el usuario acepta
 *   los Términos y el Aviso juntos, así que ambos deben estar
 *   disponibles antes de presionar «Acepto».
 * - La aceptación se guarda localmente (versión + fecha/hora). Si los
 *   términos cambian de versión ([CURRENT_VERSION]), el usuario debe
 *   aceptarlos de nuevo en el siguiente arranque.
 * - Sin aceptación no se usa la app: la pantalla principal no se muestra
 *   hasta que el usuario presiona «Acepto» (ver MainActivity).
 */
object TermsAcceptance {

    /** Versión vigente de los términos (debe coincidir con el documento). */
    const val CURRENT_VERSION = "1.7"

    /** Versión vigente del Aviso de Privacidad (informativa, no bloquea). */
    const val PRIVACY_VERSION = "1.2"

    private const val PREFS = "semaforos_mx_prefs"
    private const val KEY_TERMS_VERSION = "terms_accepted_version"
    private const val KEY_TERMS_ACCEPTED_AT = "terms_accepted_at_millis"

    /** Nombre del archivo de términos dentro de assets/. */
    const val ASSET_FILE = "terminos_y_condiciones.md"

    /** Nombre del archivo del Aviso de Privacidad dentro de assets/. */
    const val PRIVACY_ASSET_FILE = "aviso_de_privacidad.md"

    /** ¿El usuario ya aceptó la versión vigente de los términos? */
    fun isAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TERMS_VERSION, null) == CURRENT_VERSION
    }

    /** Guarda la aceptación (versión + momento exacto). */
    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TERMS_VERSION, CURRENT_VERSION)
            .putLong(KEY_TERMS_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Versión aceptada (null si nunca aceptó). */
    fun acceptedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TERMS_VERSION, null)

    /** Momento de la aceptación, en milisegundos (0 si nunca aceptó). */
    fun acceptedAtMillis(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_TERMS_ACCEPTED_AT, 0L)

    /** Lee el texto completo de los términos desde assets (verbatim). */
    fun loadTermsText(context: Context): String =
        context.assets.open(ASSET_FILE).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    /** Lee el texto completo del Aviso de Privacidad desde assets (verbatim). */
    fun loadPrivacyText(context: Context): String =
        context.assets.open(PRIVACY_ASSET_FILE).bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}
