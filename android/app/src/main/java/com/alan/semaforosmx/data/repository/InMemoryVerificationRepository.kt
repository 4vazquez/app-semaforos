package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.VerificationRequest
import com.alan.semaforosmx.data.model.VerificationStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Verificación en memoria (modo local / pruebas).
 *
 * Guarda las solicitudes en un mapa (la foto solo vive en memoria en
 * este modo; en producción vive en el bucket privado y se borra tras
 * la revisión). Respeta la regla de una sola solicitud pendiente por
 * usuario, igual que el índice parcial único de la base de datos.
 */
class InMemoryVerificationRepository : VerificationRepository {

    private val requestsByUser = ConcurrentHashMap<String, CopyOnWriteArrayList<VerificationRequest>>()

    /** Fotos en memoria (solo modo local; en producción van al bucket). */
    private val photos = ConcurrentHashMap<String, ByteArray>()

    override suspend fun myRequests(userId: String): List<VerificationRequest> =
        (requestsByUser[userId] ?: emptyList()).sortedByDescending { it.submittedAtMillis }

    override suspend fun submit(
        userId: String,
        licenseType: String,
        photoBytes: ByteArray,
        fileName: String,
        consentAtMillis: Long
    ): VerificationRequest {
        val list = requestsByUser.getOrPut(userId) { CopyOnWriteArrayList() }
        synchronized(list) {
            if (list.any { it.status == VerificationStatus.PENDING }) {
                throw IllegalStateException("Ya hay una solicitud en revisión")
            }
            val request = VerificationRequest(
                userId = userId,
                licenseType = licenseType,
                photoPath = "$userId/$fileName",
                status = VerificationStatus.PENDING,
                submittedAtMillis = System.currentTimeMillis(),
                consentAtMillis = consentAtMillis
            )
            list.add(request)
            photos[request.id] = photoBytes
            return request
        }
    }

    /** Foto guardada para una solicitud (solo pruebas / modo local). */
    fun photoFor(requestId: String): ByteArray? = photos[requestId]

    /** Simula la resolución del Admin (para pruebas del flujo completo). */
    fun resolveForTest(requestId: String, approved: Boolean, reason: String? = null) {
        requestsByUser.values.forEach { list ->
            val index = list.indexOfFirst { it.id == requestId }
            if (index >= 0) {
                val current = list[index]
                list[index] = current.copy(
                    status = if (approved) VerificationStatus.APPROVED else VerificationStatus.REJECTED,
                    reviewedAtMillis = System.currentTimeMillis(),
                    rejectionReason = if (approved) null else reason,
                    // La foto se borra tras la revisión (SPEC §6.9).
                    photoPath = null
                )
                photos.remove(requestId)
            }
        }
    }
}
