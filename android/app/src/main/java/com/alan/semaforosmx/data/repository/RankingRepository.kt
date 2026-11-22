package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.RankingEntry
import com.alan.semaforosmx.data.model.RankingOptIn

/**
 * Ranking semanal de Colaboradores (SPEC §6.10).
 *
 * Solo cuentan los aportes que cuentan para la cuota de §6.5: syncs
 * manuales con login y GPS verificado. Un semáforo cuenta una sola
 * vez por semana aunque se sincronice varias veces. Desempate: mayor
 * confianza, luego el primer aporte de la semana más temprano.
 *
 * Solo aparecen los usuarios con opt-in (apodo + mostrar). En beta
 * cerrada (decisión de Alan, 20 nov 2026) nadie aparece por defecto.
 */
interface RankingRepository {

    /**
     * Top [limit] de la semana en curso (lunes a domingo,
     * America/Monterrey), ordenado por semáforos distintos
     * descendente, confianza descendente y primer aporte ascendente.
     */
    suspend fun topWeek(limit: Int = 10): List<RankingEntry>

    /** Preferencia actual de aparición del usuario en el ranking. */
    suspend fun optInFor(userId: String): RankingOptIn

    /**
     * Guarda la preferencia de aparición. Con [showInRanking] en true
     * se requiere un apodo no vacío (si no, se ignora la activación).
     */
    suspend fun setOptIn(userId: String, nickname: String?, showInRanking: Boolean)
}
