package com.alan.semaforosmx.data.aggregation

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias del algoritmo de agregación (SPEC.md §6.4).
 * Se ejecutan con: ./gradlew testDebugUnitTest
 */
class TimingAggregatorTest {

    private fun reportAtSecond(secondInMinute: Int, minutesAgo: Long = 60): TimingReport {
        val now = System.currentTimeMillis()
        val baseMinute = (now / 60_000) * 60_000 - minutesAgo * 60_000
        return TimingReport(
            lightId = "light-1",
            reportedAtMillis = baseMinute + secondInMinute * 1000L,
            hourSlot = 8,
            dayType = DayType.WEEKDAY,
            source = ReportSource.MANUAL_SYNC,
            gpsAccuracyMeters = 10f
        )
    }

    @Test
    fun `sin reportes no hay timing`() {
        val result = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, emptyList())
        assertNull(result)
    }

    @Test
    fun `la mediana es el tiempo estimado del verde`() {
        // 10 reportes agrupados en el segundo 25 del minuto.
        val reports = List(10) { reportAtSecond(25, minutesAgo = (it + 1).toLong()) }
        val result = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, reports)
        assertNotNull(result)
        assertEquals(25, result!!.greenOffsetSeconds)
        assertEquals(10, result.sampleCount)
    }

    @Test
    fun `los atipicos no contaminan la mediana`() {
        // 8 reportes en el segundo 30 + 2 atípicos (segundos 2 y 58).
        val reports = List(8) { reportAtSecond(30, minutesAgo = (it + 1).toLong()) } +
            listOf(reportAtSecond(2, 20), reportAtSecond(58, 21))
        val result = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, reports)
        assertNotNull(result)
        assertEquals(30, result!!.greenOffsetSeconds)
        // Los 2 atípicos deben haber sido descartados.
        assertEquals(8, result.sampleCount)
    }

    @Test
    fun `mas reportes agrupados dan mas confianza`() {
        val few = List(3) { reportAtSecond(20, minutesAgo = (it + 1).toLong()) }
        val many = List(20) { reportAtSecond(20, minutesAgo = (it + 1).toLong()) }
        val low = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, few)!!
        val high = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, many)!!
        assertTrue(high.confidence > low.confidence)
        assertTrue(high.confidence >= 0.6f)
        assertTrue(high.isPredictive)
    }

    @Test
    fun `segundos hasta el verde con ciclo de 60s`() {
        val reports = List(20) { reportAtSecond(30, minutesAgo = (it + 1).toLong()) }
        val timing = TimingAggregator.aggregate("light-1", 8, DayType.WEEKDAY, reports)!!
        // Ahora: segundo 10 del minuto → faltan 20 s para el verde (segundo 30).
        val now = (System.currentTimeMillis() / 60_000) * 60_000 + 10_000
        assertEquals(20, TimingAggregator.secondsUntilGreen(timing, now))
    }
}
