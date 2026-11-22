package com.alan.semaforosmx.data.model

import java.util.UUID

/**
 * Verificación de conductor (sello Colaborador, SPEC §6.9).
 *
 * El usuario sube una foto de su licencia de chofer (tipo C/D en
 * Nuevo León) y el Admin la revisa una sola vez. Al aprobarla, el rol
 * pasa a COLLABORATOR. La foto es un dato sensible (LFPDPPP): vive en
 * un bucket privado, se revisa una sola vez y SE BORRA tras la
 * verificación; solo queda guardado el estado, la fecha y el tipo de
 * licencia, nunca la imagen. Al subir la foto, el usuario otorga su
 * consentimiento expreso (casilla, LFPDPPP art. 9) y se guarda la
 * fecha y hora en `consent_at`.
 *
 * La revisión del Admin NO va en el APK público (SPEC §6.9): Alan
 * resuelve desde el panel de Supabase con `resolve_verification()`.
 */

enum class VerificationStatus {
    PENDING, APPROVED, REJECTED
}

data class VerificationRequest(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    /** Tipo de licencia de chofer ('C' o 'D' en Nuevo León). */
    val licenseType: String,
    /** Ruta en el bucket license-photos: '{user_id}/{archivo}'. */
    val photoPath: String? = null,
    val status: VerificationStatus = VerificationStatus.PENDING,
    val submittedAtMillis: Long = System.currentTimeMillis(),
    /** Consentimiento expreso del usuario (LFPDPPP art. 9). */
    val consentAtMillis: Long? = null,
    val reviewedAtMillis: Long? = null,
    val rejectionReason: String? = null
) {
    val isPending: Boolean get() = status == VerificationStatus.PENDING
    val isApproved: Boolean get() = status == VerificationStatus.APPROVED
}
