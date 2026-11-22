package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.VerificationRequest

/**
 * Solicitudes de verificación de conductor (sello Colaborador,
 * SPEC §6.9; tabla `verification_requests` + bucket privado
 * `license-photos`, supabase/003_verification.sql).
 *
 * El usuario sube la foto de su licencia con consentimiento expreso;
 * el Admin la revisa una sola vez y la foto se borra tras la
 * revisión. Solo puede haber UNA solicitud pendiente por usuario
 * (índice parcial único en la base de datos).
 */
interface VerificationRepository {

    /** Historial del usuario, más reciente primero. */
    suspend fun myRequests(userId: String): List<VerificationRequest>

    /**
     * Envía una solicitud nueva: sube la foto al bucket privado y
     * crea la fila en estado PENDING con el consentimiento registrado.
     * Lanza [IllegalStateException] si ya hay una solicitud pendiente.
     */
    suspend fun submit(
        userId: String,
        licenseType: String,
        photoBytes: ByteArray,
        fileName: String,
        consentAtMillis: Long
    ): VerificationRequest
}
