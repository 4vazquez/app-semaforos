package com.alan.semaforosmx.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la elección de ubicación para centrar el mapa (lógica pura,
 * sin clases de Android: los fix son MapLocationPicker.Candidate).
 * Se ejecutan con run_tests_sandbox.sh.
 */
class MapLocationPickerTest {

    private fun candidate(
        provider: String,
        ageMillis: Long = 0L,
        accuracyMeters: Float? = 10f,
        isMock: Boolean = false
    ) = MapLocationPicker.Candidate(
        provider = provider,
        latitude = 25.6866,
        longitude = -100.3161,
        ageMillis = ageMillis,
        accuracyMeters = accuracyMeters,
        isMock = isMock
    )

    @Test
    fun `GPS fresco gana sobre red fresca`() {
        val picked = MapLocationPicker.pickLastKnown(
            candidate("gps", ageMillis = 5_000L),
            candidate("network", ageMillis = 1_000L)
        )
        assertEquals("gps", picked?.provider)
    }

    @Test
    fun `GPS viejo cae a red fresca`() {
        val picked = MapLocationPicker.pickLastKnown(
            candidate("gps", ageMillis = 300_000L),
            candidate("network", ageMillis = 10_000L)
        )
        assertEquals("network", picked?.provider)
    }

    @Test
    fun `ambos viejos devuelven null para pedir fix fresco`() {
        assertNull(
            MapLocationPicker.pickLastKnown(
                candidate("gps", ageMillis = 300_000L),
                candidate("network", ageMillis = 600_000L)
            )
        )
    }

    @Test
    fun `sin candidatos devuelve null`() {
        assertNull(MapLocationPicker.pickLastKnown(null, null))
    }

    @Test
    fun `GPS simulado se ignora y cae a la red`() {
        val picked = MapLocationPicker.pickLastKnown(
            candidate("gps", ageMillis = 1_000L, isMock = true),
            candidate("network", ageMillis = 2_000L)
        )
        assertEquals("network", picked?.provider)
    }

    @Test
    fun `ambos simulados devuelven null`() {
        assertNull(
            MapLocationPicker.pickLastKnown(
                candidate("gps", ageMillis = 1_000L, isMock = true),
                candidate("network", ageMillis = 1_000L, isMock = true)
            )
        )
    }

    @Test
    fun `sin precision tambien sirve para centrar el mapa`() {
        assertTrue(
            MapLocationPicker.isUsable(
                candidate("gps", ageMillis = 1_000L, accuracyMeters = null)
            )
        )
    }

    @Test
    fun `en el limite de 2 minutos sigue sirviendo`() {
        assertTrue(
            MapLocationPicker.isUsable(
                candidate(
                    "gps",
                    ageMillis = MapLocationPicker.MAX_LAST_KNOWN_AGE_MILLIS
                )
            )
        )
    }

    @Test
    fun `un ms despues del limite ya no sirve`() {
        assertFalse(
            MapLocationPicker.isUsable(
                candidate(
                    "gps",
                    ageMillis = MapLocationPicker.MAX_LAST_KNOWN_AGE_MILLIS + 1
                )
            )
        )
    }

    @Test
    fun `edad negativa no sirve`() {
        assertFalse(MapLocationPicker.isUsable(candidate("gps", ageMillis = -1L)))
    }

    @Test
    fun `sin GPS usa la red fresca`() {
        val picked = MapLocationPicker.pickLastKnown(
            null,
            candidate("network", ageMillis = 3_000L)
        )
        assertEquals("network", picked?.provider)
    }
}
