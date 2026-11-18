package com.alan.semaforosmx.widget

import com.alan.semaforosmx.data.model.UserContribution

/**
 * Modo del widget flotante en segundo plano (decisión de Alan, 18 nov 2026).
 *
 * - [FULL]: usuarios Colaborador o Plus. El widget completo: borde LED +
 *   botones «Sync verde» y «Hay un semáforo aquí» + slider manual de
 *   opacidad de los botones. Son los conductores que alimentan la base de
 *   datos; el widget es su herramienta de trabajo.
 * - [LED_ONLY]: usuarios normales (Gratis). En segundo plano solo actúa el
 *   LED: el borde luminoso con la cuenta regresiva cuando el semáforo está
 *   por cambiar. Sin botones, sin control de opacidad. La alerta es el
 *   servicio; los botones son el premio por aportar.
 *
 * El modo lo decide el nivel del usuario (ver [AdsManager]) y se
 * re-evalúa en el servicio cada 30 s y después de cada aporte: en cuanto
 * un usuario llega a los 20 semáforos distintos del mes, su widget pasa a
 * completo sin reiniciar nada.
 */
enum class WidgetMode {
    FULL,
    LED_ONLY
}

/** Decisión pura del modo de widget a partir del nivel del usuario. */
object WidgetModes {

    /**
     * Colaborador (20+ semáforos distintos al mes, sin anuncios) o Plus
     * (suscripción de pago) → widget completo. Cualquier otro → solo LED.
     */
    fun forContribution(contribution: UserContribution, isPlusUser: Boolean): WidgetMode =
        if (contribution.isAdFree || isPlusUser) WidgetMode.FULL else WidgetMode.LED_ONLY
}
