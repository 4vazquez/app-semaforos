package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.RankingEntry
import com.alan.semaforosmx.data.model.RankingOptIn
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.WeekWindow
import java.util.concurrent.ConcurrentHashMap

/**
 * Ranking semanal en memoria (modo local / pruebas).
 *
 * Replica la lógica de la función SQL `top_collaborators_week`
 * (supabase/004_ranking.sql): solo cuentan los syncs MANUALES con
 * login de la semana en curso (lunes a domingo, America/Monterrey);
 * un semáforo cuenta una sola vez por semana aunque se sincronice
 * varias veces. Solo aparecen los usuarios con opt-in y apodo.
 * Desempate: mayor confianza, luego el primer aporte más temprano.
 *
 * La preferencia de aparición (apodo + mostrar) vive en un mapa en
 * memoria; en producción vive en la tabla `users` (nickname,
 * show_in_ranking).
 */
class InMemoryRankingRepository(
    private val timing: InMemoryTimingRepository
) : RankingRepository {

    private val optIns = ConcurrentHashMap<String, RankingOptIn>()

    override suspend fun topWeek(limit: Int): List<RankingEntry> {
        val now = System.currentTimeMillis()
        val start = WeekWindow.weekStartMillis(now)
        val end = start + 7L * 24 * 60 * 60 * 1000

        // Solo syncs manuales con login dentro de la semana.
        val weekReports = timing.allTimingReports().filter { report ->
            report.source == ReportSource.MANUAL_SYNC &&
                report.userId != null &&
                report.reportedAtMillis >= start &&
                report.reportedAtMillis < end
        }

        return weekReports
            .groupBy { it.userId!! }
            .mapNotNull { (userId, userReports) ->
                val optIn = optIns[userId] ?: return@mapNotNull null
                if (!optIn.isVisible) return@mapNotNull null
                RankingEntry(
                    userId = userId,
                    nickname = optIn.nickname!!.trim(),
                    distinctLights = userReports.map { it.lightId }.toSet().size,
                    trustScore = timing.trustFor(userId),
                    firstContributionMillis = userReports.minOf { it.reportedAtMillis }
                )
            }
            .sortedWith(
                compareByDescending<RankingEntry> { it.distinctLights }
                    .thenByDescending { it.trustScore }
                    .thenBy { it.firstContributionMillis }
            )
            .take(limit)
    }

    override suspend fun optInFor(userId: String): RankingOptIn =
        optIns[userId] ?: RankingOptIn()

    override suspend fun setOptIn(userId: String, nickname: String?, showInRanking: Boolean) {
        val clean = nickname?.trim()?.takeIf { it.isNotEmpty() }
        // Sin apodo no se puede activar la aparición (SPEC §6.10).
        val effectiveShow = showInRanking && clean != null
        optIns[userId] = RankingOptIn(nickname = clean, showInRanking = effectiveShow)
    }
}
