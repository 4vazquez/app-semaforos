package com.alan.semaforosmx.data.repository

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
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Cliente REST de Supabase (PostgREST) para la base de datos colaborativa.
 *
 * STUB en el MVP: la clase compila y define el contrato completo, pero no
 * se activa hasta que Alan cree el proyecto de Supabase y ponga la URL y
 * la clave anónima en [RepositoryProvider]. Las tablas esperadas son:
 * `traffic_lights`, `timing_reports`, `light_timings`,
 * `user_contributions`, `removal_reports` y `user_trust` (ver SPEC.md §6).
 * La consulta geoespacial "semáforos cerca" debe implementarse en Supabase
 * como función RPC con PostGIS (`ST_DWithin`); aquí se usa el endpoint REST
 * con filtro de ciudad como aproximación inicial. Las transiciones de estado
 * (UNVERIFIED → VERIFIED con 3 confirmaciones, → REMOVED con 3 reportes de
 * retiro), el tope diario de 5 semáforos nuevos y el rechazo de saltos
 * imposibles deben implementarse en v1 como triggers/funciones de base de
 * datos (el cliente nunca es la única línea de defensa: cualquier tope
 * solo en el cliente se puede saltar con una app modificada); el stub solo
 * inserta los reportes y calcula los conteos en el cliente. La agregación
 * oficial (mediana ponderada por confianza) también vive en la base de
 * datos en v1; `light_timings` ya trae el resultado agregado.
 */
class SupabaseTimingRepository(
    supabaseUrl: String,
    supabaseAnonKey: String
) : TimingRepository {

    private val api: SupabaseApi

    init {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer $supabaseAnonKey")
                    .build()
                chain.proceed(request)
            }
            .build()
        api = Retrofit.Builder()
            .baseUrl(supabaseUrl.trimEnd('/') + "/rest/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApi::class.java)
    }

    override suspend fun lightsNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<TrafficLight> {
        // En v1: llamar a la función RPC PostGIS `lights_near(lat, lon, radius)`.
        // Aproximación del MVP: traer los de la ciudad y filtrar en el cliente.
        return lightsInCity("Monterrey")
            .filter { it.distanceToMeters(latitude, longitude) <= radiusMeters }
    }

    override suspend fun lightsInCity(city: String): List<TrafficLight> =
        api.lightsByCity("eq.$city").map { it.toDomain() }

    override suspend fun addLight(light: TrafficLight): TrafficLight {
        val unverified = light.copy(status = LightStatus.UNVERIFIED)
        api.insertLight(LightRow.fromDomain(unverified))
        return unverified
    }

    override suspend fun canSubmitNewLight(userId: String): Boolean {
        // Cuenta los semáforos que este usuario agregó hoy. En v1 un
        // trigger de base de datos hace cumplir el tope aunque el cliente
        // no lo respete.
        val today = InMemoryTimingRepository.currentDay()
        val added = api.lightsReportedBy("eq.$userId", "gte.$today")
        return added.size < MapCorrection.MAX_NEW_LIGHTS_PER_DAY
    }

    override suspend fun reportNewLightAt(
        latitude: Double,
        longitude: Double,
        userId: String,
        gpsAccuracyMeters: Float
    ): NewLightOutcome {
        val existing = lightsNear(latitude, longitude, MapCorrection.DEDUP_RADIUS_METERS)
            .minByOrNull { it.distanceToMeters(latitude, longitude) }
        if (existing != null) {
            if (existing.reportedBy == userId) {
                return NewLightOutcome(existing, NewLightStatus.ALREADY_REPORTED)
            }
            submitReport(
                TimingReport(
                    lightId = existing.id,
                    reportedAtMillis = System.currentTimeMillis(),
                    hourSlot = java.util.Calendar.getInstance()
                        .get(java.util.Calendar.HOUR_OF_DAY),
                    dayType = InMemoryTimingRepository.currentDayType(),
                    source = ReportSource.MANUAL_SYNC,
                    gpsAccuracyMeters = gpsAccuracyMeters,
                    userId = userId
                )
            )
            return NewLightOutcome(existing, NewLightStatus.CONFIRMED_EXISTING)
        }
        if (!canSubmitNewLight(userId)) {
            return NewLightOutcome(null, NewLightStatus.DAILY_CAP_REACHED)
        }
        val light = addLight(
            TrafficLight(
                latitude = latitude,
                longitude = longitude,
                reportedBy = userId
            )
        )
        submitReport(
            TimingReport(
                lightId = light.id,
                reportedAtMillis = System.currentTimeMillis(),
                hourSlot = java.util.Calendar.getInstance()
                    .get(java.util.Calendar.HOUR_OF_DAY),
                dayType = InMemoryTimingRepository.currentDayType(),
                source = ReportSource.MANUAL_SYNC,
                gpsAccuracyMeters = gpsAccuracyMeters,
                userId = userId
            )
        )
        return NewLightOutcome(light, NewLightStatus.CREATED)
    }

    override suspend fun reportRemoved(lightId: String, userId: String): RemovalOutcome {
        val existing = api.removalReportsFor("eq.$lightId")
        if (existing.any { it.userId == userId }) {
            val count = existing.mapNotNull { it.userId }.distinct().size
            return RemovalOutcome(count, alreadyReported = true, nowHidden = false)
        }
        api.insertRemovalReport(RemovalRow.fromDomain(RemovalReport(lightId = lightId, userId = userId)))
        val count = (existing.mapNotNull { it.userId } + userId).distinct().size
        // En v1 un trigger de base de datos pone status=REMOVED al llegar al
        // umbral; aquí se reporta el conteo y el cliente lo interpreta.
        return RemovalOutcome(
            distinctCount = count,
            alreadyReported = false,
            nowHidden = count >= MapCorrection.REPORTS_TO_REMOVE
        )
    }

    override suspend fun hasReportedRemoval(lightId: String, userId: String): Boolean =
        api.removalReportsFor("eq.$lightId", "eq.$userId").isNotEmpty()

    override suspend fun removalCount(lightId: String): Int =
        api.removalReportsFor("eq.$lightId").mapNotNull { it.userId }.distinct().size

    override suspend fun submitReport(report: TimingReport): Boolean {
        // El stub acepta siempre; en v1 la función de inserción de la base
        // de datos aplica el rechazo de saltos imposibles (ver SPEC.md §6.7).
        api.insertReport(ReportRow.fromDomain(report))
        return true
    }

    override suspend fun timingFor(
        lightId: String,
        hourSlot: Int,
        dayType: DayType
    ): LightTiming? {
        return api.timingFor("eq.$lightId", "eq.$hourSlot", "eq.${dayType.name}")
            .firstOrNull()?.toDomain()
    }

    override suspend fun contributionFor(userId: String, month: String): UserContribution {
        return api.contributionFor("eq.$userId", "eq.$month")
            .firstOrNull()?.toDomain()
            ?: UserContribution(userId, month)
    }

    override suspend fun recordContribution(
        userId: String,
        month: String,
        lightId: String
    ): UserContribution {
        val current = api.contributionFor("eq.$userId", "eq.$month").firstOrNull()
        val lightIds = (current?.lightIds.orEmpty() + lightId).distinct()
        val updated = UserContribution(userId, month, updateCount = lightIds.size)
        api.upsertContribution(ContributionRow(userId, month, lightIds))
        return updated
    }

    override suspend fun userTrust(userId: String): UserTrust {
        return api.trustFor("eq.$userId").firstOrNull()?.toDomain()
            ?: UserTrust(userId)
    }

    override suspend fun contributionHistory(
        userId: String,
        limit: Int
    ): List<ContributionHistoryEntry> {
        // Limitación del stub: ReportRow todavía no parsea el ISO 8601 de
        // `reported_at` (reportedAtMillis = 0 en v1), así que los syncs se
        // ordenan al final hasta que el parseo se implemente. Los agregados
        // y retiros traen sus propias marcas de tiempo.
        val entries = mutableListOf<ContributionHistoryEntry>()
        api.reportsByUser("eq.$userId", "eq.MANUAL_SYNC").forEach { row ->
            entries.add(
                ContributionHistoryEntry(
                    kind = HistoryKind.SYNC,
                    lightId = row.lightId,
                    lightName = null,
                    reportedAtMillis = row.toDomain().reportedAtMillis
                )
            )
        }
        api.lightsReportedBy("eq.$userId", "gte.1970-01-01").forEach { row ->
            entries.add(
                ContributionHistoryEntry(
                    kind = HistoryKind.LIGHT_ADDED,
                    lightId = row.id,
                    lightName = row.name,
                    reportedAtMillis = 0L
                )
            )
        }
        api.removalReportsByUser("eq.$userId").forEach { row ->
            entries.add(
                ContributionHistoryEntry(
                    kind = HistoryKind.LIGHT_REMOVED,
                    lightId = row.lightId,
                    lightName = null,
                    reportedAtMillis = 0L
                )
            )
        }
        return entries
            .sortedByDescending { it.reportedAtMillis }
            .take(limit)
    }

    /** Endpoints PostgREST. */
    interface SupabaseApi {
        @GET("traffic_lights")
        suspend fun lightsByCity(
            @Query("city") cityFilter: String,
            @Query("status") statusFilter: String = "neq.REMOVED",
            @Header("Accept") accept: String = "application/json"
        ): List<LightRow>

        @POST("traffic_lights")
        suspend fun insertLight(@Body light: LightRow)

        @GET("traffic_lights")
        suspend fun lightsReportedBy(
            @Query("reported_by") userFilter: String,
            @Query("created_at") createdFilter: String
        ): List<LightRow>

        @POST("timing_reports")
        suspend fun insertReport(@Body report: ReportRow)

        @GET("timing_reports")
        suspend fun reportsByUser(
            @Query("user_id") userFilter: String,
            @Query("source") sourceFilter: String
        ): List<ReportRow>

        @GET("removal_reports")
        suspend fun removalReportsFor(
            @Query("light_id") lightFilter: String,
            @Query("user_id") userFilter: String? = null
        ): List<RemovalRow>

        @GET("removal_reports")
        suspend fun removalReportsByUser(
            @Query("user_id") userFilter: String
        ): List<RemovalRow>

        @POST("removal_reports")
        suspend fun insertRemovalReport(@Body report: RemovalRow)

        @GET("light_timings")
        suspend fun timingFor(
            @Query("light_id") lightFilter: String,
            @Query("hour_slot") hourFilter: String,
            @Query("day_type") dayFilter: String
        ): List<TimingRow>

        @GET("user_contributions")
        suspend fun contributionFor(
            @Query("user_id") userFilter: String,
            @Query("month") monthFilter: String
        ): List<ContributionRow>

        @POST("user_contributions")
        suspend fun upsertContribution(
            @Body contribution: ContributionRow,
            @Header("Prefer") prefer: String = "resolution=merge-duplicates"
        )

        @GET("user_trust")
        suspend fun trustFor(
            @Query("user_id") userFilter: String
        ): List<TrustRow>
    }

    // --- Filas de la base de datos (DTOs) ---

    @JsonClass(generateAdapter = true)
    data class LightRow(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val name: String?,
        val city: String,
        val status: String?,
        @Json(name = "reported_by") val reportedBy: String?,
        @Json(name = "created_at") val createdAt: String?
    ) {
        fun toDomain() = TrafficLight(
            id = id,
            latitude = latitude,
            longitude = longitude,
            name = name,
            city = city,
            status = runCatching { LightStatus.valueOf(status ?: "VERIFIED") }
                .getOrDefault(LightStatus.VERIFIED),
            reportedBy = reportedBy
        )
        companion object {
            fun fromDomain(l: TrafficLight) =
                LightRow(
                    l.id, l.latitude, l.longitude, l.name, l.city,
                    l.status.name, l.reportedBy, null
                )
        }
    }

    @JsonClass(generateAdapter = true)
    data class RemovalRow(
        val id: String,
        @Json(name = "light_id") val lightId: String,
        @Json(name = "reported_at") val reportedAt: String?,
        @Json(name = "user_id") val userId: String?
    ) {
        fun toDomain() = RemovalReport(id, lightId, 0L, userId)
        companion object {
            fun fromDomain(r: RemovalReport) = RemovalRow(r.id, r.lightId, null, r.userId)
        }
    }

    @JsonClass(generateAdapter = true)
    data class ReportRow(
        val id: String,
        @Json(name = "light_id") val lightId: String,
        @Json(name = "reported_at") val reportedAt: String,
        @Json(name = "hour_slot") val hourSlot: Int,
        @Json(name = "day_type") val dayType: String,
        val source: String,
        @Json(name = "gps_accuracy_m") val gpsAccuracyMeters: Float,
        @Json(name = "user_id") val userId: String?
    ) {
        fun toDomain(): TimingReport = TimingReport(
            id = id,
            lightId = lightId,
            // PostgREST devuelve ISO 8601; el parseo completo queda para v1.
            reportedAtMillis = 0L,
            hourSlot = hourSlot,
            dayType = DayType.valueOf(dayType),
            source = com.alan.semaforosmx.data.model.ReportSource.valueOf(source),
            gpsAccuracyMeters = gpsAccuracyMeters,
            userId = userId
        )

        companion object {
            fun fromDomain(r: TimingReport): ReportRow {
                val iso = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.US
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date(r.reportedAtMillis))
                return ReportRow(
                    id = r.id,
                    lightId = r.lightId,
                    reportedAt = iso,
                    hourSlot = r.hourSlot,
                    dayType = r.dayType.name,
                    source = r.source.name,
                    gpsAccuracyMeters = r.gpsAccuracyMeters,
                    userId = r.userId
                )
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class TimingRow(
        @Json(name = "light_id") val lightId: String,
        @Json(name = "hour_slot") val hourSlot: Int,
        @Json(name = "day_type") val dayType: String,
        @Json(name = "green_offset_seconds") val greenOffsetSeconds: Int,
        @Json(name = "cycle_seconds") val cycleSeconds: Int?,
        @Json(name = "sample_count") val sampleCount: Int,
        val confidence: Float
    ) {
        fun toDomain() = LightTiming(
            lightId, hourSlot, DayType.valueOf(dayType),
            greenOffsetSeconds, cycleSeconds, sampleCount, confidence
        )
    }

    @JsonClass(generateAdapter = true)
    data class ContributionRow(
        @Json(name = "user_id") val userId: String,
        val month: String,
        /**
         * Semáforos distintos a los que el usuario aportó en el mes.
         * La cuota Colaborador cuenta distintos, no reportes totales
         * (anti-granja). En Postgres: columna text[].
         */
        @Json(name = "light_ids") val lightIds: List<String> = emptyList()
    ) {
        fun toDomain() = UserContribution(userId, month, updateCount = lightIds.distinct().size)
    }

    @JsonClass(generateAdapter = true)
    data class TrustRow(
        @Json(name = "user_id") val userId: String,
        @Json(name = "trust_score") val trustScore: Float,
        @Json(name = "confirmed_count") val confirmedCount: Int = 0,
        @Json(name = "rejected_count") val rejectedCount: Int = 0
    ) {
        fun toDomain() = UserTrust(userId, trustScore, confirmedCount, rejectedCount)
    }
}
