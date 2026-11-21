package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightAnswer
import com.alan.semaforosmx.data.model.LightChat
import com.alan.semaforosmx.data.model.LightQuestion

/**
 * Mensajería ligera por semáforo («¿Ahí hay algo?» + respuestas
 * predefinidas). Ver [com.alan.semaforosmx.data.model.LightMessages]
 * y SPEC.md §12 (v2).
 *
 * Reglas que toda implementación debe cumplir:
 * - Una sola pregunta vigente por semáforo: [ask] devuelve la activa
 *   si ya existe (no duplica).
 * - Las preguntas expiradas no se muestran ni aceptan respuestas.
 * - Una respuesta por usuario por pregunta: responder de nuevo
 *   REEMPLAZA la respuesta anterior (cambiar de opinión es válido).
 * - Las respuestas NO cuentan para la cuota de Colaborador ni para el
 *   ranking: este repositorio nunca toca user_contributions.
 */
interface LightMessagesRepository {

    /** Chat vigente de un semáforo (pregunta activa + respuestas), o null. */
    suspend fun activeChat(lightId: String): LightChat?

    /**
     * Pregunta «¿Ahí hay algo?» en un semáforo. Si ya hay una pregunta
     * activa en ese semáforo, devuelve esa (sin crear otra).
     */
    suspend fun ask(lightId: String, userId: String?, nickname: String): LightQuestion

    /**
     * Responde con una opción predefinida. Si el usuario ya había
     * respondido esa pregunta, su respuesta se reemplaza.
     */
    suspend fun answer(questionId: String, userId: String?, option: AnswerOption): LightAnswer
}
