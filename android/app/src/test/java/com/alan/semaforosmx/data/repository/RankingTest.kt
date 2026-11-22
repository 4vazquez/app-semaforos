package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.WeekWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant

/**
 * Pruebas del ranking semanal de Colaboradores (SPEC §6.10):
 * ventana lunes-domingo America/Monterrey, conteo de semáforos
 * distintos, filtro de opt-in y desempates.
 */
class RankingTest {

    private fun report(
        lightId: String,
        userId: String,
        atMillis: Long,
        source: ReportSource = ReportSource.MANUAL_SYNC
    ): TimingReport = TimingReport(
        lightId = lightId,
        reportedAtMillis = atMillis,
        hourSlot = 8,
        dayType = DayType.WEEKDAY,
        source = source,
        gpsAccuracyMeters = 10f,
        userId = userId
    )

    private fun rankingWithOptIns(vararg users: Pair<String, String>): Pair<InMemoryTimingRepository, InMemoryRankingRepository> {
        val timing = InMemoryTimingRepository()
        val ranking = InMemoryRankingRepository(timing)
        runBlocking {
            users.forEach { (uid, nick) -> ranking.setOptIn(uid, nick, true) }
        }
        return timing to ranking
    }

    @Test
    fun `la semana empieza el lunes a medianoche en Monterrey`() {
        // Miércoles 18 nov 2026, 12:00 America/Monterrey (UTC-6).
        val wednesday = Instant.parse("2026-11-18T18:00:00Z").toEpochMilli()
        val start = WeekWindow.weekStartMillis(wednesday)
        val startDate = Instant.ofEpochMilli(start).atZone(WeekWindow.ZONE)
        assertEquals(DayOfWeek.MONDAY, startDate.dayOfWeek)
        assertEquals(0, startDate.hour)
        assertEquals(0, startDate.minute)
        assertEquals(0, startDate.second)
        // El lunes de esa semana es el 16 de noviembre.
        assertEquals(16, startDate.dayOfMonth)
        // Fin exclusivo: lunes siguiente a medianoche.
        assertEquals(start + 7L * 24 * 60 * 60 * 1000, WeekWindow.weekEndMillis(wednesday))
        assertTrue(WeekWindow.contains(start, wednesday))
        assertTrue(WeekWindow.contains(start + 6L * 24 * 60 * 60 * 1000, wednesday))
        assertFalse(WeekWindow.contains(start - 1, wednesday))
        assertFalse(WeekWindow.contains(start + 7L * 24 * 60 * 60 * 1000, wednesday))
    }

    @Test
    fun `cuenta semaforos distintos por usuario en la semana`() = runBlocking {
        val (timing, ranking) = rankingWithOptIns("user-a" to "Ana", "user-b" to "Beto")
        val start = WeekWindow.weekStartMillis()
        // Ana: l1 dos veces + l2 una vez = 2 distintos.
        timing.submitReport(report("l1", "user-a", start + 60_000L))
        timing.submitReport(report("l1", "user-a", start + 120_000L))
        timing.submitReport(report("l2", "user-a", start + 180_000L))
        // Beto: l1 una vez = 1 distinto.
        timing.submitReport(report("l1", "user-b", start + 240_000L))

        val top = ranking.topWeek(10)
        assertEquals(2, top.size)
        assertEquals("Ana", top[0].nickname)
        assertEquals(2, top[0].distinctLights)
        assertEquals("Beto", top[1].nickname)
        assertEquals(1, top[1].distinctLights)
    }

    @Test
    fun `ignora reportes pasivos y de la semana pasada`() = runBlocking {
        val (timing, ranking) = rankingWithOptIns("user-a" to "Ana")
        val start = WeekWindow.weekStartMillis()
        // Pasivo de esta semana: no cuenta.
        timing.submitReport(
            report("l1", "user-a", start + 60_000L, source = ReportSource.PASSIVE)
        )
        // Manual de la semana pasada: no cuenta.
        timing.submitReport(report("l2", "user-a", start - 60_000L))
        // Manual de esta semana: sí cuenta.
        timing.submitReport(report("l3", "user-a", start + 120_000L))

        val top = ranking.topWeek(10)
        assertEquals(1, top.size)
        assertEquals(1, top[0].distinctLights)
    }

    @Test
    fun `solo aparecen usuarios con opt-in y apodo`() = runBlocking {
        val timing = InMemoryTimingRepository()
        val ranking = InMemoryRankingRepository(timing)
        val start = WeekWindow.weekStartMillis()
        // Sin opt-in: no aparece.
        timing.submitReport(report("l1", "user-a", start + 60_000L))
        // Con opt-in pero sin apodo: no aparece.
        ranking.setOptIn("user-b", null, true)
        timing.submitReport(report("l1", "user-b", start + 60_000L))
        // Con opt-in y apodo: aparece.
        ranking.setOptIn("user-c", "Caro", true)
        timing.submitReport(report("l1", "user-c", start + 60_000L))

        val top = ranking.topWeek(10)
        assertEquals(1, top.size)
        assertEquals("Caro", top[0].nickname)
    }

    @Test
    fun `desempata por el primer aporte mas temprano`() = runBlocking {
        val (timing, ranking) = rankingWithOptIns("user-a" to "Ana", "user-b" to "Beto")
        val start = WeekWindow.weekStartMillis()
        // Ambos con 1 semáforo y confianza neutral (0.5): gana quien
        // aportó primero en la semana.
        timing.submitReport(report("l1", "user-a", start + 300_000L))
        timing.submitReport(report("l1", "user-b", start + 60_000L))

        val top = ranking.topWeek(10)
        assertEquals(2, top.size)
        assertEquals("Beto", top[0].nickname)
        assertEquals("Ana", top[1].nickname)
    }

    @Test
    fun `sin apodo no se activa la aparicion`() = runBlocking {
        val ranking = InMemoryRankingRepository(InMemoryTimingRepository())
        ranking.setOptIn("user-a", "   ", true)
        val optIn = ranking.optInFor("user-a")
        assertFalse(optIn.showInRanking)
        assertFalse(optIn.isVisible)

        ranking.setOptIn("user-a", "Ana", true)
        val optIn2 = ranking.optInFor("user-a")
        assertTrue(optIn2.showInRanking)
        assertTrue(optIn2.isVisible)
        assertEquals("Ana", optIn2.nickname)
    }

    @Test
    fun `el limite recorta el top`() = runBlocking {
        val (timing, ranking) = rankingWithOptIns(
            "user-a" to "Ana", "user-b" to "Beto", "user-c" to "Caro"
        )
        val start = WeekWindow.weekStartMillis()
        timing.submitReport(report("l1", "user-a", start + 60_000L))
        timing.submitReport(report("l2", "user-a", start + 70_000L))
        timing.submitReport(report("l1", "user-b", start + 80_000L))
        timing.submitReport(report("l1", "user-c", start + 90_000L))

        val top = ranking.topWeek(2)
        assertEquals(2, top.size)
        assertEquals("Ana", top[0].nickname)
        assertEquals("Beto", top[1].nickname)
    }
}
