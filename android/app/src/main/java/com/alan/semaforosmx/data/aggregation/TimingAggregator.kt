package com.alan.semaforosmx.data.aggregation

import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.LightTiming
import com.alan.semaforosmx.data.model.TimingReport
import kotlin.math.abs
import kotlin.math.min

/**
 * Agregación de reportes de tiempo en un estimado usable por semáforo.
 *
 * Algoritmo (ver SPEC.md §6.4):
 * 1. Filtrar reportes de los últimos 30 días.
 * 2. Convertir cada reporte a "segundos dentro del minuto" (los ciclos de
 *    semáforo en México suelen ser submúltiplos de 60 s; si en v1 se infiere
 *    el ciclo real, se usa el módulo del ciclo).
 * 3. Rechazo de atípicos por IQR (±1.5 × rango intercuartílico).
 * 4. Mediana PONDERADA por la confianza del usuario que envió cada reporte
 *    (ver UserTrust): un conductor con historial confirmado pesa más que
 *    un usuario nuevo. Sin pesos, se comporta como la mediana clásica.
 * 5. Confianza = 0.5·volumen + 0.3·dispersión + 0.2·frescura. El volumen
 *    usa la muestra EFECTIVA (suma de pesos), no el conteo bruto.
 *
 * La misma lógica debe implementarse en el backend (función de Postgres en
 * Supabase) para el cálculo oficial; esta versión corre en el dispositivo
 * para predicción local sin red.
 */
object TimingAggregator {

    /** Antigüedad máxima de un reporte para contar (30 días). */
    const val MAX_REPORT_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** Reportes necesarios para confianza plena por volumen. */
    const val FULL_CONFIDENCE_SAMPLES = 20

    /** Vida media de la frescura (14 días). */
    const val FRESHNESS_HALF_LIFE_MILLIS = 14L * 24 * 60 * 60 * 1000

    /** Peso de un reporte sin usuario conocido (equivale a peso completo). */
    const val DEFAULT_WEIGHT = 1.0f

    /** Resultado de una agregación, con retroalimentación por reporte. */
    data class AggregationResult(
        val timing: LightTiming?,
        /** IDs de los reportes que sobrevivieron al rechazo de atípicos. */
        val acceptedReportIds: List<String>,
        /** IDs de los reportes rechazados como atípicos (discrepan con la multitud). */
        val rejectedReportIds: List<String>
    )

    fun aggregate(
        lightId: String,
        hourSlot: Int,
        dayType: DayType,
        reports: List<TimingReport>,
        nowMillis: Long = System.currentTimeMillis(),
        /** Peso por usuario (su confianza). Sin entrada: peso completo. */
        trustWeights: Map<String, Float> = emptyMap()
    ): LightTiming? =
        aggregateWithFeedback(lightId, hourSlot, dayType, reports, nowMillis, trustWeights).timing

    /**
     * Igual que [aggregate], pero además dice qué reportes fueron aceptados
     * y cuáles rechazados como atípicos, para que el repositorio ajuste la
     * confianza (trust) de cada usuario una sola vez por reporte.
     */
    fun aggregateWithFeedback(
        lightId: String,
        hourSlot: Int,
        dayType: DayType,
        reports: List<TimingReport>,
        nowMillis: Long = System.currentTimeMillis(),
        trustWeights: Map<String, Float> = emptyMap()
    ): AggregationResult {
        val fresh = reports.filter { nowMillis - it.reportedAtMillis <= MAX_REPORT_AGE_MILLIS }
        if (fresh.isEmpty()) return AggregationResult(null, emptyList(), emptyList())

        // Segundos dentro del minuto de cada reporte.
        val offsets = fresh.map { ((it.reportedAtMillis / 1000) % 60).toInt() }

        // Rechazo de atípicos por IQR, conservando qué reportes sobreviven.
        val bounds = outlierBounds(offsets)
        val keptIndices = if (bounds == null) {
            offsets.indices.toList()
        } else {
            offsets.indices
                .filter { offsets[it].toDouble() in bounds.first..bounds.second }
                .ifEmpty { offsets.indices.toList() }
        }
        val keptSet = keptIndices.toSet()
        val keptReports = keptIndices.map { fresh[it] }
        val rejectedReports = fresh.filterIndexed { index, _ -> index !in keptSet }
        val keptOffsets = keptIndices.map { offsets[it] }
        if (keptOffsets.isEmpty()) {
            return AggregationResult(null, emptyList(), fresh.map { it.id })
        }

        // Peso de cada reporte sobreviviente (confianza de quien lo envió).
        val weights = keptReports.map { report ->
            report.userId?.let { trustWeights[it] } ?: DEFAULT_WEIGHT
        }
        val median = weightedMedian(keptOffsets.zip(weights))
        val mad = median(keptOffsets.map { abs(it - median) })

        // Confianza por volumen: 20 reportes (efectivos, ponderados) = 1.0.
        val effectiveSamples = weights.sum()
        val volume = min(1f, effectiveSamples / FULL_CONFIDENCE_SAMPLES)
        // Confianza por dispersión: MAD de 0 s = 1.0, MAD de 10 s o más = 0.0.
        val dispersion = 1f - min(1f, mad / 10f)
        // Confianza por frescura: decaimiento exponencial con vida media de 14 días.
        val newestAge = nowMillis - fresh.maxOf { it.reportedAtMillis }
        val freshness = Math.pow(
            0.5,
            newestAge.toDouble() / FRESHNESS_HALF_LIFE_MILLIS
        ).toFloat()

        val confidence = 0.5f * volume + 0.3f * dispersion + 0.2f * freshness

        return AggregationResult(
            timing = LightTiming(
                lightId = lightId,
                hourSlot = hourSlot,
                dayType = dayType,
                greenOffsetSeconds = median,
                // La inferencia del ciclo real queda para v1 (requiere series
                // temporales más largas); por ahora se usa el minuto como ciclo base.
                cycleSeconds = 60,
                sampleCount = keptOffsets.size,
                confidence = confidence.coerceIn(0f, 1f),
                updatedAtMillis = nowMillis
            ),
            acceptedReportIds = keptReports.map { it.id },
            rejectedReportIds = rejectedReports.map { it.id }
        )
    }

    /**
     * Estima en cuántos segundos llegará el verde, dado el tiempo agregado.
     * Devuelve null si no hay predicción confiable.
     */
    fun secondsUntilGreen(timing: LightTiming, nowMillis: Long): Int? {
        if (!timing.isPredictive) return null
        val cycle = timing.cycleSeconds ?: 60
        val positionInCycle = ((nowMillis / 1000) % cycle).toInt()
        val offset = timing.greenOffsetSeconds % cycle
        var delta = offset - positionInCycle
        if (delta < 0) delta += cycle
        return delta
    }

    /** Mediana entera de una lista no vacía. */
    internal fun median(values: List<Int>): Int {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2
        }
    }

    /**
     * Mediana ponderada: el valor donde el peso acumulado cruza la mitad
     * del peso total. Con pesos uniformes reproduce exactamente la mediana
     * clásica (si el acumulado llega justo a la mitad en un elemento y hay
     * un siguiente valor, se promedian los dos, como con número par).
     */
    internal fun weightedMedian(pairs: List<Pair<Int, Float>>): Int {
        if (pairs.isEmpty()) return 0
        val sorted = pairs.sortedBy { it.first }
        val total = sorted.sumOf { it.second.toDouble() }
        var cumulative = 0.0
        for (i in sorted.indices) {
            cumulative += sorted[i].second
            if (cumulative >= total / 2) {
                if (kotlin.math.abs(cumulative - total / 2) < 1e-6 && i + 1 < sorted.size) {
                    return (sorted[i].first + sorted[i + 1].first) / 2
                }
                return sorted[i].first
            }
        }
        return sorted.last().first
    }

    /** Límites [Q1 − 1.5·IQR, Q3 + 1.5·IQR]; null si hay muy pocos datos. */
    internal fun outlierBounds(values: List<Int>): Pair<Double, Double>? {
        if (values.size < 4) return null // Muy pocos datos para filtrar.
        val sorted = values.sorted()
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3) / 4]
        val iqr = (q3 - q1).toDouble()
        return (q1 - 1.5 * iqr) to (q3 + 1.5 * iqr)
    }

    /** Descarta valores fuera de [Q1 − 1.5·IQR, Q3 + 1.5·IQR]. */
    internal fun rejectOutliers(values: List<Int>): List<Int> {
        val bounds = outlierBounds(values) ?: return values
        val kept = values.filter { it.toDouble() in bounds.first..bounds.second }
        return kept.ifEmpty { values }
    }
}
