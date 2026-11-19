package com.alan.semaforosmx.widget

import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.TrafficLight

/** Clave de preferencia: modo prueba encendido o apagado. */
const val TEST_MODE_PREF_KEY = "test_mode"

/** Clave de preferencia: opciones de desarrollador desbloqueadas (7 toques). */
const val DEV_UNLOCK_PREF_KEY = "dev_unlock"

/**
 * Estado del LED en un instante del ciclo de prueba.
 *
 * @param ledState estado del borde LED.
 * @param secondsLeft segundos simulados hasta el verde (0 en verde).
 * @param showCountdown true si se muestra la cuenta regresiva.
 */
data class TestTimingState(
    val ledState: LedState,
    val secondsLeft: Int,
    val showCountdown: Boolean
)

/**
 * Ciclo de tiempos sintético del modo prueba: una máquina de estados pura
 * (sin Android, unit-testeable).
 *
 * El ciclo dura 30 s y se repite: el verde "llega" en el segundo 25, la
 * alerta empieza 3 s antes y el verde dura 5 s. Así el widget muestra los
 * tres estados (espera → alerta → verde) sin manejar ni esperar un
 * semáforo real.
 */
object TestTimingCycle {

    /** Duración del ciclo de prueba, en segundos. */
    const val CYCLE_SECONDS = 30L

    /** Segundo del ciclo en que llega el verde. */
    const val GREEN_AT_SECOND = 25L

    /** Segundos antes del verde en que empieza la alerta. */
    const val ALERT_SECONDS = 3L

    /**
     * Estado del ciclo a los [elapsedSeconds] segundos de haber empezado.
     * Acepta cualquier valor (los negativos se envuelven como si el ciclo
     * ya llevara rato corriendo).
     */
    fun stateAt(elapsedSeconds: Long): TestTimingState {
        val t = ((elapsedSeconds % CYCLE_SECONDS) + CYCLE_SECONDS) % CYCLE_SECONDS
        return when {
            t < GREEN_AT_SECOND - ALERT_SECONDS -> TestTimingState(
                LedState.WAITING,
                (GREEN_AT_SECOND - t).toInt(),
                true
            )
            t < GREEN_AT_SECOND -> TestTimingState(
                LedState.ALERT,
                (GREEN_AT_SECOND - t).toInt(),
                true
            )
            else -> TestTimingState(LedState.GREEN, 0, true)
        }
    }
}

/**
 * Selección de la ubicación simulada del modo prueba: lógica pura (sin
 * Android, unit-testeable). El servicio ([FloatingWidgetService]) usa
 * estas funciones y construye el `Location` de Android con el resultado.
 *
 * La simulación NO es una ubicación simulada del sistema (mock provider):
 * es un punto calculado dentro de la app que alimenta la misma ruta de
 * proximidad que el GPS real, así que no dispara la detección anti-abuso
 * de ubicación simulada — y nunca se envía como reporte.
 */
object TestModeSimulator {

    /** Punto por defecto si no hay semáforos cerca: Lincoln / Ruiz Cortines. */
    const val DEFAULT_LATITUDE = 25.7360
    const val DEFAULT_LONGITUDE = -100.3760

    /** Radio de búsqueda del semáforo de prueba, en metros. */
    const val SEARCH_RADIUS_METERS = 2000.0

    /** Distancia simulada al semáforo de prueba, en metros. */
    const val SIMULATED_DISTANCE_METERS = 30.0

    /** Metros por grado de latitud (aproximación suficiente para 30 m). */
    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    /**
     * Elige el semáforo de prueba: el VERIFICADO más cercano al punto de
     * referencia dentro de [maxDistanceMeters]. Los UNVERIFIED (grises) y
     * los REMOVED no sirven: el widget real tampoco da alertas de ellos.
     * Devuelve null si no hay ninguno.
     */
    fun selectTargetLight(
        candidates: List<TrafficLight>,
        refLatitude: Double,
        refLongitude: Double,
        maxDistanceMeters: Double
    ): TrafficLight? = candidates
        .filter { it.status == LightStatus.VERIFIED }
        .filter { it.distanceToMeters(refLatitude, refLongitude) <= maxDistanceMeters }
        .minByOrNull { it.distanceToMeters(refLatitude, refLongitude) }

    /**
     * Posición simulada (latitud, longitud): ~30 m al sur del semáforo,
     * dentro del radio de detección (40 m) y del de proximidad (150 m),
     * para que el widget aparezca y la cuenta regresiva se active.
     * Desplazamiento fijo y determinista (testeable), no aleatorio.
     */
    fun simulatedPosition(light: TrafficLight): Pair<Double, Double> {
        val offsetDegrees = SIMULATED_DISTANCE_METERS / METERS_PER_DEGREE_LATITUDE
        return (light.latitude - offsetDegrees) to light.longitude
    }
}
