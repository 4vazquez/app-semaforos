package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.VerificationStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pruebas de la verificación de conductor (sello Colaborador,
 * SPEC §6.9): una sola solicitud pendiente por usuario, consentimiento
 * registrado, reenvío tras rechazo y borrado de la foto al resolver.
 */
class VerificationTest {

    private val photo = byteArrayOf(1, 2, 3, 4)

    @Test
    fun `enviar crea una solicitud pendiente con consentimiento`() = runBlocking {
        val repo = InMemoryVerificationRepository()
        val request = repo.submit(
            userId = "user-a",
            licenseType = "C",
            photoBytes = photo,
            fileName = "license_1.jpg",
            consentAtMillis = 123456789L
        )
        assertEquals(VerificationStatus.PENDING, request.status)
        assertEquals("C", request.licenseType)
        assertEquals("user-a/license_1.jpg", request.photoPath)
        assertEquals(123456789L, request.consentAtMillis)
        assertTrue(repo.photoFor(request.id)!!.contentEquals(photo))

        val mine = repo.myRequests("user-a")
        assertEquals(1, mine.size)
        assertEquals(request.id, mine[0].id)
    }

    @Test
    fun `no permite dos solicitudes pendientes`() = runBlocking {
        val repo = InMemoryVerificationRepository()
        repo.submit("user-a", "C", photo, "a.jpg", 1L)
        try {
            repo.submit("user-a", "D", photo, "b.jpg", 2L)
            fail("Debería lanzar IllegalStateException")
        } catch (expected: IllegalStateException) {
            // Esperado: una sola pendiente por usuario.
        }
        assertEquals(1, repo.myRequests("user-a").size)
    }

    @Test
    fun `tras un rechazo se puede reenviar`() = runBlocking {
        val repo = InMemoryVerificationRepository()
        val first = repo.submit("user-a", "C", photo, "a.jpg", 1L)
        repo.resolveForTest(first.id, approved = false, reason = "Foto borrosa")
        Thread.sleep(5)
        val second = repo.submit("user-a", "C", photo, "b.jpg", 2L)

        val mine = repo.myRequests("user-a")
        assertEquals(2, mine.size)
        // Más reciente primero.
        assertEquals(second.id, mine[0].id)
        assertEquals(VerificationStatus.PENDING, mine[0].status)
        assertEquals(VerificationStatus.REJECTED, mine[1].status)
        assertEquals("Foto borrosa", mine[1].rejectionReason)
    }

    @Test
    fun `al aprobar se borra la foto`() = runBlocking {
        val repo = InMemoryVerificationRepository()
        val request = repo.submit("user-a", "D", photo, "a.jpg", 1L)
        repo.resolveForTest(request.id, approved = true)

        val resolved = repo.myRequests("user-a").first()
        assertEquals(VerificationStatus.APPROVED, resolved.status)
        assertNull(resolved.photoPath)
        assertNull(repo.photoFor(request.id))
    }

    @Test
    fun `las solicitudes son por usuario`() = runBlocking {
        val repo = InMemoryVerificationRepository()
        repo.submit("user-a", "C", photo, "a.jpg", 1L)
        repo.submit("user-b", "D", photo, "b.jpg", 2L)
        assertEquals(1, repo.myRequests("user-a").size)
        assertEquals(1, repo.myRequests("user-b").size)
        assertEquals(0, repo.myRequests("user-c").size)
    }
}
