package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.model.WeekWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la regla Plus fake GPS (SPEC §9): un reporte hecho con
 * ubicación simulada se guarda marcado pero NO cuenta para la cuota,
 * ni para el ranking, ni alimenta el modelo de tiempos, ni confirma
 * semáforos nuevos.
 */
class PlusSimulatedTest {

    private fun report(
        lightId: String,
        userId: String,
        atMillis: Long,
        simulated: Boolean = false
    ): TimingReport = TimingReport(
        lightId = lightId,
        reportedAtMillis = atMillis,
        hourSlot = 8,
        dayType = DayType.WEEKDAY,
        source = ReportSource.MANUAL_SYNC,
        gpsAccuracyMeters = 10f,
        userId = userId,
        simulated = simulated
    )

    private fun seedLight(repo: InMemoryTimingRepository, id: String, status: LightStatus = LightStatus.VERIFIED) {
        // addLight crea UNVERIFIED; para VERIFIED lo sembramos directo.
        repo.seedLights(
            listOf(
                TrafficLight(
                    id = id, latitude = 25.6866, longitude = -100.3161,
                    status = status
                )
            )
        )
    }

    @Test
    fun `un reporte simulado no alimenta el modelo de tiempos`() = runBlocking {
        val repo = InMemoryTimingRepository()
        seedLight(repo, "l1")
        val now = System.currentTimeMillis()
        repo.submitReport(report("l1", "user-a", now, simulated = true))

        // Solo hay un reporte simulado: sin tiempos agregados.
        val timing = repo.timingFor("l1", 8, DayType.WEEKDAY)
        assertNull(timing)

        // Con un reporte real sí hay tiempos.
        repo.submitReport(report("l1", "user-b", now))
        val timing2 = repo.timingFor("l1", 8, DayType.WEEKDAY)
        assertNotNull(timing2)
        assertEquals(1, timing2!!.sampleCount)
    }

    @Test
    fun `un reporte simulado no confirma un semaforo nuevo`() = runBlocking {
        val repo = InMemoryTimingRepository()
        // Semáforo nuevo UNVERIFIED (como lo crea reportNewLightAt).
        val outcome = repo.reportNewLightAt(
            25.6866, -100.3161, "user-a", 10f,
            simulated = true
        )
        assertEquals(
            com.alan.semaforosmx.data.model.NewLightStatus.CREATED,
            outcome.status
        )
        val light = outcome.light!!
        assertEquals(LightStatus.UNVERIFIED, light.status)

        // Tres usuarios distintos con reportes simulados: sigue UNVERIFIED.
        repo.submitReport(report(light.id, "user-b", System.currentTimeMillis(), simulated = true))
        repo.submitReport(report(light.id, "user-c", System.currentTimeMillis(), simulated = true))
        val lights = repo.lightsNear(25.6866, -100.3161, 100.0)
        assertEquals(LightStatus.UNVERIFIED, lights.first { it.id == light.id }.status)
    }

    @Test
    fun `un reporte simulado no envenena la base de saltos`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(
            listOf(
                TrafficLight(id = "l1", latitude = 25.6866, longitude = -100.3161),
                TrafficLight(id = "l2", latitude = 25.9000, longitude = -100.3161)
            )
        )
        val now = System.currentTimeMillis()
        // Simulado en l1 (lejos), luego real en l2 casi al mismo tiempo:
        // el salto imposible NO debe rechazar el reporte real.
        assertTrue(repo.submitReport(report("l1", "user-a", now, simulated = true)))
        assertTrue(repo.submitReport(report("l2", "user-a", now + 1000L)))
    }

    @Test
    fun `el ranking ignora reportes simulados`() = runBlocking {
        val timing = InMemoryTimingRepository()
        val ranking = InMemoryRankingRepository(timing)
        ranking.setOptIn("user-a", "Ana", true)
        val start = WeekWindow.weekStartMillis()
        // Solo reportes simulados: no aparece.
        timing.submitReport(report("l1", "user-a", start + 60_000L, simulated = true))
        timing.submitReport(report("l2", "user-a", start + 120_000L, simulated = true))
        assertTrue(ranking.topWeek(10).isEmpty())

        // Un reporte real sí la hace aparecer, con 1 semáforo.
        timing.submitReport(report("l1", "user-a", start + 180_000L))
        val top = ranking.topWeek(10)
        assertEquals(1, top.size)
        assertEquals(1, top[0].distinctLights)
    }

    @Test
    fun `reporte simulado se guarda marcado`() = runBlocking {
        val repo = InMemoryTimingRepository()
        seedLight(repo, "l1")
        val now = System.currentTimeMillis()
        assertTrue(repo.submitReport(report("l1", "user-a", now, simulated = true)))
        val stored = repo.allTimingReports().first { it.lightId == "l1" }
        assertTrue(stored.simulated)
    }
}
