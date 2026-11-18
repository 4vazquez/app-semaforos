package com.alan.semaforosmx.data.model

import java.util.UUID

/**
 * Estado de verificación de un semáforo en el mapa.
 * El mapa se autocompleta con reportes de conductores, pero nada se da por
 * hecho con un solo reporte: todo lo que agrega un usuario empieza en
 * UNVERIFIED (marcador gris, sin alertas) y solo se confirma (o se retira)
 * con varios conductores independientes (ver SPEC.md §4.4, §4.5 y §6.1).
 */
enum class LightStatus {
    /** En el mapa y con datos de confianza: viene de OSM o ya lo confirmaron. */
    VERIFIED,
    /**
     * Reportado por un conductor; aún sin confirmar por otros. Se muestra
     * con marcador GRIS y el widget NO sirve alertas de él hasta que
     * [MapCorrection.CONFIRMATIONS_TO_VERIFY] conductores distintos lo
     * confirmen (anti-abuso: un solo reporte no crea un semáforo real).
     */
    UNVERIFIED,
    /** Retirado: varios conductores confirmaron que ya no existe. Oculto. */
    REMOVED
}

/**
 * Un semáforo registrado en el mapa.
 * La ubicación se guarda con 6 decimales (~11 cm de precisión).
 */
data class TrafficLight(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    /** Nombre opcional del cruce, p. ej. "Av. Lincoln / Ruiz Cortines". */
    val name: String? = null,
    /** Ciudad, para crecer fuera de Monterrey sin cambiar el modelo. */
    val city: String = "Monterrey",
    /** Estado de verificación (ver [LightStatus]). */
    val status: LightStatus = LightStatus.VERIFIED,
    /** Usuario anónimo que lo reportó (solo para luces UNVERIFIED). */
    val reportedBy: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    /** Distancia aproximada en metros a un punto (fórmula haversine). */
    fun distanceToMeters(lat: Double, lon: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat - latitude)
        val dLon = Math.toRadians(lon - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/** Tipo de día: los tiempos de los semáforos varían entre semana y fin de semana. */
enum class DayType {
    WEEKDAY, SATURDAY, SUNDAY_HOLIDAY
}

/** Origen del reporte de tiempo. */
enum class ReportSource {
    /** El conductor presionó el botón "Sync verde". */
    MANUAL_SYNC,
    /** Detección pasiva: el coche arrancó (acelerómetro/GPS). */
    PASSIVE
}

/**
 * Un reporte de tiempo: "este semáforo se puso en verde en este instante".
 * Los reportes son anónimos: [userId] es un UUID generado en el dispositivo
 * y solo sirve para contar contribuciones, nunca se publica ligado a un reporte.
 */
data class TimingReport(
    val id: String = UUID.randomUUID().toString(),
    val lightId: String,
    /** Momento exacto del verde, en milisegundos (UTC). */
    val reportedAtMillis: Long,
    /** Franja horaria 0-23 (hora local del reporte). */
    val hourSlot: Int,
    val dayType: DayType,
    val source: ReportSource,
    /** Precisión del GPS en metros al momento del reporte. */
    val gpsAccuracyMeters: Float,
    val userId: String? = null
)

/**
 * Tiempos agregados de un semáforo para una franja horaria y tipo de día.
 * Es el resultado del algoritmo de [com.alan.semaforosmx.data.aggregation.TimingAggregator].
 */
data class LightTiming(
    val lightId: String,
    val hourSlot: Int,
    val dayType: DayType,
    /** Segundos desde el inicio del minuto en que suele llegar el verde (mediana). */
    val greenOffsetSeconds: Int,
    /** Duración estimada del ciclo en segundos, si se pudo inferir. */
    val cycleSeconds: Int?,
    /** Número de reportes usados tras el rechazo de atípicos. */
    val sampleCount: Int,
    /** Confianza 0.0–1.0. Ver SPEC.md §6.4. */
    val confidence: Float,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    /** ¿El dato es lo bastante bueno para mostrar una cuenta regresiva? */
    val isPredictive: Boolean get() = confidence >= 0.6f

    /** ¿Vale la pena mostrarlo como "tiempo aproximado"? */
    val isApproximate: Boolean get() = confidence in 0.3f..<0.6f
}

/**
 * Reporte de que un semáforo ya no existe («Ya no existe»).
 * Un solo reporte nunca retira un semáforo: se acumulan por semáforo y
 * solo se oculta cuando varios conductores independientes lo confirman
 * (ver [MapCorrection] y SPEC.md §4.5).
 */
data class RemovalReport(
    val id: String = UUID.randomUUID().toString(),
    val lightId: String,
    val reportedAtMillis: Long = System.currentTimeMillis(),
    val userId: String? = null
)

/**
 * Reglas de corrección del mapa (las tres acciones usan la misma lógica:
 * nada cambia con un solo reporte; todo se confirma con varios
 * conductores independientes).
 */
object MapCorrection {
    /** Conductores distintos que deben confirmar un semáforo UNVERIFIED. */
    const val CONFIRMATIONS_TO_VERIFY = 3

    /** Conductores distintos que deben reportar un retiro para ocultarlo. */
    const val REPORTS_TO_REMOVE = 3

    /**
     * Radio de deduplicación (metros): si ya hay un semáforo a menos de
     * esta distancia del punto reportado, se considera el MISMO semáforo
     * (no se crea un duplicado; el reporte confirma/sincroniza el existente).
     */
    const val DEDUP_RADIUS_METERS = 30.0

    /** Máximo de semáforos nuevos que un usuario puede agregar por día. */
    const val MAX_NEW_LIGHTS_PER_DAY = 5
}

/**
 * Umbrales del paquete anti-abuso (ver SPEC.md §6.7). Todos viven en un
 * solo lugar para ajustarlos con datos de campo sin tocar la lógica.
 */
object AntiAbuse {
    /**
     * Velocidad máxima físicamente plausible entre dos reportes del mismo
     * usuario (180 km/h). Si dos reportes implican moverse más rápido que
     * eso, el segundo se rechaza: nadie teletransporta entre cruces.
     */
    const val MAX_PLAUSIBLE_SPEED_KMH = 180.0

    /**
     * Salto GPS sospechoso con el teléfono detenido: si el sensor de
     * movimiento dice que el coche está quieto pero el GPS salta más de
     * 100 m en menos de 30 s, el fix no es fiable y el reporte se rechaza.
     */
    const val STATIONARY_JUMP_METERS = 100.0
    const val STATIONARY_JUMP_WINDOW_MILLIS = 30_000L
}

/**
 * Reputación de un usuario (anónimo, ligado a su dispositivo).
 *
 * La confianza empieza NEUTRAL (0.5): los reportes de un usuario nuevo
 * pesan, pero menos que los de un conductor con historial confirmado.
 * Cada reporte que la multitud confirma sube la confianza; cada reporte
 * que sistemáticamente discrepa con la multitud la baja. La agregación
 * de tiempos pondera cada reporte por la confianza de quien lo envió
 * (ver [com.alan.semaforosmx.data.aggregation.TimingAggregator]).
 *
 * El mínimo es 0.1, no 0: un usuario que se equivocó puede recuperarse
 * con reportes buenos. Nunca se publica la confianza de nadie.
 */
data class UserTrust(
    val userId: String,
    /** Confianza 0.1–1.0. Empieza en 0.5 (neutral). */
    val trustScore: Float = NEUTRAL_TRUST,
    /** Reportes confirmados por la multitud (o verificaciones acertadas). */
    val confirmedCount: Int = 0,
    /** Reportes rechazados como atípicos frente a la multitud. */
    val rejectedCount: Int = 0
) {
    companion object {
        const val NEUTRAL_TRUST = 0.5f
        const val MIN_TRUST = 0.1f
        const val MAX_TRUST = 1.0f
        /** Subida por reporte confirmado. */
        const val CONFIRMED_BUMP = 0.03f
        /** Bajada por reporte que discrepa con la multitud. */
        const val REJECTED_PENALTY = 0.05f
    }
}

/** Resultado de «Aquí hay un semáforo». */
enum class NewLightStatus {
    /** Se creó un semáforo UNVERIFIED nuevo y se registró el sync. */
    CREATED,
    /** Ya existía un semáforo ahí: se registró el sync sobre él (lo confirma). */
    CONFIRMED_EXISTING,
    /** Este usuario ya había reportado un semáforo en ese punto. */
    ALREADY_REPORTED,
    /** El usuario llegó al tope diario de semáforos nuevos (5/día). */
    DAILY_CAP_REACHED
}

/** Resultado de «Aquí hay un semáforo»: el semáforo y qué pasó. */
data class NewLightOutcome(
    val light: TrafficLight?,
    val status: NewLightStatus
)

/** Resultado de «Ya no existe». */
data class RemovalOutcome(
    /** Conductores distintos que lo han reportado (incluido este, si es nuevo). */
    val distinctCount: Int,
    /** true si este usuario ya lo había reportado antes (no se contó de nuevo). */
    val alreadyReported: Boolean,
    /** true si con este reporte el semáforo quedó oculto del mapa. */
    val nowHidden: Boolean
)

/**
 * Contribución mensual de un usuario (anónimo).
 *
 * Cada actualización cuenta igual: un sync verde, un semáforo agregado
 * («Aquí hay un semáforo») o un retiro reportado («Ya no existe») suman
 * 1 al contador. Con 20 o más actualizaciones en el mes el usuario es
 * Colaborador: usa la app sin anuncios gratis, sin pagar nunca (su pago
 * son los datos que aporta). Ver SPEC.md §9.
 *
 * La cuota cuenta SEMÁFOROS DISTINTOS al mes (no reportes totales): repetir
 * el mismo cruce no suma. Y hay una cuenta por dispositivo: el [userId]
 * está ligado al dispositivo (ver UserIdentity), así que reinstalar la app
 * o crear «otra cuenta» en el mismo teléfono no duplica la cuota ni las
 * confirmaciones (los reportes del mismo dispositivo nunca cuentan como
 * confirmaciones independientes entre sí).
 */
data class UserContribution(
    val userId: String,
    /** Mes en formato "2026-11". */
    val month: String,
    /** Actualizaciones en el mes: syncs + agregados + retiros reportados. */
    val updateCount: Int = 0
) {
    companion object {
        /** Actualizaciones necesarias al mes para ser Colaborador (sin anuncios). */
        const val AD_FREE_THRESHOLD = 20
    }

    val isAdFree: Boolean get() = updateCount >= AD_FREE_THRESHOLD
}
