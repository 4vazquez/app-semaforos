package com.alan.semaforosmx.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Ranking semanal de Colaboradores (diseño aprobado por Alan el
 * 19 nov 2026, SPEC §6.10).
 *
 * Reconocimiento semanal (no competencia formal): cada semana, los
 * usuarios con opt-in (apodo + mostrar en el ranking) se ordenan por
 * semáforos distintos aportados. El top 3 recibe premios de
 * $300/$200/$100 MXN. La cuota mensual de 20 distintos (§6.5) es
 * independiente: es lo que conserva el beneficio sin anuncios.
 *
 * En beta cerrada (decisión de Alan, 20 nov 2026): nadie aparece por
 * defecto; solo entran usuarios por invitación.
 */

/** Una fila del ranking: apodo + semáforos distintos de la semana. */
data class RankingEntry(
    val userId: String,
    /** Apodo visible (nunca nombre real ni correo). */
    val nickname: String,
    /** Semáforos distintos aportados en la semana. */
    val distinctLights: Int,
    /** Confianza del usuario (desempate). */
    val trustScore: Float,
    /** Primer aporte de la semana (desempate final). */
    val firstContributionMillis: Long
)

/** Preferencia de aparición en el ranking (opt-in). */
data class RankingOptIn(
    val nickname: String? = null,
    val showInRanking: Boolean = false
) {
    /** Aparece en el ranking solo con opt-in Y apodo. */
    val isVisible: Boolean get() = showInRanking && !nickname.isNullOrBlank()
}

/**
 * Ventana semanal del ranking: de lunes 00:00 a domingo 23:59 en
 * America/Monterrey (SPEC §6.10). Un solo lugar para calcularla.
 */
object WeekWindow {

    val ZONE: ZoneId = ZoneId.of("America/Monterrey")

    /** Instante del lunes 00:00 (America/Monterrey) de la semana de [nowMillis]. */
    fun weekStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val date = Instant.ofEpochMilli(nowMillis).atZone(ZONE).toLocalDate()
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(ZONE).toInstant().toEpochMilli()
    }

    /** Instante del lunes siguiente (fin exclusivo de la ventana). */
    fun weekEndMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        weekStartMillis(nowMillis) + 7L * 24 * 60 * 60 * 1000

    /** ¿El instante cae dentro de la semana de [nowMillis]? */
    fun contains(millis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val start = weekStartMillis(nowMillis)
        return millis >= start && millis < start + 7L * 24 * 60 * 60 * 1000
    }
}
