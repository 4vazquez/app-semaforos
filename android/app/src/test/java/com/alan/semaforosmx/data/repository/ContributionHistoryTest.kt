package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.HistoryKind
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.model.TrustLevel
import com.alan.semaforosmx.data.model.UserTrust
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del historial de aportes y del nivel de confianza
 * (pantalla «Mis aportes», SPEC.md §5).
 * Se ejecutan con: ./gradlew testDebugUnitTest
 */
class ContributionHistoryTest {

    private fun report(lightId: String, userId: String, atMillis: Long): TimingReport =
        TimingReport(
            lightId = lightId,
            reportedAtMillis = atMillis,
            hourSlot = 8,
            dayType = DayType.WEEKDAY,
            source = ReportSource.MANUAL_SYNC,
            gpsAccuracyMeters = 10f,
            userId = userId
        )

    @Test
    fun `el historial combina syncs agregados y retiros del mas reciente al mas antiguo`() =
        runBlocking {
            val repo = InMemoryTimingRepository()
            repo.seedLights(
                listOf(
                    TrafficLight(
                        id = "l1", latitude = 25.6866, longitude = -100.3161,
                        name = "Lincoln / Ruiz Cortines"
                    ),
                    TrafficLight(id = "l2", latitude = 25.7000, longitude = -100.3161)
                )
            )
            val base = System.currentTimeMillis()
            // Sync manual (t0), semáforo agregado (t0+), retiro (t0+).
            // Pequeñas pausas para que cada aporte tenga marca de tiempo
            // distinta y el orden del historial sea determinista.
            repo.submitReport(report("l1", "user-a", atMillis = base))
            Thread.sleep(5)
            val added = repo.reportNewLightAt(
                25.7100, -100.3161, "user-a", 10f
            )
            assertEquals(
                com.alan.semaforosmx.data.model.NewLightStatus.CREATED,
                added.status
            )
            Thread.sleep(5)
            repo.reportRemoved("l2", "user-a")

            val history = repo.contributionHistory("user-a")
            // Sync en l1 + agregado + retiro = 3. (El sync interno que
            // reportNewLightAt intenta registrar se rechaza por anti-salto:
            // 2.6 km en milisegundos es físicamente imposible.)
            assertEquals(3, history.size)
            // El más reciente primero: el retiro (última acción).
            assertEquals(HistoryKind.LIGHT_REMOVED, history.first().kind)
            assertEquals("l2", history.first().lightId)
            // El sync en l1 trae el nombre del cruce.
            val sync = history.first { it.kind == HistoryKind.SYNC && it.lightId == "l1" }
            assertEquals("Lincoln / Ruiz Cortines", sync.lightName)
            // El agregado aparece como LIGHT_ADDED.
            assertTrue(history.any { it.kind == HistoryKind.LIGHT_ADDED })
            // Otro usuario no ve nada.
            assertTrue(repo.contributionHistory("user-b").isEmpty())
        }

    @Test
    fun `los reportes pasivos no aparecen en el historial`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(
            listOf(TrafficLight(id = "l1", latitude = 25.6866, longitude = -100.3161))
        )
        repo.submitReport(
            TimingReport(
                lightId = "l1",
                reportedAtMillis = System.currentTimeMillis(),
                hourSlot = 8,
                dayType = DayType.WEEKDAY,
                source = ReportSource.PASSIVE,
                gpsAccuracyMeters = 10f,
                userId = "user-a"
            )
        )
        assertTrue(repo.contributionHistory("user-a").isEmpty())
    }

    @Test
    fun `el historial respeta el limite`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(
            listOf(TrafficLight(id = "l1", latitude = 25.6866, longitude = -100.3161))
        )
        val base = System.currentTimeMillis()
        repeat(5) { i ->
            // Mismo semáforo, minutos distintos: no hay salto (misma ubicación).
            repo.submitReport(report("l1", "user-a", atMillis = base + i * 60_000L))
        }
        assertEquals(3, repo.contributionHistory("user-a", limit = 3).size)
    }

    @Test
    fun `userTrust nuevo es neutral y se puede consultar`() = runBlocking {
        val repo = InMemoryTimingRepository()
        val trust = repo.userTrust("user-nuevo")
        assertEquals(0.5f, trust.trustScore, 0.001f)
        assertEquals(0, trust.confirmedCount)
        assertEquals(0, trust.rejectedCount)
    }

    @Test
    fun `niveles de confianza por puntaje`() {
        assertEquals(TrustLevel.BUILDING, UserTrust("u", trustScore = 0.1f).level())
        assertEquals(TrustLevel.BUILDING, UserTrust("u", trustScore = 0.29f).level())
        assertEquals(TrustLevel.GOOD, UserTrust("u", trustScore = 0.3f).level())
        assertEquals(TrustLevel.GOOD, UserTrust("u", trustScore = 0.5f).level())
        assertEquals(TrustLevel.RELIABLE, UserTrust("u", trustScore = 0.6f).level())
        assertEquals(TrustLevel.RELIABLE, UserTrust("u", trustScore = 0.84f).level())
        assertEquals(TrustLevel.HIGHLY_RELIABLE, UserTrust("u", trustScore = 0.85f).level())
        assertEquals(TrustLevel.HIGHLY_RELIABLE, UserTrust("u", trustScore = 1.0f).level())
    }
}
