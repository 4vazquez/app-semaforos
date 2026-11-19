package com.alan.semaforosmx.ui.contact

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Pruebas del canal de contacto (Ajustes → Contacto): la dirección debe
 * ser la cuenta del negocio y el `mailto:` debe llevar el asunto
 * precargado con codificación RFC 6068 (%20, nunca +).
 */
class ContactEmailTest {

    @Test
    fun `la direccion es la cuenta del negocio`() {
        assertEquals("adminsemaforosmx@gmail.com", ContactEmail.ADDRESS)
    }

    @Test
    fun `mailto lleva el asunto precargado codificado`() {
        assertEquals(
            "mailto:adminsemaforosmx@gmail.com" +
                "?subject=V%C3%ADa%20Verde%20%E2%80%94%20Contacto",
            ContactEmail.mailtoUriString()
        )
    }

    @Test
    fun `el mailto no usa mas como espacio`() {
        assertFalse(ContactEmail.mailtoUriString().contains("+"))
    }
}
