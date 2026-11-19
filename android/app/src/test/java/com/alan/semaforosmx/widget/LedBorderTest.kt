package com.alan.semaforosmx.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del degradado del LED (v1.1.2): intenso en la orilla y
 * difuminado hacia el centro. Sin Android: corren con JUnitCore.
 */
class LedBorderTest {

    @Test
    fun `el primer paso (la orilla) es totalmente opaco`() {
        assertEquals(1f, glowStepAlpha(0, 14), 0.001f)
    }

    @Test
    fun `el degradado se apaga paso a paso hacia el centro`() {
        val steps = 14
        var previous = 2f
        for (i in 0 until steps) {
            val alpha = glowStepAlpha(i, steps)
            assertTrue("paso $i debe ser más tenue que el anterior", alpha < previous)
            assertTrue("paso $i fuera de rango", alpha in 0f..1f)
            previous = alpha
        }
    }

    @Test
    fun `el último paso casi no se ve`() {
        assertTrue(glowStepAlpha(13, 14) < 0.1f)
    }
}
