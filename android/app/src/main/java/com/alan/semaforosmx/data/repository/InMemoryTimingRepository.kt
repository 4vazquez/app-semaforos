package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.aggregation.TimingAggregator
import com.alan.semaforosmx.data.model.AntiAbuse
import com.alan.semaforosmx.data.model.ContributionHistoryEntry
import com.alan.semaforosmx.data.model.DayType
import com.alan.semaforosmx.data.model.HistoryKind
import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.LightTiming
import com.alan.semaforosmx.data.model.MapCorrection
import com.alan.semaforosmx.data.model.NewLightOutcome
import com.alan.semaforosmx.data.model.NewLightStatus
import com.alan.semaforosmx.data.model.RemovalOutcome
import com.alan.semaforosmx.data.model.RemovalReport
import com.alan.semaforosmx.data.model.ReportSource
import com.alan.semaforosmx.data.model.TimingReport
import com.alan.semaforosmx.data.model.TrafficLight
import com.alan.semaforosmx.data.model.UserContribution
import com.alan.semaforosmx.data.model.UserTrust
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación en memoria del repositorio. Es la activa en el MVP:
 * la app funciona completa sin servidor (los datos se pierden al cerrar
 * la app; en v1 se sustituye por [SupabaseTimingRepository]).
 *
 * La base semilla son los 1 579 semáforos reales del área metropolitana
 * de Monterrey, cargados desde el GeoJSON de OpenStreetMap
 * (ver [com.alan.semaforosmx.data.GeoJsonLightsLoader] y
 * [RepositoryProvider.init]).
 */
class InMemoryTimingRepository : TimingRepository {

    private val lights = ConcurrentHashMap<String, TrafficLight>()
    private val reports = ConcurrentHashMap<String, MutableList<TimingReport>>()
    /** Usuarios distintos que han reportado tiempo en cada semáforo UNVERIFIED. */
    private val confirmations = ConcurrentHashMap<String, MutableSet<String>>()
    /** Reportes de retiro por semáforo (quién reportó qué). */
    private val removalReports = ConcurrentHashMap<String, MutableList<RemovalReport>>()
    /** Semáforos distintos a los que cada usuario aportó en cada mes (cuota). */
    private val contributedLights = ConcurrentHashMap<String, MutableSet<String>>()
    /** Reputación (trust) por usuario; empieza neutral (0.5) para todos. */
    private val trust = ConcurrentHashMap<String, UserTrust>()
    /** Último reporte de cada usuario: (semáforo, momento). Anti-salto. */
    private val lastReportByUser = ConcurrentHashMap<String, Pair<TrafficLight, Long>>()
    /** Semáforos nuevos agregados por usuario y día (tope diario). */
    private val dailyAdds = ConcurrentHashMap<String, Int>()
    /** Reportes cuyo efecto en la confianza ya se aplicó (una sola vez). */
    private val trustProcessedReports = ConcurrentHashMap.newKeySet<String>()

    /**
     * Siembra la base con una lista de semáforos (p. ej. los del GeoJSON).
     * Se llama una sola vez al arrancar la app. Si la lista viene vacía
     * (falló la carga del GeoJSON), se usan 5 cruces de respaldo para
     * que el mapa no quede vacío.
     */
    fun seedLights(seed: List<TrafficLight>) {
        val effective = seed.ifEmpty { fallbackLights() }
        effective.forEach { lights[it.id] = it }
    }

    /** Cruces de respaldo de la zona norte de Monterrey (solo si falla el GeoJSON). */
    private fun fallbackLights(): List<TrafficLight> = listOf(
        TrafficLight(
            latitude = 25.7245, longitude = -100.3532,
            name = "Av. Lincoln / Av. Ruiz Cortines"
        ),
        TrafficLight(
            latitude = 25.7186, longitude = -100.3458,
            name = "Av. Lincoln / Av. Aztlán"
        ),
        TrafficLight(
            latitude = 25.7123, longitude = -100.3389,
            name = "Av. Ruiz Cortines / Av. Rangel Frías"
        ),
        TrafficLight(
            latitude = 25.7051, longitude = -100.3314,
            name = "Av. Lincoln / Av. Gonzalitos"
        ),
        TrafficLight(
            latitude = 25.6987, longitude = -100.3245,
            name = "Av. Constitución / Av. Gonzalitos"
        )
    )

    override suspend fun lightsNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<TrafficLight> =
        lights.values.filter {
            it.status != LightStatus.REMOVED &&
                it.distanceToMeters(latitude, longitude) <= radiusMeters
        }

    override suspend fun lightsInCity(city: String): List<TrafficLight> =
        lights.values.filter {
            it.status != LightStatus.REMOVED && it.city.equals(city, ignoreCase = true)
        }

    override suspend fun addLight(light: TrafficLight): TrafficLight {
        // Todo semáforo agregado por un usuario empieza UNVERIFIED (gris, sin
        // alertas): lo confirman otros conductores con sus reportes.
        val unverified = light.copy(status = LightStatus.UNVERIFIED)
        lights[unverified.id] = unverified
        // El tope diario cuenta cada semáforo nuevo agregado (botón y
        // toque prolongado comparten el mismo contador).
        light.reportedBy?.let { userId ->
            val key = "$userId|${currentDay()}"
            dailyAdds[key] = (dailyAdds[key] ?: 0) + 1
        }
        return unverified
    }

    override suspend fun canSubmitNewLight(userId: String): Boolean {
        val key = "$userId|${currentDay()}"
        return (dailyAdds[key] ?: 0) < MapCorrection.MAX_NEW_LIGHTS_PER_DAY
    }

    override suspend fun reportNewLightAt(
        latitude: Double,
        longitude: Double,
        userId: String,
        gpsAccuracyMeters: Float,
        reportedAtMillis: Long,
        source: ReportSource
    ): NewLightOutcome {
        val now = System.currentTimeMillis()
        // El reporte de tiempo va con el momento del verde: en el flujo
        // manual es «ahora»; en la detección pasiva es el instante del
        // arranque detectado por GPS.
        val reportCal = Calendar.getInstance().apply { timeInMillis = reportedAtMillis }
        val reportHourSlot = reportCal.get(Calendar.HOUR_OF_DAY)
        val reportDayType = when (reportCal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> DayType.SATURDAY
            Calendar.SUNDAY -> DayType.SUNDAY_HOLIDAY
            else -> DayType.WEEKDAY
        }
        // ¿Ya hay un semáforo en ese punto? No duplicar: el reporte se
        // convierte en un sync sobre el existente (lo confirma si es UNVERIFIED).
        val existing = lights.values
            .filter { it.status != LightStatus.REMOVED }
            .minByOrNull { it.distanceToMeters(latitude, longitude) }
            ?.takeIf { it.distanceToMeters(latitude, longitude) <= MapCorrection.DEDUP_RADIUS_METERS }
        if (existing != null) {
            // Anti-spam: este usuario ya reportó un semáforo en este punto.
            if (existing.reportedBy == userId) {
                return NewLightOutcome(existing, NewLightStatus.ALREADY_REPORTED)
            }
            submitReport(
                TimingReport(
                    lightId = existing.id,
                    reportedAtMillis = reportedAtMillis,
                    hourSlot = reportHourSlot,
                    dayType = reportDayType,
                    source = source,
                    gpsAccuracyMeters = gpsAccuracyMeters,
                    userId = userId
                )
            )
            return NewLightOutcome(existing, NewLightStatus.CONFIRMED_EXISTING)
        }
        // Tope diario: 5 semáforos nuevos por usuario por día.
        if (!canSubmitNewLight(userId)) {
            return NewLightOutcome(null, NewLightStatus.DAILY_CAP_REACHED)
        }
        val light = addLight(
            TrafficLight(
                latitude = latitude,
                longitude = longitude,
                reportedBy = userId,
                createdAtMillis = now
            )
        )
        // El conductor lo presiona al cambiar a verde: el reporte de tiempo
        // va incluido (es también la primera confirmación del semáforo).
        submitReport(
            TimingReport(
                lightId = light.id,
                reportedAtMillis = reportedAtMillis,
                hourSlot = reportHourSlot,
                dayType = reportDayType,
                source = source,
                gpsAccuracyMeters = gpsAccuracyMeters,
                userId = userId
            )
        )
        return NewLightOutcome(light, NewLightStatus.CREATED)
    }

    override suspend fun reportRemoved(lightId: String, userId: String): RemovalOutcome {
        val list = removalReports.getOrPut(lightId) { mutableListOf() }
        // Anti-spam: un mismo conductor solo cuenta una vez por semáforo,
        // para siempre (ni siquiera en otros meses).
        if (list.any { it.userId == userId }) {
            val count = list.mapNotNull { it.userId }.distinct().size
            return RemovalOutcome(
                distinctCount = count,
                alreadyReported = true,
                nowHidden = lights[lightId]?.status == LightStatus.REMOVED
            )
        }
        list.add(RemovalReport(lightId = lightId, userId = userId))
        val distinctCount = list.mapNotNull { it.userId }.distinct().size
        var nowHidden = false
        if (distinctCount >= MapCorrection.REPORTS_TO_REMOVE) {
            // Se oculta, no se borra: queda en la base para auditoría y por
            // si hay que restaurarlo (p. ej. reportes erróneos en masa).
            lights[lightId]?.let {
                lights[lightId] = it.copy(status = LightStatus.REMOVED)
                nowHidden = true
                // La multitud confirmó el retiro: sube la confianza de todos
                // los conductores que lo reportaron.
                list.mapNotNull { report -> report.userId }.distinct()
                    .forEach { reporter -> adjustTrust(reporter, confirmed = true) }
            }
        }
        return RemovalOutcome(distinctCount, alreadyReported = false, nowHidden)
    }

    override suspend fun hasReportedRemoval(lightId: String, userId: String): Boolean =
        removalReports[lightId].orEmpty().any { it.userId == userId }

    override suspend fun removalCount(lightId: String): Int =
        removalReports[lightId].orEmpty().mapNotNull { it.userId }.distinct().size

    override suspend fun submitReport(report: TimingReport): Boolean {
        val userId = report.userId
        // Anti-abuso: salto físicamente imposible. Si el último reporte de
        // este usuario fue en otro semáforo y llegar hasta aquí implicaría
        // ir a más de 180 km/h, el reporte se rechaza (ubicación falsa o
        // GPS brincando). No se guarda ni cuenta para nada.
        if (userId != null) {
            val previous = lastReportByUser[userId]
            val light = lights[report.lightId]
            if (previous != null && light != null) {
                val (prevLight, prevTime) = previous
                val dtSeconds = (report.reportedAtMillis - prevTime) / 1000.0
                val distance = prevLight.distanceToMeters(light.latitude, light.longitude)
                val maxSpeedMps = AntiAbuse.MAX_PLAUSIBLE_SPEED_KMH / 3.6
                val impossible = distance > 0 &&
                    (dtSeconds <= 0 || distance / dtSeconds > maxSpeedMps)
                if (impossible) return false
            }
            if (light != null) {
                lastReportByUser[userId] = light to report.reportedAtMillis
            }
        }

        reports.getOrPut(report.lightId) { mutableListOf() }.add(report)
        // Confirmación de semáforos UNVERIFIED: cada usuario distinto que
        // reporta tiempo en el semáforo es un voto de que existe.
        if (userId != null) {
            val confirmedBy = confirmations.getOrPut(report.lightId) { mutableSetOf() }
            confirmedBy.add(userId)
            val light = lights[report.lightId]
            if (light?.status == LightStatus.UNVERIFIED &&
                confirmedBy.size >= MapCorrection.CONFIRMATIONS_TO_VERIFY
            ) {
                lights[report.lightId] = light.copy(status = LightStatus.VERIFIED)
                // La multitud confirmó que el semáforo existe: sube la
                // confianza de todos los que ayudaron a verificarlo.
                confirmedBy.forEach { confirmer -> adjustTrust(confirmer, confirmed = true) }
            }
        }
        return true
    }

    override suspend fun timingFor(
        lightId: String,
        hourSlot: Int,
        dayType: DayType
    ): LightTiming? {
        val all = reports[lightId].orEmpty()
            .filter { it.hourSlot == hourSlot && it.dayType == dayType }
        val weights = all.mapNotNull { it.userId }.distinct()
            .associateWith { trustFor(it) }
        val result = TimingAggregator.aggregateWithFeedback(
            lightId, hourSlot, dayType, all,
            trustWeights = weights
        )
        // Ajuste de confianza: cada reporte influye UNA sola vez (el widget
        // recalcula la agregación cada segundo; sin esta guarda, la misma
        // evidencia subiría la confianza sin parar). Los aceptados suben,
        // los rechazados como atípicos bajan.
        val byId = all.associateBy { it.id }
        result.acceptedReportIds.forEach { id ->
            if (trustProcessedReports.add(id)) {
                byId[id]?.userId?.let { adjustTrust(it, confirmed = true) }
            }
        }
        result.rejectedReportIds.forEach { id ->
            if (trustProcessedReports.add(id)) {
                byId[id]?.userId?.let { adjustTrust(it, confirmed = false) }
            }
        }
        return result.timing
    }

    /** Confianza actual de un usuario (0.5 neutral si no tiene historial). */
    fun trustFor(userId: String): Float =
        trust[userId]?.trustScore ?: UserTrust.NEUTRAL_TRUST

    /** Ajusta la confianza de un usuario por un reporte confirmado o rechazado. */
    private fun adjustTrust(userId: String, confirmed: Boolean) {
        val current = trust[userId] ?: UserTrust(userId)
        val delta = if (confirmed) UserTrust.CONFIRMED_BUMP else -UserTrust.REJECTED_PENALTY
        trust[userId] = current.copy(
            trustScore = (current.trustScore + delta)
                .coerceIn(UserTrust.MIN_TRUST, UserTrust.MAX_TRUST),
            confirmedCount = current.confirmedCount + if (confirmed) 1 else 0,
            rejectedCount = current.rejectedCount + if (confirmed) 0 else 1
        )
    }

    /** Todos los reportes de tiempo (para el ranking semanal, SPEC §6.10). */
    fun allTimingReports(): List<TimingReport> = reports.values.flatMap { it }

    override suspend fun contributionFor(userId: String, month: String): UserContribution {
        val count = contributedLights["$userId|$month"].orEmpty().size
        return UserContribution(userId, month, updateCount = count)
    }

    override suspend fun recordContribution(
        userId: String,
        month: String,
        lightId: String
    ): UserContribution {
        // La cuota cuenta SEMÁFOROS DISTINTOS: repetir el mismo cruce no suma.
        contributedLights.getOrPut("$userId|$month") { mutableSetOf() }.add(lightId)
        return contributionFor(userId, month)
    }

    override suspend fun userTrust(userId: String): UserTrust =
        trust[userId] ?: UserTrust(userId)

    override suspend fun contributionHistory(
        userId: String,
        limit: Int
    ): List<ContributionHistoryEntry> {
        val entries = mutableListOf<ContributionHistoryEntry>()
        // Syncs manuales (los pasivos no cuentan para la cuota ni aparecen).
        reports.values.flatten()
            .filter { it.userId == userId && it.source == ReportSource.MANUAL_SYNC }
            .forEach { report ->
                entries.add(
                    ContributionHistoryEntry(
                        kind = HistoryKind.SYNC,
                        lightId = report.lightId,
                        lightName = lights[report.lightId]?.name,
                        reportedAtMillis = report.reportedAtMillis
                    )
                )
            }
        // Semáforos agregados por este usuario.
        lights.values
            .filter { it.reportedBy == userId }
            .forEach { light ->
                entries.add(
                    ContributionHistoryEntry(
                        kind = HistoryKind.LIGHT_ADDED,
                        lightId = light.id,
                        lightName = light.name,
                        reportedAtMillis = light.createdAtMillis
                    )
                )
            }
        // Retiros reportados por este usuario.
        removalReports.values.flatten()
            .filter { it.userId == userId }
            .forEach { removal ->
                entries.add(
                    ContributionHistoryEntry(
                        kind = HistoryKind.LIGHT_REMOVED,
                        lightId = removal.lightId,
                        lightName = lights[removal.lightId]?.name,
                        reportedAtMillis = removal.reportedAtMillis
                    )
                )
            }
        return entries
            .sortedByDescending { it.reportedAtMillis }
            .take(limit)
    }

    companion object {
        /** Mes actual en formato "yyyy-MM". */
        fun currentMonth(): String {
            val cal = Calendar.getInstance()
            return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }

        /** Día actual en formato "yyyy-MM-dd" (para el tope diario). */
        fun currentDay(): String {
            val cal = Calendar.getInstance()
            return "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        /** Tipo de día actual (semana / sábado / domingo). */
        fun currentDayType(): DayType {
            return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> DayType.SATURDAY
                Calendar.SUNDAY -> DayType.SUNDAY_HOLIDAY
                else -> DayType.WEEKDAY
            }
        }
    }
}
