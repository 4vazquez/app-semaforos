package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.ContributionHistoryEntry
import com.alan.semaforosmx.data.model.DayType
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

/**
 * Fuente de datos de semáforos y tiempos.
 *
 * Implementaciones:
 * - [InMemoryTimingRepository]: funciona sin servidor; es la activa en el MVP.
 * - [SupabaseTimingRepository]: cliente REST de Supabase (Postgres + PostGIS);
 *   se activa poniendo la URL y la clave anónima en [RepositoryProvider].
 */
interface TimingRepository {

    /** Semáforos dentro de un radio (en metros) de un punto. */
    suspend fun lightsNear(latitude: Double, longitude: Double, radiusMeters: Double): List<TrafficLight>

    /** Todos los semáforos de una ciudad (para poblar el mapa). */
    suspend fun lightsInCity(city: String): List<TrafficLight>

    /** Agrega un semáforo nuevo (reportado por un usuario). */
    suspend fun addLight(light: TrafficLight): TrafficLight

    /**
     * «Aquí hay un semáforo»: el conductor está detenido en un semáforo
     * que la app no conoce. Si no hay ningún semáforo cerca del punto,
     * crea uno en estado UNVERIFIED en la ubicación GPS dada Y registra el
     * sync de verde de este momento. Si YA hay uno cerca (menos de
     * [MapCorrection.DEDUP_RADIUS_METERS]), no duplica: registra el sync
     * sobre el existente (lo confirma si estaba UNVERIFIED). Si ESTE usuario
     * ya había reportado un semáforo en ese punto, no hace nada
     * (anti-spam: un usuario solo reporta cada semáforo una vez). Si el
     * usuario llegó al tope diario de semáforos nuevos
     * ([MapCorrection.MAX_NEW_LIGHTS_PER_DAY]), devuelve DAILY_CAP_REACHED.
     *
     * [reportedAtMillis] y [source] describen el momento y el origen del
     * reporte de tiempo incluido: en el flujo manual son «ahora» y
     * MANUAL_SYNC (valores por defecto); en la detección pasiva por
     * movimiento GPS son el instante del arranque y PASSIVE.
     */
    suspend fun reportNewLightAt(
        latitude: Double,
        longitude: Double,
        userId: String,
        gpsAccuracyMeters: Float,
        reportedAtMillis: Long = System.currentTimeMillis(),
        source: ReportSource = ReportSource.MANUAL_SYNC
    ): NewLightOutcome

    /**
     * ¿Puede este usuario agregar un semáforo nuevo hoy? (Tope diario:
     * [MapCorrection.MAX_NEW_LIGHTS_PER_DAY] por usuario por día.)
     * La app consulta esto antes del flujo de toque prolongado en el mapa.
     */
    suspend fun canSubmitNewLight(userId: String): Boolean

    /**
     * «Ya no existe»: reporta que un semáforo fue retirado en la vida real.
     * Un usuario solo puede reportar cada semáforo UNA vez (para siempre):
     * si ya lo había reportado, no se cuenta de nuevo y el resultado lo
     * indica con [RemovalOutcome.alreadyReported]. Al llegar a
     * [MapCorrection.REPORTS_TO_REMOVE] conductores distintos, el semáforo
     * se oculta del mapa (nunca se borra del todo).
     */
    suspend fun reportRemoved(lightId: String, userId: String): RemovalOutcome

    /** ¿Este usuario ya reportó retirado este semáforo? (para bloquear el botón). */
    suspend fun hasReportedRemoval(lightId: String, userId: String): Boolean

    /** Cuántos conductores distintos han reportado retirado un semáforo. */
    suspend fun removalCount(lightId: String): Int

    /**
     * Envía un reporte de tiempo (sync manual o pasivo). Devuelve true si
     * el reporte fue aceptado, false si el anti-abuso lo rechazó (salto
     * físicamente imposible: dos semáforos lejanos en pocos segundos).
     * Un reporte aceptado también cuenta como confirmación si el semáforo
     * estaba UNVERIFIED.
     */
    suspend fun submitReport(report: TimingReport): Boolean

    /** Tiempo agregado de un semáforo para una franja horaria y tipo de día. */
    suspend fun timingFor(lightId: String, hourSlot: Int, dayType: DayType): LightTiming?

    /** Contribución mensual del usuario (para el modo sin anuncios). */
    suspend fun contributionFor(userId: String, month: String): UserContribution

    /**
     * Registra una actualización en el contador mensual del usuario.
     * Cada acción cuenta igual (sync verde, semáforo agregado o retiro
     * reportado), pero la cuota cuenta SEMÁFOROS DISTINTOS al mes: las
     * actualizaciones repetidas sobre el mismo semáforo no suman de nuevo
     * (anti-granja: no se puede farmear el Colaborador en un solo cruce).
     * Con 20 semáforos distintos al mes el usuario es Colaborador.
     */
    suspend fun recordContribution(userId: String, month: String, lightId: String): UserContribution

    /**
     * Reputación (trust) actual de un usuario: puntaje 0.1–1.0 (empieza en
     * 0.5 neutral) + conteos de reportes confirmados y rechazados. La
     * pantalla «Mis aportes» lo muestra como nivel de confianza.
     */
    suspend fun userTrust(userId: String): UserTrust

    /**
     * Historial de aportes del usuario (syncs manuales, semáforos agregados
     * y retiros reportados), del más reciente al más antiguo, hasta
     * [limit] entradas. Los reportes pasivos de arranque NO aparecen: no
     * cuentan para la cuota Colaborador y no son aportes activos.
     */
    suspend fun contributionHistory(
        userId: String,
        limit: Int = 50
    ): List<ContributionHistoryEntry>
}
