package com.alan.semaforosmx.data.aggregation

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Pruebas de la mediana ponderada por confianza (SPEC.md §6.4 y §6.7).
 * Se ejecutan con: ./gradlew testDebugUnitTest
 */
class TrustWeightingTest {

    private fun report(
        userId: String,
        secondInMinute: Int,
        minutesAgo: Long = 60
    ): TimingReport {
        val now = System.currentTimeMillis()
        val baseMinute = (now / 60_000) * 60_000 - minutesAgo * 60_000
        return TimingReport(
            lightId = "light-1",
            reportedAtMillis = baseMinute + secondInMinute * 1000L,
            hourSlot = 8,
            dayType = DayType.WEEKDAY,
            source = ReportSource.MANUAL_SYNC,
            gpsAccuracyMeters = 10f,
            userId = userId
        )
    }

    @Test
    fun `sin pesos la mediana es la clasica`() {
        val reports = listOf(
            report("a", 10), report("b", 10),
            report("c", 40), report("d", 40)
        )
        val result = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, reports)
        assertNotNull(result)
        // Mediana clásica de [10, 10, 40, 40] = (10 + 40) / 2.
        assertEquals(25, result!!.greenOffsetSeconds)
    }

    @Test
    fun `usuarios confiables pesan mas que usuarios nuevos`() {
        val reports = listOf(
            report("confiable-1", 10), report("confiable-2", 10),
            report("nuevo-1", 40), report("nuevo-2", 40)
        )
        val weights = mapOf(
            "confiable-1" to 1.0f,
            "confiable-2" to 1.0f,
            "nuevo-1" to 0.1f,
            "nuevo-2" to 0.1f
        )
        val result = TimingAggregator.aggregate(
            "light-1", 8, DayType.WEEKDAY, reports, trustWeights = weights
        )
        assertNotNull(result)
        // Peso total 2.2; la mitad (1.1) se cruza en el segundo 10.
        assertEquals(10, result!!.greenOffsetSeconds)
    }

    @Test
    fun `la retroalimentacion identifica aceptados y rechazados`() {
        val reports = List(8) { report("good-$it", 30, minutesAgo = (it + 1).toLong()) } +
            listOf(report("bad", 2, 20), report("bad2", 58, 21))
        val result = TimingAggregator.aggregateWithFeedback(
            "light-1", 8, DayType.WEEKDAY, reports
        )
        assertNotNull(result.timing)
        assertEquals(8, result.acceptedReportIds.size)
        assertEquals(2, result.rejectedReportIds.size)
        assertEquals(30, result.timing!!.greenOffsetSeconds)
    }
}
