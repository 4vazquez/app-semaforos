package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.NewLightStatus
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de correcciones de mapa y anti-abuso del repositorio
 * (SPEC.md §4.4, §4.5, §6.1 y §6.7).
 * Se ejecutan con: ./gradlew testDebugUnitTest
 */
class MapCorrectionTest {

    private fun verifiedLight(id: String, lat: Double, lon: Double) =
        TrafficLight(id = id, latitude = lat, longitude = lon)

    private fun report(
        lightId: String,
        userId: String,
        atMillis: Long = System.currentTimeMillis(),
        secondInMinute: Int = 30
    ): TimingReport {
        val baseMinute = (atMillis / 60_000) * 60_000
        return TimingReport(
            lightId = lightId,
            reportedAtMillis = baseMinute + secondInMinute * 1000L,
            hourSlot = 8,
            dayType = DayType.WEEKDAY,
            source = ReportSource.MANUAL_SYNC,
            gpsAccuracyMeters = 10f,
            userId = userId
        )
    }

    @Test
    fun `tres confirmaciones verifican un semaforo agregado`() = runBlocking {
        val repo = InMemoryTimingRepository()
        val light = repo.addLight(TrafficLight(latitude = 25.6866, longitude = -100.3161))
        assertEquals(LightStatus.UNVERIFIED, light.status)

        repo.submitReport(report(light.id, "user-a"))
        repo.submitReport(report(light.id, "user-b"))
        var current = repo.lightsInCity("Monterrey").first { it.id == light.id }
        assertEquals(LightStatus.UNVERIFIED, current.status)

        repo.submitReport(report(light.id, "user-c"))
        current = repo.lightsInCity("Monterrey").first { it.id == light.id }
        assertEquals(LightStatus.VERIFIED, current.status)
    }

    @Test
    fun `el mismo usuario no cuenta dos veces para verificar`() = runBlocking {
        val repo = InMemoryTimingRepository()
        val light = repo.addLight(TrafficLight(latitude = 25.6866, longitude = -100.3161))
        // El mismo usuario reporta 3 veces: solo cuenta como 1 confirmación.
        repeat(3) { repo.submitReport(report(light.id, "user-a")) }
        val current = repo.lightsInCity("Monterrey").first { it.id == light.id }
        assertEquals(LightStatus.UNVERIFIED, current.status)
    }

    @Test
    fun `reportar cerca de un semaforo existente no duplica`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(listOf(verifiedLight("l1", 25.6866, -100.3161)))
        // ~10 m al este: dentro del radio de deduplicación (30 m).
        val outcome = repo.reportNewLightAt(25.6866, -100.3160, "user-a", 10f)
        assertEquals(NewLightStatus.CONFIRMED_EXISTING, outcome.status)
        assertEquals("l1", outcome.light!!.id)
        assertEquals(1, repo.lightsInCity("Monterrey").size)
    }

    @Test
    fun `un usuario solo reporta cada semaforo una vez`() = runBlocking {
        val repo = InMemoryTimingRepository()
        val first = repo.reportNewLightAt(25.6866, -100.3161, "user-a", 10f)
        assertEquals(NewLightStatus.CREATED, first.status)
        val second = repo.reportNewLightAt(25.6866, -100.3161, "user-a", 10f)
        assertEquals(NewLightStatus.ALREADY_REPORTED, second.status)
        assertEquals(1, repo.lightsInCity("Monterrey").size)
    }

    @Test
    fun `tres retiros distintos ocultan el semaforo`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(listOf(verifiedLight("l1", 25.6866, -100.3161)))
        repo.reportRemoved("l1", "user-a")
        repo.reportRemoved("l1", "user-b")
        assertEquals(1, repo.lightsNear(25.6866, -100.3161, 100.0).size)

        val third = repo.reportRemoved("l1", "user-c")
        assertTrue(third.nowHidden)
        assertEquals(3, third.distinctCount)
        assertTrue(repo.lightsNear(25.6866, -100.3161, 100.0).isEmpty())
    }

    @Test
    fun `el mismo usuario no retira dos veces el mismo semaforo`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(listOf(verifiedLight("l1", 25.6866, -100.3161)))
        val first = repo.reportRemoved("l1", "user-a")
        assertFalse(first.alreadyReported)
        val second = repo.reportRemoved("l1", "user-a")
        assertTrue(second.alreadyReported)
        assertEquals(1, second.distinctCount)
        assertEquals(1, repo.removalCount("l1"))
    }

    @Test
    fun `la cuota cuenta semaforos distintos no reportes`() = runBlocking {
        val repo = InMemoryTimingRepository()
        val month = InMemoryTimingRepository.currentMonth()
        repeat(5) { repo.recordContribution("user-a", month, "light-1") }
        repo.recordContribution("user-a", month, "light-2")
        val contribution = repo.contributionFor("user-a", month)
        assertEquals(2, contribution.updateCount)
        assertFalse(contribution.isAdFree)
    }

    @Test
    fun `tope diario de cinco semaforos nuevos`() = runBlocking {
        val repo = InMemoryTimingRepository()
        // 5 ubicaciones separadas por ~111 m (fuera del radio de deduplicación).
        // Nota: los syncs internos de estos reportes pueden ser rechazados por
        // el anti-salto (5 ubicaciones en milisegundos), pero la creación del
        // semáforo y el tope diario no dependen de eso.
        repeat(5) { i ->
            val outcome = repo.reportNewLightAt(
                25.6866 + i * 0.001, -100.3161, "user-a", 10f
            )
            assertEquals(NewLightStatus.CREATED, outcome.status)
        }
        val sixth = repo.reportNewLightAt(25.6866 + 5 * 0.001, -100.3161, "user-a", 10f)
        assertEquals(NewLightStatus.DAILY_CAP_REACHED, sixth.status)
        assertFalse(repo.canSubmitNewLight("user-a"))
        // Otro usuario sí puede (el tope es por usuario).
        assertTrue(repo.canSubmitNewLight("user-b"))
    }

    @Test
    fun `salto imposible entre semaforos lejanos se rechaza`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(
            listOf(
                verifiedLight("cerca", 25.6866, -100.3161),
                // ~4.8 km al norte.
                verifiedLight("lejos", 25.7300, -100.3161),
                // ~200 m al este del primero.
                verifiedLight("vecino", 25.6866, -100.3141)
            )
        )
        val now = System.currentTimeMillis()
        assertTrue(repo.submitReport(report("cerca", "user-a", atMillis = now)))
        // 4.8 km en 5 segundos: físicamente imposible → rechazado.
        assertFalse(repo.submitReport(report("lejos", "user-a", atMillis = now + 5_000)))
        // El rechazo no contamina el estado: un cruce vecino 90 s después
        // (200 m en 90 s ≈ 8 km/h) sí se acepta.
        assertTrue(repo.submitReport(report("vecino", "user-a", atMillis = now + 90_000)))
    }

    @Test
    fun `la confianza sube con reportes confirmados y baja con atipicos`() = runBlocking {
        val repo = InMemoryTimingRepository()
        repo.seedLights(listOf(verifiedLight("l1", 25.6866, -100.3161)))
        // 8 conductores en el segundo 30 + 1 atípico en el segundo 2.
        repeat(8) { i ->
            repo.submitReport(report("l1", "good-$i", secondInMinute = 30))
        }
        repo.submitReport(report("l1", "bad-1", secondInMinute = 2))

        repo.timingFor("l1", 8, DayType.WEEKDAY)

        assertTrue(repo.trustFor("good-0") > 0.5f)
        assertTrue(repo.trustFor("bad-1") < 0.5f)
        // Sin historial: neutral.
        assertEquals(0.5f, repo.trustFor("unknown"), 0.001f)

        // La agregación se recalcula cada segundo en el widget: el ajuste de
        // confianza de cada reporte se aplica UNA sola vez.
        val trustAfterFirst = repo.trustFor("good-0")
        repo.timingFor("l1", 8, DayType.WEEKDAY)
        assertEquals(trustAfterFirst, repo.trustFor("good-0"), 0.001f)
    }
}
