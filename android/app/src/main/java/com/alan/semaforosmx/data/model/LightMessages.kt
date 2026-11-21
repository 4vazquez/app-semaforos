package com.alan.semaforosmx.data.model

import java.util.UUID

/**
 * Mensajería ligera por semáforo (idea de Alan, 2026-11-21).
 *
 * En un semáforo, un usuario puede preguntar «¿Ahí hay algo?» y los
 * demás usuarios presentes en ese semáforo responden con opciones
 * PREDEFINIDAS (tráfico / choque / mercado sobre ruedas / obra / nada).
 * Sin texto libre, sin feed: solo pregunta + respuestas de un toque,
 * visibles únicamente para quienes están en ese semáforo (≤ 150 m, lo
 * aplica el cliente), con apodo (sin identidad real) y expiración
 * rápida. Las respuestas NO cuentan para la cuota de Colaborador ni
 * para el ranking (evita farming). Ver SPEC.md §12 (v2).
 */

/** Opciones de respuesta predefinidas (sin texto libre, a propósito). */
enum class AnswerOption(val displayName: String) {
    TRAFICO("Tráfico"),
    CHOQUE("Choque"),
    MERCADO("Mercado sobre ruedas"),
    OBRA("Obra"),
    NADA("Nada, todo tranquilo")
}

/**
 * Una pregunta «¿Ahí hay algo?» en un semáforo.
 *
 * Solo hay UNA pregunta vigente por semáforo a la vez: si ya hay una
 * activa, preguntar de nuevo devuelve la existente. Expira a los
 * [LightChatRules.EXPIRY_MILLIS]; después ya no se muestra ni acepta
 * respuestas.
 */
data class LightQuestion(
    val id: String = UUID.randomUUID().toString(),
    val lightId: String,
    /** Usuario que preguntó (null en modo local anónimo). */
    val askedBy: String? = null,
    /** Apodo visible (nunca nombre real ni correo). */
    val nickname: String = LightChatRules.DEFAULT_NICKNAME,
    val askedAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = askedAtMillis + LightChatRules.EXPIRY_MILLIS
) {
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < expiresAtMillis
}

/** Una respuesta a una pregunta: la opción elegida por un usuario. */
data class LightAnswer(
    val id: String = UUID.randomUUID().toString(),
    val questionId: String,
    /** Usuario que respondió (null en modo local anónimo). */
    val answeredBy: String? = null,
    val option: AnswerOption,
    val answeredAtMillis: Long = System.currentTimeMillis()
)

/**
 * Vista agregada de la mensajería de un semáforo: la pregunta vigente
 * (si hay) y todas sus respuestas. La UI muestra conteos por opción y
 * marca la respuesta del usuario actual.
 */
data class LightChat(
    val question: LightQuestion,
    val answers: List<LightAnswer> = emptyList()
) {
    /** Conteo de respuestas por opción (solo opciones con ≥ 1 respuesta). */
    fun countsByOption(): Map<AnswerOption, Int> =
        answers.groupingBy { it.option }.eachCount()

    /** La respuesta del usuario actual, si ya respondió. */
    fun myAnswer(userId: String?): LightAnswer? =
        if (userId == null) null else answers.firstOrNull { it.answeredBy == userId }

    /** Total de respuestas (para mostrar «3 respuestas»). */
    val totalAnswers: Int get() = answers.size
}

/** Reglas de la mensajería ligera (un solo lugar para ajustarlas). */
object LightChatRules {
    /** Texto de la única pregunta posible (no hay texto libre). */
    const val QUESTION_TEXT = "¿Ahí hay algo?"

    /** Apodo por defecto cuando no hay sesión (modo local). */
    const val DEFAULT_NICKNAME = "Conductor"

    /** Las preguntas expiran a los 30 minutos. */
    const val EXPIRY_MILLIS = 30L * 60L * 1000L

    /**
     * Radio para ver/preguntar en un semáforo (metros): el mismo del
     * widget (aparece a ≤ 150 m). Lo aplica el cliente.
     */
    const val PROXIMITY_METERS = 150.0
}
