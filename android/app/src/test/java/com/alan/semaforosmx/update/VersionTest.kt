package com.alan.semaforosmx.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de parseo y comparación de versiones (SPEC.md §13): los tags
 * de release deben ser vX.Y.Z y la comparación es numérica por partes.
 */
class VersionTest {

    @Test
    fun `parsea tag con v`() {
        assertEquals(AppVersion(0, 3, 0), AppVersion.parse("v0.3.0"))
    }

    @Test
    fun `parsea version sin v`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parse("1.2.3"))
    }

    @Test
    fun `parsea V mayuscula y espacios`() {
        assertEquals(AppVersion(0, 3, 0), AppVersion.parse("  V0.3.0  "))
    }

    @Test
    fun `version menor es menor`() {
        assertTrue(AppVersion.parse("v0.2.0")!! < AppVersion.parse("v0.3.0")!!)
    }

    @Test
    fun `version mayor es mayor`() {
        assertTrue(AppVersion.parse("v1.0.0")!! > AppVersion.parse("v0.9.9")!!)
    }

    @Test
    fun `versiones iguales son iguales`() {
        assertEquals(AppVersion.parse("v0.3.0"), AppVersion.parse("0.3.0"))
    }

    @Test
    fun `comparacion es numerica no lexicografica`() {
        // "10" > "9" aunque lexicográficamente "1" < "9".
        assertTrue(AppVersion.parse("v0.10.0")!! > AppVersion.parse("v0.9.0")!!)
    }

    @Test
    fun `el parche desempata`() {
        assertTrue(AppVersion.parse("v0.3.1")!! > AppVersion.parse("v0.3.0")!!)
    }

    @Test
    fun `nulo es invalido`() {
        assertNull(AppVersion.parse(null))
    }

    @Test
    fun `vacio es invalido`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
    }

    @Test
    fun `dos partes es invalido`() {
        assertNull(AppVersion.parse("v0.3"))
    }

    @Test
    fun `cuatro partes es invalido`() {
        assertNull(AppVersion.parse("v0.3.0.1"))
    }

    @Test
    fun `sufijo beta es invalido`() {
        assertNull(AppVersion.parse("v0.3.0-beta"))
    }

    @Test
    fun `texto es invalido`() {
        assertNull(AppVersion.parse("abc"))
        assertNull(AppVersion.parse("v0.3.x"))
    }

    @Test
    fun `negativos son invalidos`() {
        assertNull(AppVersion.parse("v0.-1.0"))
    }

    @Test
    fun `toString reconstruye la version`() {
        assertEquals("0.3.0", AppVersion.parse("v0.3.0").toString())
    }
}
