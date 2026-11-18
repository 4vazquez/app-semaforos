package com.alan.semaforosmx.sensing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas de la matemática pura de verificación GPS (SPEC.md §6.7).
 * Las funciones que toman un objeto Location de Android no se pueden probar
 * como pruebas unitarias locales (el android.jar es solo stubs); esas se
 * cubren con pruebas instrumentadas en v1.
 * Se ejecutan con: ./gradlew testDebugUnitTest
 */
class GpsVerificationTest {

    @Test
    fun `distancia cero en el mismo punto`() {
        assertEquals(
            0.0,
            GpsVerification.distanceMeters(25.6866, -100.3161, 25.6866, -100.3161),
            0.001
        )
    }

    @Test
    fun `miliesima de grado de longitud son unos 100 metros en Monterrey`() {
        // 0.001° de longitud a latitud 25.69° ≈ 100.3 m.
        val distance = GpsVerification.distanceMeters(
            25.6866, -100.3161, 25.6866, -100.3151
        )
        assertEquals(100.3, distance, 2.0)
    }

    @Test
    fun `el radio de reporte de 100 m cabe en el radio de deteccion de 40 m`() {
        // Invariante del diseño: un semáforo dentro del radio de detección
        // (40 m) siempre pasa la verificación de proximidad (100 m).
        assertEquals(100.0, GpsVerification.MAX_REPORT_DISTANCE_METERS, 0.001)
    }
}
