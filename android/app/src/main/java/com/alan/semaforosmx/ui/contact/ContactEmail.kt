package com.alan.semaforosmx.ui.contact

import java.net.URLEncoder

/**
 * Canal de contacto del equipo de Vía Verde.
 *
 * - La dirección es la cuenta de Google del negocio, creada para la app
 *   (adminviaverdemx@gmail.com). Es también la dirección que se usará
 *   como contacto en la ficha de Google Play cuando se publique.
 * - Se muestra en Ajustes → Contacto, con acciones para copiarla o
 *   escribir por correo (ver SettingsScreen), y está nombrada en los
 *   Términos y Condiciones (§12).
 *
 * La construcción del `mailto:` es Kotlin puro (sin clases de Android)
 * para poder probarla en pruebas unitarias locales.
 */
object ContactEmail {

    /** Dirección de contacto del equipo (cuenta del negocio). */
    const val ADDRESS = "adminviaverdemx@gmail.com"

    /** Asunto precargado al escribir desde la app. */
    const val SUBJECT = "Vía Verde — Contacto"

    /**
     * URI `mailto:` con el asunto precargado (RFC 6068): los espacios se
     * codifican como `%20` (no `+`) y los caracteres no ASCII en UTF-8.
     */
    fun mailtoUriString(): String =
        "mailto:$ADDRESS?subject=${encodeQueryComponent(SUBJECT)}"

    private fun encodeQueryComponent(value: String): String =
        // URLEncoder es para formularios (espacio = +); en una URI el
        // espacio debe ser %20, así que se corrige después de codificar.
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
