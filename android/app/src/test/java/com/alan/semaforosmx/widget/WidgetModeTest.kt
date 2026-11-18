package com.alan.semaforosmx.widget

import com.alan.semaforosmx.data.model.UserContribution
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas de la decisión del modo de widget (SPEC.md §5 y §9):
 * Colaborador o Plus → widget completo (botones + opacidad);
 * usuario normal → solo LED.
 */
class WidgetModeTest {

    @Test
    fun `usuario normal ve solo el LED`() {
        val contribution = UserContribution("u1", "2026-11", updateCount = 0)
        assertEquals(WidgetMode.LED_ONLY, WidgetModes.forContribution(contribution, false))
    }

    @Test
    fun `con 19 semaforos distintos todavia es solo LED`() {
        val contribution = UserContribution("u1", "2026-11", updateCount = 19)
        assertEquals(WidgetMode.LED_ONLY, WidgetModes.forContribution(contribution, false))
    }

    @Test
    fun `con 20 semaforos distintos el widget es completo`() {
        val contribution = UserContribution("u1", "2026-11", updateCount = 20)
        assertEquals(WidgetMode.FULL, WidgetModes.forContribution(contribution, false))
    }

    @Test
    fun `usuario Plus tiene widget completo sin aportes`() {
        val contribution = UserContribution("u1", "2026-11", updateCount = 0)
        assertEquals(WidgetMode.FULL, WidgetModes.forContribution(contribution, true))
    }
}
