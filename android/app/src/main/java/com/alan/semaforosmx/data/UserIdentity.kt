package com.alan.semaforosmx.data

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Identidad anónima del usuario, ligada al dispositivo.
 *
 * - Una cuenta por dispositivo: el ID se deriva del ANDROID_ID del teléfono
 *   (con hash SHA-256; el ID crudo nunca sale del dispositivo ni se guarda
 *   en el backend). Reinstalar la app en el mismo teléfono recupera el
 *   mismo ID, así que no se puede farmear el programa Colaborador ni
 *   multiplicar confirmaciones creando «otra cuenta» en el mismo aparato.
 * - No está ligado a nombre, correo ni teléfono. Solo sirve para contar
 *   contribuciones (modo sin anuncios), aplicar el anti-spam (un reporte
 *   por semáforo por usuario) y ponderar reportes por confianza.
 * - Instalaciones viejas que ya guardaron un UUID aleatorio lo conservan
 *   (migrarlas cambiaría su historial de contribuciones); las nuevas
 *   instalaciones y cualquier reinstalación usan el ID del dispositivo.
 */
object UserIdentity {
    private const val PREFS = "semaforos_mx_prefs"
    private const val KEY_USER_ID = "user_id"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_USER_ID, null)?.let { return it }
        val androidId = runCatching {
            Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            )
        }.getOrNull()
        val id = if (!androidId.isNullOrBlank()) {
            "d-" + sha256Hex(androidId).take(32)
        } else {
            // Sin ANDROID_ID (dispositivo raro): UUID aleatorio como antes.
            UUID.randomUUID().toString()
        }
        prefs.edit().putString(KEY_USER_ID, id).apply()
        return id
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
