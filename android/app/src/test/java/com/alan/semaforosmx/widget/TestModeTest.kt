package com.alan.semaforosmx.widget

import com.alan.semaforosmx.data.model.LightStatus
import com.alan.semaforosmx.data.model.TrafficLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del modo prueba (v0.3.3): la máquina de estados del ciclo de
 * tiempos sintético y la selección de la ubicación simulada. Ambas son
 * lógica pura (sin Android), así que corren en la JVM.
 */
class TestModeTest {

    // --- Ciclo de tiempos: 30 s, verde en el segundo 25, alerta 3 s antes. ---

    @Test
    fun `al inicio del ciclo espera con 25 s`() {
        val state = TestTimingCycle.stateAt(0)
        assertEquals(LedState.WAITING, state.ledState)
        assertEquals(25, state.secondsLeft)
        assertTrue(state.showCountdown)
    }

    @Test
    fun `a los 10 s espera con 15 s`() {
        val state = TestTimingCycle.stateAt(10)
        assertEquals(LedState.WAITING, state.ledState)
        assertEquals(15, state.secondsLeft)
    }

    @Test
    fun `a los 21 s todavia espera con 4 s`() {
        val state = TestTimingCycle.stateAt(21)
        assertEquals(LedState.WAITING, state.ledState)
        assertEquals(4, state.secondsLeft)
    }

    @Test
    fun `a los 22 s alerta con 3 s`() {
        val state = TestTimingCycle.stateAt(22)
        assertEquals(LedState.ALERT, state.ledState)
        assertEquals(3, state.secondsLeft)
        assertTrue(state.showCountdown)
    }

    @Test
    fun `a los 24 s alerta con 1 s`() {
        val state = TestTimingCycle.stateAt(24)
        assertEquals(LedState.ALERT, state.ledState)
        assertEquals(1, state.secondsLeft)
    }

    @Test
    fun `a los 25 s verde`() {
        val state = TestTimingCycle.stateAt(25)
        assertEquals(LedState.GREEN, state.ledState)
        assertEquals(0, state.secondsLeft)
    }

    @Test
    fun `a los 29 s sigue verde`() {
        val state = TestTimingCycle.stateAt(29)
        assertEquals(LedState.GREEN, state.ledState)
    }

    @Test
    fun `a los 30 s el ciclo reinicia en espera`() {
        val state = TestTimingCycle.stateAt(30)
        assertEquals(LedState.WAITING, state.ledState)
        assertEquals(25, state.secondsLeft)
    }

    @Test
    fun `a los 55 s verde otra vez`() {
        // 55 % 30 = 25: segundo ciclo, mismo instante de verde.
        val state = TestTimingCycle.stateAt(55)
        assertEquals(LedState.GREEN, state.ledState)
    }

    @Test
    fun `tiempo negativo no rompe el ciclo`() {
        // -1 envuelve al segundo 29 del ciclo: verde.
        val state = TestTimingCycle.stateAt(-1)
        assertEquals(LedState.GREEN, state.ledState)
    }

    // --- Selección del semáforo de prueba. ---

    private fun light(
        id: String,
        lat: Double,
        lon: Double,
        status: LightStatus = LightStatus.VERIFIED
    ) = TrafficLight(id = id, latitude = lat, longitude = lon, status = status)

    // Lincoln / Ruiz Cortines, punto por defecto del modo prueba.
    private val refLat = TestModeSimulator.DEFAULT_LATITUDE
    private val refLon = TestModeSimulator.DEFAULT_LONGITUDE

    @Test
    fun `elige el verificado mas cercano dentro del radio`() {
        val near = light("cerca", refLat + 0.001, refLon) // ~111 m
        val far = light("lejos", refLat + 0.01, refLon) // ~1,1 km
        val chosen = TestModeSimulator.selectTargetLight(
            listOf(far, near), refLat, refLon, 2000.0
        )
        assertEquals("cerca", chosen?.id)
    }

    @Test
    fun `ignora semaforos sin verificar y retirados aunque esten mas cerca`() {
        val unverified = light("gris", refLat + 0.0005, refLon, LightStatus.UNVERIFIED)
        val removed = light("retirado", refLat + 0.0006, refLon, LightStatus.REMOVED)
        val verified = light("ok", refLat + 0.002, refLon)
        val chosen = TestModeSimulator.selectTargetLight(
            listOf(unverified, removed, verified), refLat, refLon, 2000.0
        )
        assertEquals("ok", chosen?.id)
    }

    @Test
    fun `fuera del radio no elige nada`() {
        val far = light("lejos", refLat + 0.05, refLon) // ~5,5 km
        val chosen = TestModeSimulator.selectTargetLight(
            listOf(far), refLat, refLon, 2000.0
        )
        assertNull(chosen)
    }

    @Test
    fun `sin candidatos no elige nada`() {
        val chosen = TestModeSimulator.selectTargetLight(
            emptyList(), refLat, refLon, 2000.0
        )
        assertNull(chosen)
    }

    @Test
    fun `la posicion simulada queda a unos 30 m del semaforo`() {
        val target = light("t", refLat, refLon)
        val (lat, lon) = TestModeSimulator.simulatedPosition(target)
        val distance = target.distanceToMeters(lat, lon)
        assertTrue("distancia $distance m, esperada ~30 m", distance in 29.0..31.0)
    }

    @Test
    fun `la posicion simulada esta al sur del semaforo`() {
        val target = light("t", refLat, refLon)
        val (lat, lon) = TestModeSimulator.simulatedPosition(target)
        assertTrue(lat < target.latitude)
        assertEquals(target.longitude, lon, 0.0)
    }
}
